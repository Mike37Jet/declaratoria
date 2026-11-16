package com.roguelito.rommicines

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.InputStream
import kotlin.math.max

/**
 * Las fotos del carrusel viven copiadas dentro de la app, así no se pierden
 * aunque se borren de la galería.
 */
object Fotos {
    @Volatile var importando = false
    /** Lado más largo con el que se guarda cada foto. */
    private const val LADO_GUARDADO = 1600

    private fun carpeta(context: Context) = File(context.filesDir, "fotos").apply { mkdirs() }

    fun lista(context: Context): List<File> =
        carpeta(context).listFiles { f -> f.name.endsWith(".jpg") }?.sortedBy { it.name }.orEmpty()

    /** La primera vez, el carrusel arranca con las fotos de la página. */
    fun sembrar(context: Context) {
        val prefs = context.getSharedPreferences("fotos", Context.MODE_PRIVATE)
        if (prefs.getBoolean("sembradas", false)) return
        context.assets.list("photos").orEmpty()
            .filter { it.first().isDigit() && it.endsWith(".jpg") }
            .sorted()
            .forEach { nombre -> guardar(context, "$PREFIJO_SEMILLA$nombre") { context.assets.open("photos/$nombre") } }
        prefs.edit().putBoolean("sembradas", true).apply()
    }

    fun importar(context: Context, uris: List<Uri>): Int {
        val base = System.currentTimeMillis()
        return uris.withIndex().count { (i, uri) ->
            guardar(context, "%013d-%03d.jpg".format(base, i)) { context.contentResolver.openInputStream(uri) }
        }
    }

    /** Apartar la copia local permite deshacer; la galería original nunca se modifica. */
    fun quitar(context: Context, foto: File): Boolean {
        val apartada = File(foto.parentFile, foto.name + ".quitada")
        if (!foto.renameTo(apartada)) return false
        context.getSharedPreferences("fotos", Context.MODE_PRIVATE).edit()
            .putString("ultima_quitada", apartada.name).putString("estado", "Foto quitada").apply()
        return true
    }

    fun ultimaQuitada(context: Context): File? {
        val nombre = context.getSharedPreferences("fotos", Context.MODE_PRIVATE).getString("ultima_quitada", null) ?: return null
        return File(carpeta(context), nombre).takeIf { it.exists() }
    }

    fun deshacer(context: Context): Boolean {
        val apartada = ultimaQuitada(context) ?: return false
        val destino = File(carpeta(context), apartada.name.removeSuffix(".quitada"))
        if (destino.exists() || !apartada.renameTo(destino)) return false
        context.getSharedPreferences("fotos", Context.MODE_PRIVATE).edit()
            .remove("ultima_quitada").putString("estado", "Foto restaurada").apply()
        return true
    }

    /** Decodifica la foto reducida para que su lado más largo sea [lado] como mucho. */
    fun reducida(foto: File, lado: Int, config: Bitmap.Config = Bitmap.Config.ARGB_8888): Bitmap? =
        decodificar(lado, config) { foto.inputStream() }

    private const val PREFIJO_SEMILLA = "0000000000000-"

    /** Las fotos de la página que trae la app: están en los dos celulares, no hace falta subirlas. */
    fun esSemilla(nombre: String) = nombre.startsWith(PREFIJO_SEMILLA)

    /** Vuelve a poner una foto de la página (cuando el otro la restauró). */
    fun resembrar(context: Context, nombre: String): Boolean {
        val asset = "photos/" + nombre.removePrefix(PREFIJO_SEMILLA).removeSuffix(".jpg") + ".jpg"
        return guardar(context, nombre.removeSuffix(".jpg") + ".jpg") { context.assets.open(asset) }
    }

    /** ¿Este celular la quitó y aún se puede deshacer? */
    fun apartada(context: Context, nombre: String) = File(carpeta(context), "$nombre.jpg.quitada").exists()

    /** La quitó el otro: aquí se borra la copia sin ofrecer deshacer. */
    fun borrarCopia(foto: File) { foto.delete() }

    /** Guarda una foto que llegó del otro celular (ya viene derecha y comprimida). */
    fun guardarBytes(context: Context, nombre: String, bytes: ByteArray): Boolean = try {
        escribir(File(carpeta(context), nombre)) { it.write(bytes) }
        true
    } catch (e: Exception) {
        Log.w("Fotos", "No se pudo guardar $nombre", e)
        false
    }

    /** Se escribe aparte y se renombra al final: nadie (ni la sincronización) ve una foto a medias. */
    private fun escribir(destino: File, llenar: (java.io.OutputStream) -> Unit) {
        val temporal = File(destino.parentFile, destino.name + ".parcial")
        temporal.outputStream().use(llenar)
        if (!temporal.renameTo(destino)) { temporal.delete(); throw java.io.IOException("No se pudo renombrar ${destino.name}") }
    }

    private fun guardar(context: Context, nombre: String, abrir: () -> InputStream?): Boolean = try {
        val giro = abrir()?.use { giroExif(ExifInterface(it)) } ?: 0
        val bitmap = decodificar(LADO_GUARDADO, Bitmap.Config.ARGB_8888, abrir) ?: return false
        val derecha = if (giro == 0) bitmap else
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(giro.toFloat()) }, true)
        escribir(File(carpeta(context), nombre)) { derecha.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        true
    } catch (e: Exception) {
        Log.w("Fotos", "No se pudo guardar $nombre", e)
        false
    }

    /** Las fotos del celular suelen venir "acostadas" con una marca EXIF de giro. */
    private fun giroExif(exif: ExifInterface) =
        when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }

    private fun decodificar(lado: Int, config: Bitmap.Config, abrir: () -> InputStream?): Bitmap? {
        val medidas = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // Con inJustDecodeBounds decodeStream siempre devuelve null: solo llena las medidas.
        (abrir() ?: return null).use { BitmapFactory.decodeStream(it, null, medidas) }
        val mayor = max(medidas.outWidth, medidas.outHeight)
        if (mayor <= 0) return null

        var muestra = 1
        while (mayor / (muestra * 2) >= lado) muestra *= 2
        val opciones = BitmapFactory.Options().apply { inSampleSize = muestra; inPreferredConfig = config }
        val bitmap = abrir()?.use { BitmapFactory.decodeStream(it, null, opciones) } ?: return null

        val escala = lado.toFloat() / max(bitmap.width, bitmap.height)
        if (escala >= 1f) return bitmap
        return Bitmap.createScaledBitmap(bitmap, (bitmap.width * escala).toInt(), (bitmap.height * escala).toInt(), true)
    }
}
