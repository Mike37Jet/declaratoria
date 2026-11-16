package com.roguelito.rommicines.libreta

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import android.util.LruCache
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import java.io.ByteArrayOutputStream
import kotlin.math.max

/** Una página del álbum: la foto y la línea que se pegaron al cumplir un plan. */
data class Recuerdo(
    val id: String, // el mismo id del plan
    val plan: String,
    val linea: String,
    val autor: String,
    val tinta: Tinta,
    val fecha: Long,
    val numero: Int,
    val foto: ByteArray?,
)

/**
 * El álbum de recortes, en parejas/{p}/recuerdos/{plan}. La foto va dentro del documento
 * (comprimida a ~200 KB), porque Cloud Storage ya no es gratis.
 */
object Album {
    /** Tope de la foto en bytes: muy por debajo del máximo de 1 MiB de un documento. */
    const val LIMITE = 200_000
    private const val LADO = 1080
    const val MAX_LINEA = 80

    private val db get() = FirebaseFirestore.getInstance()

    /** Nº del boleto: el orden en que se cumplió el plan (el primero tachado es el 1). */
    fun numeroDeBoleto(planes: List<Plan>, id: String): Int {
        val hechos = planes.filter { it.hecho != null }.sortedWith(compareBy({ it.hecho }, { it.id }))
        return hechos.indexOfFirst { it.id == id } + 1
    }

    fun escuchar(pareja: String, cambio: (List<Recuerdo>) -> Unit): ListenerRegistration =
        db.collection("parejas").document(pareja).collection("recuerdos")
            .orderBy("fecha", Query.Direction.ASCENDING)
            .addSnapshotListener { lista, error ->
                if (error != null) { Log.w("Album", "No se pudo leer el álbum", error); return@addSnapshotListener }
                cambio(lista!!.documents.mapNotNull { d ->
                    Recuerdo(
                        id = d.id,
                        plan = d.getString("plan") ?: return@mapNotNull null,
                        linea = d.getString("linea") ?: "",
                        autor = d.getString("autor") ?: "",
                        tinta = Tinta.de(d.getString("tinta")),
                        fecha = d.getLong("fecha") ?: 0,
                        numero = (d.getLong("numero") ?: 0).toInt(),
                        foto = d.getBlob("foto")?.toBytes(),
                    )
                })
            }

    /** Pega el recuerdo. Se escribe primero en el celular, así que se ve al instante aunque no haya internet. */
    fun pegar(context: Context, plan: Plan, numero: Int, foto: ByteArray, linea: String) {
        val pareja = Libreta.pareja(context) ?: return
        db.collection("parejas").document(pareja).collection("recuerdos").document(plan.id).set(mapOf(
            "plan" to plan.texto,
            "linea" to linea.trim().take(MAX_LINEA),
            "autor" to (Libreta.yo() ?: ""),
            "tinta" to Libreta.tinta(context).name.lowercase(),
            "fecha" to (plan.hecho ?: System.currentTimeMillis()),
            "numero" to numero,
            "foto" to Blob.fromBytes(foto),
            "creado" to FieldValue.serverTimestamp(),
        )).addOnFailureListener { Log.w("Album", "No se pudo pegar el recuerdo", it) }
    }

    /** Las fotos ya abiertas, para no decodificarlas otra vez al pasar las páginas. */
    private val abiertas = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun foto(r: Recuerdo): Bitmap? {
        val bytes = r.foto ?: return null
        val clave = "${r.id}:${bytes.size}"
        abiertas.get(clave)?.let { return it }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.also { abiertas.put(clave, it) }
    }

    /** Abre la foto elegida, la endereza y la deja en JPEG de ≤ [LIMITE] bytes. Llamar fuera del hilo principal. */
    fun comprimir(context: Context, uri: Uri): ByteArray? = try {
        val abrir = { context.contentResolver.openInputStream(uri) }
        val medidas = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        abrir()?.use { BitmapFactory.decodeStream(it, null, medidas) }
        var muestra = 1
        while (max(medidas.outWidth, medidas.outHeight) / (muestra * 2) >= LADO) muestra *= 2
        val crudo = abrir()?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = muestra }) }
            ?: throw IllegalStateException("No es una imagen")
        val giro = abrir()?.use {
            when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
        var lado = minOf(LADO, max(crudo.width, crudo.height))
        var resultado: ByteArray? = null
        while (resultado == null && lado >= 320) {
            val escala = lado.toFloat() / max(crudo.width, crudo.height)
            val matriz = Matrix().apply { postScale(escala, escala); postRotate(giro) }
            val listo = Bitmap.createBitmap(crudo, 0, 0, crudo.width, crudo.height, matriz, true)
            for (calidad in intArrayOf(85, 75, 65, 55, 45)) {
                val bytes = ByteArrayOutputStream().also { listo.compress(Bitmap.CompressFormat.JPEG, calidad, it) }.toByteArray()
                if (bytes.size <= LIMITE) { resultado = bytes; break }
            }
            lado = (lado * 0.8f).toInt()
        }
        resultado
    } catch (e: Exception) {
        Log.w("Album", "No se pudo preparar la foto", e)
        null
    }
}
