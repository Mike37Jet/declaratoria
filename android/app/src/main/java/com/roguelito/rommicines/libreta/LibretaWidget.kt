package com.roguelito.rommicines.libreta

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Build
import android.os.Bundle
import android.text.TextPaint
import android.text.TextUtils
import android.util.SizeF
import android.widget.RemoteViews
import com.roguelito.rommicines.R
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * La hoja de la libreta en la pantalla de inicio: los próximos planes, sin tachar.
 * Se dibuja como imagen (papel, renglones, margen y letra Caveat) porque un widget no puede
 * usar fuentes propias. Se lee de una copia local: la deja [guardar] al abrir la libreta
 * y la parchan los avisos del otro celular ([alAviso]).
 *
 * Tres tamaños: mini (2×1) el próximo plan; franja (3×1, 4×1) el próximo y la cuenta;
 * completo (4×2) hasta tres planes con título. Tocarlo abre la libreta; no se tacha desde aquí.
 */
class LibretaWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { actualizar(context, manager, it) }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, opciones: Bundle) {
        actualizar(context, manager, id)
    }

    /** Lo que el widget necesita de un plan pendiente. */
    data class Pendiente(val id: String, val texto: String, val tinta: Tinta, val porFirmar: Boolean)

    data class Hoja(val vinculada: Boolean, val pendientes: List<Pendiente>, val hechos: Int)

    companion object {
        private const val PREFS = "libreta_widget"
        private const val GUARDADOS = 12 // de sobra para los tres que se ven

        // Mismos umbrales (en dp) que el widget del contador.
        private val COMPLETO = SizeF(250f, 100f)
        private val FRANJA = SizeF(230f, 56f)

        // ---- La copia local ----

        /** Lo llama la libreta cada vez que cambian los planes. */
        fun guardar(context: Context, planes: List<Plan>) {
            val pendientes = planes.filter { it.hecho == null }.sortedBy { it.orden }
                .map { Pendiente(it.id, it.texto, it.tinta, it.porFirmar) }
            escribir(context, Hoja(true, pendientes, planes.count { it.hecho != null }))
        }

        /** Un aviso del otro celular: se anota en la copia sin esperar a abrir la libreta. */
        fun alAviso(context: Context, aviso: AvisoLibreta) {
            val h = leer(context)
            val nueva = when (aviso.evento) {
                Evento.PLAN, Evento.PROPUESTA ->
                    if (h.pendientes.any { it.id == aviso.plan }) h
                    else h.copy(pendientes = h.pendientes + Pendiente(aviso.plan, aviso.texto,
                        Tinta.dePersona(aviso.quien), aviso.evento == Evento.PROPUESTA))
                Evento.FIRMA -> h.copy(pendientes = h.pendientes.map { if (it.id == aviso.plan) it.copy(porFirmar = false) else it })
                Evento.HECHO ->
                    if (h.pendientes.none { it.id == aviso.plan }) h
                    else h.copy(pendientes = h.pendientes.filter { it.id != aviso.plan }, hechos = h.hechos + 1)
                Evento.RECUERDO -> return
            }
            escribir(context, nueva.copy(vinculada = true))
        }

        fun leer(context: Context): Hoja {
            val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val lista = runCatching {
                val a = JSONArray(p.getString("pendientes", "[]"))
                (0 until a.length()).map { i ->
                    val o = a.getJSONObject(i)
                    Pendiente(o.getString("id"), o.getString("texto"), Tinta.de(o.optString("tinta")), o.optBoolean("porFirmar"))
                }
            }.getOrDefault(emptyList())
            return Hoja(Libreta.pareja(context) != null, lista, p.getInt("hechos", 0))
        }

        private fun escribir(context: Context, h: Hoja) {
            val a = JSONArray()
            h.pendientes.take(GUARDADOS).forEach {
                a.put(JSONObject().put("id", it.id).put("texto", it.texto)
                    .put("tinta", if (it.tinta == Tinta.ROSA) "rosa" else "azul").put("porFirmar", it.porFirmar))
            }
            val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val antes = p.getString("pendientes", null) to p.getInt("hechos", -1)
            if (antes == a.toString() to h.hechos) return // nada cambió: no se redibuja
            p.edit().putString("pendientes", a.toString()).putInt("hechos", h.hechos).apply()
            actualizarTodos(context)
        }

        // ---- El widget ----

        fun actualizarTodos(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            manager.getAppWidgetIds(ComponentName(context, LibretaWidget::class.java))
                .forEach { actualizar(context, manager, it) }
        }

        private fun actualizar(context: Context, manager: AppWidgetManager, id: Int) {
            val (ancho, alto) = tamaño(context, manager, id)
            val hoja = leer(context)
            val vistas = RemoteViews(context.packageName, R.layout.widget_libreta)
            vistas.setImageViewBitmap(R.id.hoja, dibujar(context, hoja, ancho, alto))
            vistas.setContentDescription(R.id.hoja, descripcion(hoja))
            val abrir = PendingIntent.getActivity(context, 0,
                Intent(context, LibretaActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            vistas.setOnClickPendingIntent(R.id.hoja, abrir)
            manager.updateAppWidget(id, vistas)
        }

        /** El tamaño actual en dp (en vertical, el primero que da el launcher). */
        private fun tamaño(context: Context, manager: AppWidgetManager, id: Int): Pair<Float, Float> {
            val o = manager.getAppWidgetOptions(id)
            @Suppress("DEPRECATION")
            val tamaños = o.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
            val vertical = context.resources.configuration.orientation != android.content.res.Configuration.ORIENTATION_LANDSCAPE
            val t = tamaños?.takeIf { it.isNotEmpty() }?.let { if (vertical) it.first() else it.last() }
            if (t != null) return t.width to t.height
            val ancho = o.getInt(if (vertical) AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH else AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
            val alto = o.getInt(if (vertical) AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT else AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
            return (if (ancho > 0) ancho.toFloat() else 250f) to (if (alto > 0) alto.toFloat() else 110f)
        }

        fun descripcion(h: Hoja): String = when {
            !h.vinculada -> "Nuestra libreta. Toca para abrirla"
            h.pendientes.isEmpty() -> "Nuestra libreta: ${h.hechos} de ${Libreta.META} planes hechos, nada pendiente"
            else -> "Nuestra libreta, ${h.hechos} de ${Libreta.META}. Próximos planes: " +
                h.pendientes.take(3).joinToString("; ") { it.texto + if (it.porFirmar) " (propuesta, por firmar)" else "" }
        }

        // ---- El dibujo ----

        fun dibujar(context: Context, h: Hoja, anchoDp: Float, altoDp: Float): Bitmap {
            val d = context.resources.displayMetrics.density
            val w = (anchoDp * d).roundToInt().coerceAtLeast(1)
            val alto = (altoDp * d).roundToInt().coerceAtLeast(1)
            val bmp = Bitmap.createBitmap(w, alto, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val radio = if (Build.VERSION.SDK_INT >= 31)
                context.resources.getDimension(android.R.dimen.system_app_widget_background_radius) else 16 * d

            val pincel = Paint(Paint.ANTI_ALIAS_FLAG)
            val hoja = Path().apply { addRoundRect(RectF(0f, 0f, w.toFloat(), alto.toFloat()), radio, radio, Path.Direction.CW) }
            c.clipPath(hoja)
            pincel.color = context.getColor(R.color.hoja)
            c.drawRect(0f, 0f, w.toFloat(), alto.toFloat(), pincel)

            val completo = anchoDp >= COMPLETO.width && altoDp >= COMPLETO.height
            val franja = !completo && anchoDp >= FRANJA.width && altoDp >= FRANJA.height
            val margen = (if (completo) 30 else 22) * d

            // Renglones: en el completo, un título arriba y hasta tres planes; en la franja, uno solo
            // centrado; en el mini, si el plan no cabe y hay alto, pasa a un segundo renglón.
            val letra = Papel.letra(context)
            val texto = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = letra }
            val derecha = w - 14 * d
            val unRenglon = (alto * 0.52f).coerceIn(22 * d, 40 * d)
            val mensaje = when {
                !h.vinculada -> "Abre nuestra libreta"
                h.pendientes.isEmpty() && h.hechos == 0 -> "Escriban su primer plan"
                h.pendientes.isEmpty() -> "Todo tachado"
                else -> null
            }
            val dosRenglones = !completo && !franja && altoDp >= 64 && run {
                texto.textSize = (unRenglon * 0.74f).coerceAtMost(26 * d)
                texto.measureText(h.pendientes.firstOrNull()?.texto ?: mensaje ?: "") > derecha - margen - 10 * d
            }
            val filas = if (completo) 3 else 1
            val cabecera = if (completo) 40 * d else 0f
            val renglon = when {
                completo -> ((alto - cabecera - 10 * d) / filas).coerceIn(24 * d, 40 * d)
                dosRenglones -> (alto * 0.36f).coerceIn(22 * d, 34 * d)
                else -> unRenglon
            }
            val primeraLinea = when {
                completo -> cabecera + renglon
                dosRenglones -> alto / 2f + 4 * d
                else -> (alto + renglon) / 2f - 4 * d
            }

            val linea = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.renglon); strokeWidth = 1 * d }
            var y = primeraLinea
            while (y - renglon > 4 * d) y -= renglon
            while (y < alto - 2 * d) { c.drawLine(0f, y, w.toFloat(), y, linea); y += renglon }
            linea.color = context.getColor(R.color.margen)
            c.drawLine(margen, 0f, margen, alto.toFloat(), linea)

            // La cuenta, a la derecha: en el completo junto al título, en la franja en el renglón.
            val cuenta = "${h.hechos}/${Libreta.META}"
            var finTexto = derecha
            if (h.vinculada && (completo || franja)) {
                texto.textSize = (if (completo) 18 else 17) * d
                texto.color = context.getColor(R.color.tinta_suave)
                val ancho = texto.measureText(cuenta)
                val base = if (completo) cabecera - 12 * d else primeraLinea - 5 * d
                c.drawText(cuenta, derecha - ancho, base, texto)
                if (franja) finTexto = derecha - ancho - 12 * d
            }
            if (completo) {
                texto.typeface = Papel.letraNegrita(context)
                texto.textSize = 22 * d
                texto.color = context.getColor(R.color.tinta_lapiz)
                c.drawText(cortar("Nuestras aventuras", texto, derecha - margen - 70 * d), margen + 10 * d, cabecera - 12 * d, texto)
                texto.typeface = letra
            }

            // Lo que va en los renglones.
            val tam = (renglon * 0.74f).coerceAtMost(26 * d)
            val ver = h.pendientes.take(filas)
            if (mensaje != null) {
                texto.textSize = tam; texto.color = context.getColor(R.color.tinta_guia)
                renglones(c, mensaje, texto, margen + 10 * d, primeraLinea - 5 * d, finTexto - margen - 10 * d, if (dosRenglones) renglon else 0f)
                // «Todo tachado» lleva su palomita de trazo (el ícono de la libreta, no un emoji).
                if (h.vinculada && h.pendientes.isEmpty() && h.hechos > 0 && !dosRenglones) {
                    val x = margen + 10 * d + texto.measureText(mensaje) + 6 * d
                    val lado = (tam * 0.9f).toInt()
                    context.getDrawable(R.drawable.ic_libreta_hecho)?.mutate()?.apply {
                        setTint(context.getColor(R.color.tinta_guia))
                        setBounds(x.toInt(), (primeraLinea - 5 * d - lado * 0.85f).toInt(), x.toInt() + lado, (primeraLinea - 5 * d + lado * 0.15f).toInt())
                        draw(c)
                    }
                }
            }
            ver.forEachIndexed { i, p ->
                val base = primeraLinea + i * renglon - 5 * d
                val clip = if (p.porFirmar) 16 * d else 0f
                texto.textSize = tam
                texto.color = context.getColor(p.tinta.color)
                val disponible = finTexto - margen - 10 * d - clip
                renglones(c, p.texto, texto, margen + 10 * d, base, disponible, if (dosRenglones) renglon else 0f)
                if (p.porFirmar) clip(c, context, derecha - 6 * d, base - tam * 0.45f, d)
            }
            return bmp
        }

        /** Escribe [s] en un renglón, o en dos si [siguiente] (la distancia al segundo) no es cero. */
        private fun renglones(c: Canvas, s: String, p: TextPaint, x: Float, base: Float, ancho: Float, siguiente: Float) {
            if (siguiente == 0f) { c.drawText(cortar(s, p, ancho), x, base, p); return }
            val (uno, dos) = partir(s, p, ancho)
            c.drawText(uno, x, base, p)
            c.drawText(cortar(dos, p, ancho), x, base + siguiente, p)
        }

        /** Lo que cabe en el primer renglón (cortando en un espacio) y el resto. */
        private fun partir(s: String, p: TextPaint, ancho: Float): Pair<String, String> {
            val limpio = s.replace('\n', ' ').trim()
            val caben = p.breakText(limpio, true, ancho, null)
            if (caben >= limpio.length) return limpio to ""
            val corte = limpio.lastIndexOf(' ', caben).takeIf { it > 0 } ?: caben
            return limpio.substring(0, corte).trimEnd() to limpio.substring(corte).trimStart()
        }

        /** Una sola línea con «…» si no cabe. */
        private fun cortar(s: String, p: TextPaint, ancho: Float): String =
            TextUtils.ellipsize(s.replace('\n', ' '), p, ancho.coerceAtLeast(0f), TextUtils.TruncateAt.END).toString()

        /** El clip de una propuesta sin firmar, pequeñito al final del renglón. */
        private fun clip(c: Canvas, context: Context, cx: Float, cy: Float, d: Float) {
            val alambre = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = 1.5f * d; strokeCap = Paint.Cap.ROUND
                color = context.getColor(R.color.clip)
            }
            val e = 0.62f // escala del clip de [BotonClip]
            val forma = Path().apply {
                moveTo(10 * d, 24 * d); lineTo(10 * d, 6 * d)
                arcTo(RectF(2 * d, 2 * d, 10 * d, 10 * d), 0f, -180f, false)
                lineTo(2 * d, 28 * d)
                arcTo(RectF(2 * d, 25 * d, 8 * d, 31 * d), 180f, -180f, false)
                lineTo(8 * d, 10 * d)
            }
            c.save()
            c.translate(cx - 6 * d * e, cy - 16 * d * e)
            c.rotate(-12f, 6 * d * e, 16 * d * e)
            c.scale(e, e)
            c.drawPath(forma, alambre.apply { strokeWidth = 1.5f * d / e })
            c.restore()
        }
    }
}
