package com.roguelito.rommicines

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.roguelito.rommicines.libreta.Album
import com.roguelito.rommicines.libreta.Libreta
import java.io.File
import java.util.concurrent.Executors

/**
 * «Nuestros recuerdos» compartidos entre los dos celulares (cuando la libreta está vinculada).
 *
 * - parejas/{p}/fotos/{nombre}: la lista, liviana (quién, cuándo, si se quitó). Se escucha siempre.
 * - parejas/{p}/fotosDatos/{nombre}: la foto en JPEG ≤ 200 KB. Se baja una sola vez por foto,
 *   así abrir la app no vuelve a descargar todas (y no se pasa del plan gratis).
 *
 * Cada celular sigue guardando su copia en filesDir/fotos (el carrusel lee de ahí). Las fotos
 * de la página (las «semillas») vienen en los dos celulares y no se suben: solo se anotan.
 * Quitar nunca borra el documento: lo marca, para que el otro celular también la quite.
 */
object FotosCompartidas {
    private val db get() = FirebaseFirestore.getInstance()
    private val trabajo = Executors.newSingleThreadExecutor()

    /** Lo último que dijo Firestore: nombre → quitada. Null si todavía no se escucha. */
    @Volatile private var remotas: Map<String, Remota>? = null
    private val bajando = mutableSetOf<String>()

    private data class Remota(val quitada: Boolean, val semilla: Boolean)

    private fun lista(pareja: String) = db.collection("parejas").document(pareja).collection("fotos")
    private fun datos(pareja: String) = db.collection("parejas").document(pareja).collection("fotosDatos")

    fun escuchar(context: Context): ListenerRegistration? {
        val app = context.applicationContext
        val pareja = Libreta.pareja(app) ?: return null
        return lista(pareja).addSnapshotListener { lista, error ->
            if (error != null) { Log.w("FotosCompartidas", "No se pudo leer la lista", error); return@addSnapshotListener }
            remotas = lista!!.documents.associate { d ->
                d.id to Remota(d.getBoolean("quitada") == true, d.getBoolean("semilla") == true)
            }
            sincronizar(app)
        }
    }

    fun dejarDeEscuchar() { remotas = null }

    /** Pone de acuerdo la carpeta local con la lista compartida. Se puede llamar cuando sea. */
    fun sincronizar(context: Context) {
        val app = context.applicationContext
        trabajo.execute { sincronizarYa(app) }
    }

    private fun sincronizarYa(context: Context) {
        val pareja = Libreta.pareja(context) ?: return
        val remotas = remotas ?: return
        if (Fotos.importando) return // al terminar de importar se vuelve a llamar
        val locales = Fotos.lista(context).associateBy { it.name.removeSuffix(".jpg") }
        var cambio = false

        remotas.forEach { (nombre, r) ->
            val local = locales[nombre]
            when {
                r.quitada && local != null -> { Fotos.borrarCopia(local); cambio = true }
                // Se quitó aquí antes de vincular: que se quite también en el otro.
                !r.quitada && local == null && Fotos.apartada(context, nombre) -> marcarQuitada(pareja, nombre)
                !r.quitada && local == null ->
                    if (r.semilla) { if (Fotos.resembrar(context, nombre)) cambio = true } else bajar(context, pareja, nombre)
            }
        }
        // Lo que solo está en este celular (añadido antes de vincular, o sin internet) se sube.
        locales.filterKeys { it !in remotas }.forEach { (nombre, archivo) -> subir(context, pareja, nombre, archivo) }

        if (cambio) avisar(context)
    }

    private fun subir(context: Context, pareja: String, nombre: String, archivo: File) {
        if (Fotos.esSemilla(nombre)) {
            lista(pareja).document(nombre).set(mapOf("semilla" to true, "quitada" to false, "creado" to FieldValue.serverTimestamp()))
            return
        }
        val bytes = Album.comprimir(context, Uri.fromFile(archivo)) ?: return
        // Primero los datos y luego la lista: así el otro nunca ve una foto que todavía no puede bajar.
        datos(pareja).document(nombre).set(mapOf("foto" to Blob.fromBytes(bytes)))
        lista(pareja).document(nombre).set(mapOf(
            "autor" to (Libreta.yo() ?: ""),
            "quitada" to false,
            "creado" to FieldValue.serverTimestamp(),
        ))
    }

    private fun bajar(context: Context, pareja: String, nombre: String) {
        synchronized(bajando) { if (!bajando.add(nombre)) return }
        datos(pareja).document(nombre).get()
            .addOnSuccessListener(trabajo) { d ->
                val bytes = d.getBlob("foto")?.toBytes()
                if (bytes != null && remotas?.get(nombre)?.quitada == false && Fotos.guardarBytes(context, "$nombre.jpg", bytes)) avisar(context)
                synchronized(bajando) { bajando.remove(nombre) }
            }
            .addOnFailureListener(trabajo) { e ->
                Log.w("FotosCompartidas", "No se pudo bajar $nombre", e)
                synchronized(bajando) { bajando.remove(nombre) }
            }
    }

    /** Quitar en un celular la quita en los dos (y libera el espacio de la foto). */
    fun quitar(context: Context, archivo: File) {
        val pareja = Libreta.pareja(context) ?: return
        val nombre = archivo.name.removeSuffix(".jpg")
        remotas = remotas?.plus(nombre to Remota(quitada = true, semilla = Fotos.esSemilla(nombre)))
        marcarQuitada(pareja, nombre)
    }

    private fun marcarQuitada(pareja: String, nombre: String) {
        lista(pareja).document(nombre).set(mapOf("quitada" to true), SetOptions.merge())
        if (!Fotos.esSemilla(nombre)) datos(pareja).document(nombre).delete()
    }

    /** Deshacer: vuelve para los dos. La foto se sube otra vez desde la copia de este celular. */
    fun restaurar(context: Context, nombreArchivo: String) {
        val pareja = Libreta.pareja(context) ?: return
        val nombre = nombreArchivo.removeSuffix(".quitada").removeSuffix(".jpg")
        // La lista en memoria se corrige ya: si no, una sincronización con la lista vieja
        // («quitada») borraría la foto recién restaurada antes de que Firestore avise.
        remotas = remotas?.minus(nombre)
        lista(pareja).document(nombre).delete() // sin documento, la próxima sincronización la sube de nuevo
    }

    private fun avisar(context: Context) {
        context.getSharedPreferences("fotos", Context.MODE_PRIVATE).edit()
            .putLong("sincronizadas", System.currentTimeMillis()).apply()
        CarruselWidget.refrescar(context)
    }
}
