package com.roguelito.rommicines.libreta

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Icon
import android.text.TextPaint
import android.text.TextUtils
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.roguelito.rommicines.R
import kotlin.math.roundToInt

/**
 * Los renglones del widget de la libreta: uno por plan pendiente, en el orden de la libreta.
 * Un plan largo ocupa dos renglones (se corta con «…» si aún no cabe), como cuando se escribe a mano.
 */
class LibretaWidgetServicio : RemoteViewsService() {

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        Renglones(applicationContext, intent.getFloatExtra(ANCHO, 250f))

    companion object {
        const val ANCHO = "ancho"
    }

    private class Renglones(private val context: Context, private val anchoDp: Float) : RemoteViewsFactory {
        private var planes: List<LibretaWidget.Pendiente> = emptyList()

        override fun onCreate() {}
        override fun onDataSetChanged() { planes = LibretaWidget.leer(context).pendientes }
        override fun onDestroy() {}
        override fun getCount() = planes.size
        override fun getViewTypeCount() = 1
        override fun getItemId(position: Int) = planes.getOrNull(position)?.id?.hashCode()?.toLong() ?: position.toLong()
        override fun hasStableIds() = true
        override fun getLoadingView(): RemoteViews? = null

        override fun getViewAt(position: Int): RemoteViews {
            val v = RemoteViews(context.packageName, R.layout.widget_libreta_fila)
            val p = planes.getOrNull(position) ?: return v

            // Acción de la derecha: casilla (tachar), pluma (firmar la propuesta del otro) o clip (esperando firma).
            val (icono, accion, dice) = when {
                p.porFirmar && !p.mio -> Triple(R.drawable.ic_libreta_firma, AccionLibretaActivity.FIRMAR, "Firmar la propuesta")
                p.porFirmar -> Triple(R.drawable.ic_libreta_clip, AccionLibretaActivity.ABRIR, "Propuesta tuya, espera la firma")
                else -> Triple(R.drawable.ic_libreta_casilla, AccionLibretaActivity.TACHAR, "Tachar este plan")
            }
            v.setImageViewResource(R.id.accion, icono)
            v.setInt(R.id.accion, "setColorFilter", context.getColor(if (p.porFirmar) R.color.clip else p.tinta.color))
            v.setContentDescription(R.id.accion, "$dice: ${p.texto}")
            v.setOnClickFillInIntent(R.id.accion, AccionLibretaActivity.relleno(accion, p))

            // Como Icon y no como Bitmap: los bitmaps de una lista comparten caché con los del widget y se cruzan
            // (un renglón mostraba la imagen de otro).
            v.setImageViewIcon(R.id.renglon, Icon.createWithBitmap(dibujar(p)))
            v.setContentDescription(R.id.renglon, p.texto + if (p.porFirmar) ", propuesta por firmar" else "")
            v.setOnClickFillInIntent(R.id.renglon, AccionLibretaActivity.relleno(AccionLibretaActivity.ABRIR, p))
            return v
        }

        /** El renglón (o los dos) con el plan escrito en Caveat, con la tinta de quien lo escribió. */
        private fun dibujar(p: LibretaWidget.Pendiente): Bitmap {
            val d = context.resources.displayMetrics.density
            val w = (anchoDp * d).roundToInt().coerceAtLeast(1)
            val t = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Papel.letra(context)
                textSize = LibretaWidget.LETRA * d
                color = context.getColor(p.tinta.color)
            }
            val x = (LibretaWidget.MARGEN + LibretaWidget.SANGRIA) * d
            val ancho = w - x - (LibretaWidget.BOTON + LibretaWidget.ORILLA + LibretaWidget.SANGRIA) * d
            val (uno, dos) = partir(p.texto, t, ancho)
            val lineas = if (dos.isEmpty()) listOf(uno) else listOf(uno, cortar(dos, t, ancho))

            val renglon = LibretaWidget.RENGLON * d
            val bmp = Bitmap.createBitmap(w, (renglon * lineas.size).roundToInt(), Bitmap.Config.ARGB_8888)
            bmp.density = context.resources.displayMetrics.densityDpi
            val c = Canvas(bmp)
            lineas.forEachIndexed { i, s ->
                val abajo = (i + 1) * renglon
                LibretaWidget.renglon(context, c, 0f, w.toFloat(), abajo, d)
                c.drawText(s, x, abajo - LibretaWidget.SANGRIA * d, t)
            }
            return bmp
        }

        /** Lo que cabe en el primer renglón (cortando en un espacio) y el resto. */
        private fun partir(s: String, p: TextPaint, ancho: Float): Pair<String, String> {
            val limpio = s.replace('\n', ' ').trim()
            val caben = p.breakText(limpio, true, ancho, null)
            if (caben >= limpio.length) return limpio to ""
            val corte = limpio.lastIndexOf(' ', caben).takeIf { it > 0 } ?: caben
            return limpio.substring(0, corte).trimEnd() to limpio.substring(corte).trimStart()
        }

        private fun cortar(s: String, p: TextPaint, ancho: Float): String =
            TextUtils.ellipsize(s, p, ancho.coerceAtLeast(0f), TextUtils.TruncateAt.END).toString()
    }
}
