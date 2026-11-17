package com.roguelito.rommicines.tablero

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.roguelito.rommicines.CarruselWidget
import com.roguelito.rommicines.libreta.Libreta
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.abs
import kotlin.random.Random

/**
 * Algo clavado en el tablero de corcho: una foto de «Nuestros recuerdos» (con su pie, opcional) o una notita.
 * [x] e [y] son el centro, como fracción del tablero (0 a 1): así el mismo acomodo sirve para la pantalla
 * de la app y para cada tamaño del widget. [orden] decide quién queda encima (el último que se tocó).
 */
data class Pieza(
    val id: String,
    val tipo: String,
    val foto: String? = null,
    val texto: String = "",
    val color: Int = 0,
    val x: Float = 0.5f,
    val y: Float = 0.5f,
    val giro: Float = 0f,
    val chincheta: Int = 0,
    val orden: Long = 0,
    val autor: String = "",
) {
    val esFoto get() = tipo == Tablero.FOTO
}

/**
 * El tablero de corcho («Nuestros recuerdos» clavados con chinchetas), compartido entre los dos celulares.
 *
 * - Copia local en las prefs «tablero» (la lee el widget; no lee Firestore).
 * - Con la libreta vinculada, cada pieza es parejas/{p}/tablero/{id}. Quitar la marca `quitada`
 *   en vez de borrar, como las fotos: así un celular que estuvo sin internet no la vuelve a subir.
 * - Mientras nadie acomode nada, el tablero se arma solo con las fotos más recientes ([automatico]).
 *   La primera vez que se mueve o se agrega algo, ese acomodo queda guardado ([fijar]).
 */
object Tablero {
    const val FOTO = "foto"
    const val NOTA = "nota"
    /** Cuántas fotos pone el tablero solo, mientras nadie lo acomoda. */
    const val AUTOMATICAS = 7
    const val LARGO_TEXTO = 60

    data class Estado(val piezas: List<Pieza>, val automatico: Boolean)

    private fun prefs(context: Context) = context.getSharedPreferences("tablero", Context.MODE_PRIVATE)
    private fun db() = FirebaseFirestore.getInstance()
    private fun coleccion(pareja: String) = db().collection("parejas").document(pareja).collection("tablero")

    /** Lo que se ve ahora: lo acomodado o, si nunca se tocó, las fotos más recientes. Solo lo que existe en este celular. */
    fun estado(context: Context): Estado {
        val p = prefs(context)
        if (!p.getBoolean("tocado", false)) return Estado(automaticas(context), true)
        return Estado(leer(p.getString("piezas", null)).filter { existe(context, it) }, false)
    }

    /** Una foto se ve solo si su copia está en este celular (si el otro la quitó, o aún no baja, no sale). */
    fun existe(context: Context, p: Pieza) = !p.esFoto || archivo(context, p)?.exists() == true

    fun archivo(context: Context, p: Pieza): File? = p.foto?.let { File(File(context.filesDir, "fotos"), it) }

    private fun automaticas(context: Context): List<Pieza> =
        com.roguelito.rommicines.Fotos.lista(context).takeLast(AUTOMATICAS).mapIndexed { i, f ->
            val id = "foto-" + f.name.removeSuffix(".jpg")
            Pieza(id, FOTO, foto = f.name, giro = giroDe(Random(id.hashCode())), chincheta = abs(id.hashCode()) % 5, orden = i.toLong())
        }

    /** Una inclinación chiquita, nunca derecha del todo (entre 2° y 7°, a un lado o al otro). */
    fun giroDe(azar: Random): Float {
        val g = 2f + azar.nextFloat() * 5f
        return if (azar.nextBoolean()) g else -g
    }

    /** Una pieza nueva, cerca del centro, con su chincheta y su giro al azar. */
    fun nueva(tipo: String, foto: String? = null, texto: String = "", color: Int = 0): Pieza {
        val azar = Random(System.nanoTime())
        val id = "p" + System.currentTimeMillis().toString(36) + azar.nextInt(1 shl 20).toString(36)
        return Pieza(id, tipo, foto, texto.take(LARGO_TEXTO), color,
            x = 0.5f + (azar.nextFloat() - 0.5f) * 0.3f, y = 0.45f + (azar.nextFloat() - 0.5f) * 0.3f,
            giro = giroDe(azar), chincheta = azar.nextInt(5), orden = System.currentTimeMillis(), autor = Libreta.yo() ?: "")
    }

    // ---- Cambios ----

    /** Guarda el acomodo entero (la primera vez que se toca el tablero automático). */
    fun fijar(context: Context, piezas: List<Pieza>) {
        val antes = leerGuardadas(context).associateBy { it.id }
        escribir(context, piezas)
        val pareja = Libreta.pareja(context) ?: return
        piezas.forEach { if (antes[it.id] != it) coleccion(pareja).document(it.id).set(aMapa(it)) }
        antes.keys.filter { id -> piezas.none { it.id == id } }.forEach { marcarQuitada(pareja, it) }
    }

    /** Pone o mueve una pieza. */
    fun guardar(context: Context, pieza: Pieza) {
        escribir(context, leerGuardadas(context).filter { it.id != pieza.id } + pieza)
        Libreta.pareja(context)?.let { coleccion(it).document(pieza.id).set(aMapa(pieza)) }
    }

    fun quitar(context: Context, id: String) {
        escribir(context, leerGuardadas(context).filter { it.id != id })
        Libreta.pareja(context)?.let { marcarQuitada(it, id) }
    }

    private fun marcarQuitada(pareja: String, id: String) =
        coleccion(pareja).document(id).set(mapOf("quitada" to true), SetOptions.merge())

    // ---- Compartido ----

    /** Escucha el tablero de la pareja mientras la app está abierta. Null si la libreta no está vinculada. */
    fun escuchar(context: Context): ListenerRegistration? {
        val app = context.applicationContext
        val pareja = Libreta.pareja(app) ?: return null
        return coleccion(pareja).addSnapshotListener { docs, error ->
            if (error != null) { Log.w("Tablero", "No se pudo leer el tablero", error); return@addSnapshotListener }
            val remotas = docs!!.documents
            if (remotas.isEmpty() && !prefs(app).getBoolean("tocado", false)) return@addSnapshotListener
            val conocidas = remotas.map { it.id }.toSet()
            val vivas = remotas.filter { it.getBoolean("quitada") != true }.mapNotNull { d -> deMapa(d.id, d.data.orEmpty()) }
            // Lo que se acomodó aquí antes de vincular (o sin internet) y el otro nunca vio: se sube.
            val soloAqui = leerGuardadas(app).filter { it.id !in conocidas }
            soloAqui.forEach { coleccion(pareja).document(it.id).set(aMapa(it)) }
            escribir(app, vivas + soloAqui)
        }
    }

    // ---- Copia local ----

    private fun leerGuardadas(context: Context): List<Pieza> {
        val p = prefs(context)
        if (!p.getBoolean("tocado", false)) return emptyList()
        return leer(p.getString("piezas", null))
    }

    private fun escribir(context: Context, piezas: List<Pieza>) {
        val a = JSONArray()
        piezas.sortedBy { it.orden }.forEach { a.put(JSONObject(aMapa(it))) }
        prefs(context).edit().putBoolean("tocado", true).putString("piezas", a.toString()).apply()
        CarruselWidget.refrescar(context)
    }

    private fun leer(json: String?): List<Pieza> {
        if (json.isNullOrEmpty()) return emptyList()
        val a = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
        return (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            deMapa(o.optString("id"), o.keys().asSequence().associateWith { o.get(it) })
        }
    }

    private fun aMapa(p: Pieza): Map<String, Any> = buildMap {
        put("id", p.id); put("tipo", p.tipo); p.foto?.let { put("foto", it) }
        put("texto", p.texto); put("color", p.color)
        put("x", p.x.toDouble()); put("y", p.y.toDouble()); put("giro", p.giro.toDouble())
        put("chincheta", p.chincheta); put("orden", p.orden); put("autor", p.autor); put("quitada", false)
    }

    private fun deMapa(id: String, m: Map<String, Any?>): Pieza? {
        if (id.isEmpty()) return null
        val tipo = m["tipo"] as? String ?: return null
        fun num(k: String) = (m[k] as? Number)
        return Pieza(
            id = id, tipo = tipo, foto = m["foto"] as? String, texto = m["texto"] as? String ?: "",
            color = num("color")?.toInt() ?: 0,
            x = num("x")?.toFloat()?.coerceIn(0f, 1f) ?: 0.5f, y = num("y")?.toFloat()?.coerceIn(0f, 1f) ?: 0.5f,
            giro = num("giro")?.toFloat() ?: 0f, chincheta = num("chincheta")?.toInt() ?: 0,
            orden = num("orden")?.toLong() ?: 0L, autor = m["autor"] as? String ?: "",
        ).takeIf { tipo == FOTO && it.foto != null || tipo == NOTA }
    }
}
