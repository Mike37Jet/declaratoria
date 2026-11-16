package com.roguelito.rommicines.libreta

import android.content.Context
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.roguelito.rommicines.R
import com.roguelito.rommicines.Toques
import java.security.SecureRandom

/** Un plan de la libreta. [hecho] es cuándo se tachó (null: todavía no). */
data class Plan(
    val id: String,
    val texto: String,
    val autor: String,
    val tinta: Tinta,
    val orden: Long,
    val hecho: Long?,
    val hechoPor: String?,
)

/** Cada uno escribe con su tinta: así se nota quién escribió qué sin poner nombres. */
enum class Tinta(val color: Int) {
    AZUL(R.color.tinta_azul),
    ROSA(R.color.tinta_rosa);

    companion object {
        fun de(nombre: String?): Tinta = if (nombre == "rosa") ROSA else AZUL
        fun dePersona(nombre: String?): Tinta = if (nombre == Toques.MIMINA) ROSA else AZUL
    }
}

/** El código de 6 letras para unir el segundo celular. Sin letras que se confundan (0/O, 1/I/L). */
object Codigos {
    const val LETRAS = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
    private val azar = SecureRandom()

    fun nuevo(): String = (1..6).map { LETRAS[azar.nextInt(LETRAS.length)] }.joinToString("")

    /** Lo que la persona escribe ("k7p qx2", "K7P-QX2") → "K7PQX2", o null si no puede ser un código. */
    fun normalizar(texto: String): String? {
        val limpio = texto.uppercase().filter { it.isLetterOrDigit() }
        return limpio.takeIf { it.length == 6 && it.all { c -> c in LETRAS } }
    }

    /** Para mostrarlo: "K7P-QX2". */
    fun mostrar(codigo: String) = codigo.chunked(3).joinToString("-")
}

/**
 * La libreta compartida, guardada en Firestore (ver android/firestore.rules):
 *   parejas/{id}            miembros, nombres, tintas
 *   parejas/{id}/planes/{p} texto, autor, tinta, orden, hecho, hechoPor
 *   codigos/{codigo}        pareja, creador (se borra al usarlo)
 *
 * Cada celular entra con una cuenta anónima de Firebase (sin contraseñas). Firestore
 * guarda una copia en el celular, así que escribir y tachar funciona sin internet.
 */
object Libreta {
    /** El contador de aventuras: "13 de 100". */
    const val META = 100

    private val db get() = FirebaseFirestore.getInstance()
    private fun prefs(context: Context) = context.getSharedPreferences("libreta", Context.MODE_PRIVATE)

    fun pareja(context: Context): String? = prefs(context).getString("pareja", null)
    fun codigo(context: Context): String? = prefs(context).getString("codigo", null)
    fun yo(): String? = FirebaseAuth.getInstance().currentUser?.uid

    /** El nombre de quien usa este celular (se elige en la pantalla principal). */
    fun nombre(context: Context): String? = Toques.yo(context)
    fun tinta(context: Context): Tinta = Tinta.dePersona(nombre(context))

    /** Los planes del otro se "escriben" frente a ti solo la primera vez que los ves. */
    fun visto(context: Context, plan: String) = plan in prefs(context).getStringSet("vistos", emptySet())!!
    fun marcarVisto(context: Context, plan: String) {
        val vistos = prefs(context).getStringSet("vistos", emptySet())!!.toMutableSet()
        if (vistos.add(plan)) prefs(context).edit().putStringSet("vistos", vistos).apply()
    }

    fun entrar(listo: (String) -> Unit, fallo: (String) -> Unit) {
        val auth = FirebaseAuth.getInstance()
        auth.currentUser?.let { listo(it.uid); return }
        auth.signInAnonymously()
            .addOnSuccessListener { listo(it.user!!.uid) }
            .addOnFailureListener { fallo(motivo(it)) }
    }

    /** Crea la libreta de este celular y devuelve el código para el otro. */
    fun crear(context: Context, listo: (String) -> Unit, fallo: (String) -> Unit) = entrar({ uid ->
        val pareja = db.collection("parejas").document()
        val codigo = Codigos.nuevo()
        db.batch()
            .set(pareja, mapOf(
                "miembros" to listOf(uid),
                "nombres" to mapOf(uid to (nombre(context) ?: "")),
                "tintas" to mapOf(uid to tinta(context).name.lowercase()),
                "creada" to FieldValue.serverTimestamp(),
            ))
            .set(db.collection("codigos").document(codigo), mapOf(
                "pareja" to pareja.id,
                "creador" to uid,
                "creado" to FieldValue.serverTimestamp(),
            ))
            .commit()
            .addOnSuccessListener {
                prefs(context).edit().putString("pareja", pareja.id).putString("codigo", codigo).apply()
                listo(codigo)
            }
            .addOnFailureListener { fallo(motivo(it)) }
    }, fallo)

    /** Une este celular a la libreta del otro con su código. */
    fun unirse(context: Context, codigo: String, listo: () -> Unit, fallo: (String) -> Unit) = entrar({ uid ->
        db.collection("codigos").document(codigo).get()
            .addOnSuccessListener { doc ->
                val pareja = doc.getString("pareja") ?: return@addOnSuccessListener fallo("Ese código no existe. Revísalo con quien te lo dio.")
                db.collection("parejas").document(pareja).update(mapOf(
                    "miembros" to FieldValue.arrayUnion(uid),
                    "nombres.$uid" to (nombre(context) ?: ""),
                    "tintas.$uid" to tinta(context).name.lowercase(),
                ))
                    .addOnSuccessListener {
                        prefs(context).edit().putString("pareja", pareja).remove("codigo").apply()
                        doc.reference.delete()
                        listo()
                    }
                    .addOnFailureListener { fallo(motivo(it, unirse = true)) }
            }
            .addOnFailureListener { fallo(motivo(it)) }
    }, fallo)

    /** Avisa cuántos miembros tiene la libreta (para dejar de mostrar el código cuando ya son dos). */
    fun escucharMiembros(pareja: String, cambio: (Int) -> Unit): ListenerRegistration =
        db.collection("parejas").document(pareja).addSnapshotListener { doc, _ ->
            (doc?.get("miembros") as? List<*>)?.let { cambio(it.size) }
        }

    fun escucharPlanes(pareja: String, cambio: (List<Plan>) -> Unit, fallo: (String) -> Unit): ListenerRegistration =
        db.collection("parejas").document(pareja).collection("planes")
            .orderBy("orden", Query.Direction.ASCENDING)
            .addSnapshotListener { lista, error ->
                if (error != null) return@addSnapshotListener fallo(motivo(error))
                cambio(lista!!.documents.mapNotNull { d ->
                    Plan(
                        id = d.id,
                        texto = d.getString("texto") ?: return@mapNotNull null,
                        autor = d.getString("autor") ?: "",
                        tinta = Tinta.de(d.getString("tinta")),
                        orden = d.getLong("orden") ?: 0,
                        hecho = d.getLong("hecho"),
                        hechoPor = d.getString("hechoPor"),
                    )
                })
            }

    fun agregar(context: Context, texto: String) {
        val pareja = pareja(context) ?: return
        db.collection("parejas").document(pareja).collection("planes").add(mapOf(
            "texto" to texto.trim().take(MAX_TEXTO),
            "autor" to (yo() ?: ""),
            "tinta" to tinta(context).name.lowercase(),
            "orden" to System.currentTimeMillis(),
            "creado" to FieldValue.serverTimestamp(),
            "hecho" to null,
            "hechoPor" to null,
        ))
    }

    fun tachar(context: Context, plan: Plan, hecho: Boolean) {
        val pareja = pareja(context) ?: return
        db.collection("parejas").document(pareja).collection("planes").document(plan.id).update(mapOf(
            "hecho" to if (hecho) System.currentTimeMillis() else null,
            "hechoPor" to if (hecho) yo() else null,
        ))
    }

    const val MAX_TEXTO = 120

    private fun motivo(e: Exception, unirse: Boolean = false): String = when {
        e is FirebaseNetworkException || (e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.UNAVAILABLE) ->
            "Sin conexión. Inténtalo cuando tengas internet."
        e is FirebaseAuthException || e.message?.contains("CONFIGURATION_NOT_FOUND") == true ->
            "No se pudo entrar. Revisa que el acceso anónimo esté activado en Firebase (Authentication)."
        e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.NOT_FOUND ->
            "Falta crear la base de datos en Firebase (Firestore Database)."
        e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED ->
            if (unirse) "Esa libreta ya tiene a sus dos personas." else "Firebase no dio permiso. Revisa las reglas de Firestore."
        else -> "Algo falló: ${e.message ?: e.javaClass.simpleName}"
    }
}
