package com.roguelito.rommicines.libreta

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
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
import kotlin.random.Random

/**
 * La hoja de la libreta en la pantalla de inicio: todos los planes pendientes (firmados y por firmar),
 * en una lista que se desliza cuando no caben. Los tachados no aparecen.
 *
 * Cada renglón se dibuja como imagen (letra Caveat, que un widget no puede usar como fuente) y lleva
 * a la derecha su acción: la casilla para tachar, la pluma para firmar una propuesta del otro, o el
 * clip si la propuesta es tuya y espera firma. Tocar el renglón abre la libreta en ese plan; el «+»
 * de la cabecera abre la libreta en el renglón nuevo. Ver [LibretaWidgetServicio] y [AccionLibretaActivity].
 *
 * Se lee de una copia local: la deja [guardar] al abrir la libreta y la parchan los avisos del otro
 * celular ([alAviso]) y la firma hecha desde el widget ([firmadaAqui]).
 *
 * Medidas en proporción áurea: cabecera de 55 dp, renglones de 34 dp, letra de 21 y 26 dp,
 * márgenes de 13 y 8 dp.
 */
class LibretaWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { actualizar(context, manager, it) }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, opciones: Bundle) {
        actualizar(context, manager, id)
    }

    /** Lo que el widget necesita de un plan pendiente. [mio]: lo escribí yo (no puedo firmarlo). */
    data class Pendiente(val id: String, val texto: String, val tinta: Tinta, val porFirmar: Boolean, val mio: Boolean)

    data class Hoja(val vinculada: Boolean, val pendientes: List<Pendiente>, val hechos: Int)

    companion object {
        private const val PREFS = "libreta_widget"
        private const val GUARDADOS = 100 // la meta: nunca hay más pendientes que esto

        /** Con menos alto que esto (en dp) no cabe la cabecera: solo renglones. */
        private const val CON_CABECERA = 100f

        // Medidas en dp (escala áurea).
        const val CABECERA = 55f
        const val RENGLON = 34f
        const val LETRA = 21f
        const val TITULO = 26f
        const val MARGEN = 34f   // la raya roja
        const val SANGRIA = 8f   // de la raya a la letra
        const val ANILLADO = 21f // la columna de los agujeros
        const val BOTON = 34f
        const val ORILLA = 13f
        private const val AGUJERO = 5f // radio

        // ---- La copia local ----

        /** Lo llama la libreta cada vez que cambian los planes. */
        fun guardar(context: Context, planes: List<Plan>) {
            val yo = Libreta.yo()
            val pendientes = planes.filter { it.hecho == null }.sortedBy { it.orden }
                .map { Pendiente(it.id, it.texto, it.tinta, it.porFirmar, mio = yo != null && it.autor == yo) }
            escribir(context, Hoja(true, pendientes, planes.count { it.hecho != null }))
        }

        /** Un aviso del otro celular: se anota en la copia sin esperar a abrir la libreta. */
        fun alAviso(context: Context, aviso: AvisoLibreta) {
            val h = leer(context)
            val nueva = when (aviso.evento) {
                Evento.PLAN, Evento.PROPUESTA ->
                    if (h.pendientes.any { it.id == aviso.plan }) h
                    else h.copy(pendientes = h.pendientes + Pendiente(aviso.plan, aviso.texto,
                        Tinta.dePersona(aviso.quien), aviso.evento == Evento.PROPUESTA, mio = false))
                Evento.FIRMA -> h.copy(pendientes = h.pendientes.map { if (it.id == aviso.plan) it.copy(porFirmar = false) else it })
                Evento.HECHO ->
                    if (h.pendientes.none { it.id == aviso.plan }) h
                    else h.copy(pendientes = h.pendientes.filter { it.id != aviso.plan }, hechos = h.hechos + 1)
                Evento.RECUERDO -> return
            }
            escribir(context, nueva.copy(vinculada = true))
        }

        /** Se firmó desde el widget: la copia lo refleja al momento, sin esperar a Firestore. */
        fun firmadaAqui(context: Context, plan: String) {
            val h = leer(context)
            escribir(context, h.copy(pendientes = h.pendientes.map { if (it.id == plan) it.copy(porFirmar = false) else it }))
        }

        fun leer(context: Context): Hoja {
            val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val lista = runCatching {
                val a = JSONArray(p.getString("pendientes", "[]"))
                (0 until a.length()).map { i ->
                    val o = a.getJSONObject(i)
                    Pendiente(o.getString("id"), o.getString("texto"), Tinta.de(o.optString("tinta")),
                        // Una copia de antes no sabe de quién es: se toma como tuya para no ofrecer firmarla
                        // (abrir la libreta una vez la vuelve a escribir con el dato).
                        o.optBoolean("porFirmar"), o.optBoolean("mio", true))
                }
            }.getOrDefault(emptyList())
            return Hoja(Libreta.pareja(context) != null, lista, p.getInt("hechos", 0))
        }

        private fun escribir(context: Context, h: Hoja) {
            val a = JSONArray()
            h.pendientes.take(GUARDADOS).forEach {
                a.put(JSONObject().put("id", it.id).put("texto", it.texto)
                    .put("tinta", if (it.tinta == Tinta.ROSA) "rosa" else "azul")
                    .put("porFirmar", it.porFirmar).put("mio", it.mio))
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
            val ids = manager.getAppWidgetIds(ComponentName(context, LibretaWidget::class.java))
            ids.forEach { actualizar(context, manager, it) }
            @Suppress("DEPRECATION")
            manager.notifyAppWidgetViewDataChanged(ids, R.id.lista)
        }

        private fun actualizar(context: Context, manager: AppWidgetManager, id: Int) {
            val (ancho, alto) = tamaño(context, manager, id)
            val hoja = leer(context)
            val cabecera = alto >= CON_CABECERA
            val v = RemoteViews(context.packageName, if (cabecera) R.layout.widget_libreta else R.layout.widget_libreta_corta)

            v.setImageViewBitmap(R.id.papel, papel(context, ancho, alto))
            v.setImageViewBitmap(R.id.anillado, anillado(context, ancho, alto))
            if (cabecera) {
                v.setImageViewBitmap(R.id.titulo, titulo(context, hoja, ancho))
                v.setContentDescription(R.id.titulo, "Nuestras aventuras, ${hoja.hechos} de ${Libreta.META} hechos")
                v.setOnClickPendingIntent(R.id.nuevo, AccionLibretaActivity.abrirLibreta(context, AccionLibretaActivity.NUEVO, null))
                v.setOnClickPendingIntent(R.id.titulo, AccionLibretaActivity.abrirLibreta(context, null, null))
            }

            // La lista: el servicio dibuja un renglón por plan pendiente. La dirección lleva el ancho para que,
            // al cambiar de tamaño, el sistema pida renglones nuevos.
            val servicio = Intent(context, LibretaWidgetServicio::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(LibretaWidgetServicio.ANCHO, ancho)
            servicio.data = Uri.parse("libreta://widget/$id/${ancho.roundToInt()}")
            @Suppress("DEPRECATION")
            v.setRemoteAdapter(R.id.lista, servicio)
            v.setPendingIntentTemplate(R.id.lista, AccionLibretaActivity.plantilla(context))
            // El mensaje de «nada pendiente» se muestra a mano: como vista vacía de la lista (setEmptyView), su
            // imagen se colaba en los renglones y cada plan salía con la acción del siguiente.
            val nada = hoja.pendientes.isEmpty()
            v.setViewVisibility(R.id.vacio, if (nada) android.view.View.VISIBLE else android.view.View.GONE)
            v.setViewVisibility(R.id.lista, if (nada) android.view.View.GONE else android.view.View.VISIBLE)
            if (nada) v.setImageViewBitmap(R.id.vacio, vacio(context, hoja, ancho))
            v.setOnClickPendingIntent(R.id.vacio, AccionLibretaActivity.abrirLibreta(context,
                if (hoja.vinculada) AccionLibretaActivity.NUEVO else null, null))
            v.setContentDescription(R.id.vacio, descripcion(hoja))
            manager.updateAppWidget(id, v)
        }

        /** El tamaño actual en dp (en vertical, el primero que da el launcher). */
        fun tamaño(context: Context, manager: AppWidgetManager, id: Int): Pair<Float, Float> {
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
            h.pendientes.isEmpty() && h.hechos == 0 -> "Nuestra libreta, sin planes todavía. Toca para escribir el primero"
            h.pendientes.isEmpty() -> "Nuestra libreta: ${h.hechos} de ${Libreta.META} planes hechos, nada pendiente"
            else -> "Nuestra libreta, ${h.hechos} de ${Libreta.META}, ${h.pendientes.size} pendientes"
        }

        // ---- El dibujo ----

        private fun lienzo(context: Context, anchoDp: Float, altoDp: Float): Pair<Bitmap, Float> {
            val d = context.resources.displayMetrics.density
            val bmp = Bitmap.createBitmap((anchoDp * d).roundToInt().coerceAtLeast(1), (altoDp * d).roundToInt().coerceAtLeast(1),
                Bitmap.Config.ARGB_8888)
            bmp.density = context.resources.displayMetrics.densityDpi
            return bmp to d
        }

        private fun radio(context: Context, d: Float) =
            if (Build.VERSION.SDK_INT >= 31) context.resources.getDimension(android.R.dimen.system_app_widget_background_radius) else 16 * d

        /**
         * El papel: color crema con grano y fibras, un poco más oscuro hacia las orillas (como una hoja
         * con algo de uso), y los cantos de las hojas de abajo asomando en el borde inferior.
         */
        private fun papel(context: Context, anchoDp: Float, altoDp: Float): Bitmap {
            val (bmp, d) = lienzo(context, anchoDp, altoDp)
            val w = bmp.width.toFloat(); val h = bmp.height.toFloat()
            val c = Canvas(bmp)
            val r = radio(context, d)
            c.clipPath(Path().apply { addRoundRect(RectF(0f, 0f, w, h), r, r, Path.Direction.CW) })
            val p = Paint(Paint.ANTI_ALIAS_FLAG)

            // Las hojas de abajo: dos cantos un poco más oscuros.
            p.color = context.getColor(R.color.hoja_canto)
            c.drawRect(0f, 0f, w, h, p)
            p.color = context.getColor(R.color.hoja)
            c.drawRoundRect(RectF(0f, 0f, w, h - 3 * d), r, r, p)

            // Grano: puntitos y fibras cortas, siempre en el mismo sitio.
            val azar = Random(7)
            p.color = context.getColor(R.color.hoja_grano)
            repeat((w * h / (d * d * 55)).toInt()) {
                p.alpha = 10 + azar.nextInt(18)
                c.drawCircle(azar.nextFloat() * w, azar.nextFloat() * h, (0.4f + azar.nextFloat() * 0.6f) * d, p)
            }
            p.strokeWidth = 0.6f * d; p.strokeCap = Paint.Cap.ROUND
            repeat((w * h / (d * d * 900)).toInt()) {
                p.alpha = 14 + azar.nextInt(14)
                val x = azar.nextFloat() * w; val y = azar.nextFloat() * h
                val largo = (3 + azar.nextFloat() * 5) * d; val a = azar.nextFloat() * Math.PI.toFloat()
                c.drawLine(x, y, x + largo * kotlin.math.cos(a), y + largo * kotlin.math.sin(a), p)
            }

            // Viñeta: las orillas, apenas más tostadas.
            p.alpha = 255
            p.shader = RadialGradient(w / 2, h / 2, maxOf(w, h) * 0.75f,
                intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, context.getColor(R.color.hoja_orilla)),
                floatArrayOf(0f, 0.62f, 1f), Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w, h, p)
            // Luz de arriba: el borde superior un poco más claro.
            p.shader = LinearGradient(0f, 0f, 0f, 21 * d, Color.argb(40, 255, 255, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w, 21 * d, p)

            // Los agujeros del anillado, perforados: ahí se ve el fondo de pantalla.
            p.shader = null; p.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            agujeros(h, d).forEach { (x, y) -> c.drawCircle(x, y, AGUJERO * d, p) }
            return bmp
        }

        /**
         * Encima de todo: la raya roja del margen y los agujeros del anillado, perforados de verdad
         * (se ve el fondo de pantalla) con su sombrita en el canto de arriba.
         */
        private fun anillado(context: Context, anchoDp: Float, altoDp: Float): Bitmap {
            val (bmp, d) = lienzo(context, anchoDp, altoDp)
            val h = bmp.height.toFloat()
            val c = Canvas(bmp)
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.color = context.getColor(R.color.margen); p.strokeWidth = 1.5f * d
            c.drawLine(MARGEN * d, 0f, MARGEN * d, h, p)

            // El canto de cada agujero: sombra en la mitad de arriba (la luz viene de arriba) y brillo abajo.
            p.style = Paint.Style.STROKE
            agujeros(h, d).forEach { (x, y) ->
                val aro = RectF(x - AGUJERO * d, y - AGUJERO * d, x + AGUJERO * d, y + AGUJERO * d)
                p.strokeWidth = 1.5f * d; p.color = context.getColor(R.color.hoja_orilla); p.alpha = 200
                c.drawArc(aro, 180f, 180f, false, p)
                p.strokeWidth = 1f * d; p.color = Color.WHITE; p.alpha = 110
                c.drawArc(aro, 20f, 140f, false, p)
            }
            return bmp
        }

        /** Dónde van los agujeros: cada 55 dp, centrados en el alto. */
        private fun agujeros(h: Float, d: Float): List<Pair<Float, Float>> {
            val paso = CABECERA * d
            val n = ((h - 13 * d) / paso).toInt().coerceAtLeast(1)
            val inicio = (h - (n - 1) * paso) / 2f
            val cx = (ANILLADO / 2f + 2f) * d
            return (0 until n).map { cx to inicio + it * paso }
        }

        /** La cabecera: «Nuestras aventuras» y la cuenta, sobre su renglón. Deja libre el sitio del «+». */
        private fun titulo(context: Context, h: Hoja, anchoDp: Float): Bitmap {
            val (bmp, d) = lienzo(context, anchoDp, CABECERA)
            val c = Canvas(bmp)
            val w = bmp.width.toFloat(); val alto = bmp.height.toFloat()
            renglon(context, c, 0f, w, alto, d)
            val t = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Papel.letraNegrita(context) }
            val base = alto - SANGRIA * d
            val libre = w - (BOTON + ORILLA + SANGRIA) * d
            // La cuenta, a la izquierda del «+».
            var finTitulo = libre
            if (h.vinculada) {
                t.typeface = Papel.letra(context); t.textSize = 16 * d; t.color = context.getColor(R.color.tinta_suave)
                val cuenta = "${h.hechos}/${Libreta.META}"
                val ancho = t.measureText(cuenta)
                c.drawText(cuenta, libre - ancho, base, t)
                finTitulo = libre - ancho - ORILLA * d
            }
            t.typeface = Papel.letraNegrita(context); t.textSize = TITULO * d; t.color = context.getColor(R.color.tinta_lapiz)
            val x = (MARGEN + SANGRIA) * d
            c.drawText(TextUtils.ellipsize("Nuestras aventuras", t, finTitulo - x, TextUtils.TruncateAt.END).toString(), x, base, t)
            return bmp
        }

        /** Lo que se ve cuando no hay nada pendiente (o la libreta no está vinculada). */
        private fun vacio(context: Context, h: Hoja, anchoDp: Float): Bitmap {
            val (bmp, d) = lienzo(context, anchoDp, RENGLON)
            val c = Canvas(bmp)
            renglon(context, c, 0f, bmp.width.toFloat(), bmp.height.toFloat(), d)
            val mensaje = when {
                !h.vinculada -> "Abre nuestra libreta"
                h.hechos == 0 -> "+ Escriban su primer plan"
                else -> "Todo tachado"
            }
            val t = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Papel.letra(context); textSize = LETRA * d; color = context.getColor(R.color.tinta_guia)
            }
            val x = (MARGEN + SANGRIA) * d
            val base = bmp.height - SANGRIA * d
            c.drawText(mensaje, x, base, t)
            // «Todo tachado» lleva su palomita de trazo (el ícono de la libreta, no un emoji).
            if (h.vinculada && h.pendientes.isEmpty() && h.hechos > 0) {
                val ix = x + t.measureText(mensaje) + 6 * d
                val lado = (LETRA * 0.9f * d).toInt()
                context.getDrawable(R.drawable.ic_libreta_hecho)?.mutate()?.apply {
                    setTint(context.getColor(R.color.tinta_guia))
                    setBounds(ix.toInt(), (base - lado * 0.85f).toInt(), ix.toInt() + lado, (base + lado * 0.15f).toInt())
                    draw(c)
                }
            }
            return bmp
        }

        /** La raya azul de un renglón, a lo ancho desde después del anillado. */
        fun renglon(context: Context, c: Canvas, x0: Float, w: Float, y: Float, d: Float) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.renglon); strokeWidth = 1 * d }
            c.drawLine(maxOf(x0, (ANILLADO + 5) * d), y - 0.5f * d, w, y - 0.5f * d, p)
        }
    }
}
