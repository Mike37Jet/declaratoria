package com.roguelito.rommicines.tablero

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.SizeF
import com.roguelito.rommicines.R
import com.roguelito.rommicines.libreta.Papel
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Cómo se ve el tablero de corcho: lo usan la pantalla del tablero (que además deja arrastrar)
 * y el widget (que lo pinta como una sola imagen), así los dos se ven igual.
 *
 * Corcho claro de grano fino con marco blanco. Las fotos van como polaroid y las notitas como papel
 * adhesivo; todo un poco torcido y con su chincheta de color.
 */
object DibujoTablero {
    /** Marco blanco alrededor del corcho, en dp. */
    const val MARCO = 5f

    private val CORCHO = Color.rgb(0xDC, 0xC2, 0x9B)
    private val GRANO = intArrayOf(Color.rgb(0xC9, 0xA9, 0x7C), Color.rgb(0xEA, 0xD6, 0xB6), Color.rgb(0xBF, 0x9D, 0x70), Color.rgb(0xB0, 0x8A, 0x5C))
    private val MARCO_COLOR = Color.rgb(0xF4, 0xED, 0xE2)
    private val MARCO_CANTO = Color.rgb(0xD9, 0xCB, 0xB4)
    private val POLAROID = Color.rgb(0xFB, 0xF8, 0xF2)
    private val SIN_FOTO = Color.rgb(0xE8, 0xDC, 0xC8)
    private val TINTA = Color.rgb(0x5A, 0x3B, 0x2A)
    private val TINTA_PIE = Color.rgb(0x5A, 0x46, 0x36)
    private val PISTA = Color.rgb(0x8A, 0x6A, 0x45)

    /** Amarillo, rosa y celeste. */
    val NOTAS = intArrayOf(Color.rgb(0xFF, 0xF3, 0xA8), Color.rgb(0xFF, 0xC9, 0xD6), Color.rgb(0xC8, 0xEC, 0xF5))
    val NOMBRES_NOTAS = arrayOf("amarilla", "rosa", "celeste")
    /** Rojo, azul, verde, amarillo y rosa. */
    val CHINCHETAS = intArrayOf(Color.rgb(0xD6, 0x45, 0x45), Color.rgb(0x3B, 0x6F, 0xD6), Color.rgb(0x3F, 0x9B, 0x5C), Color.rgb(0xE8, 0xB0, 0x2E), Color.rgb(0xD4, 0x5C, 0x9A))

    /**
     * En la pantalla del tablero, el lado de una foto es esta fracción del ancho del tablero. Es fijo para que
     * el widget pueda copiar el acomodo a escala: mismas distancias y mismos tamaños, solo que más chico.
     */
    const val LADO_EN_TABLERO = 0.34f
    /** El ancho (en dp) del tablero «de referencia» con el que el widget mide el acomodo. */
    private const val ANCHO_REFERENCIA = 360f
    /** En el widget, una foto no se dibuja más chica que esto (si no caben, se muestran menos) ni más grande que esto. */
    private const val LADO_MINIMO_WIDGET = 64f
    private const val LADO_MAXIMO_WIDGET = 110f

    /** El lado de una foto (en dp) en el widget automático: la mitad del lado corto, entre un mínimo y un máximo. */
    fun lado(anchoDp: Float, altoDp: Float, fraccion: Float = 0.5f, minimo: Float = 89f, maximo: Float = 110f) =
        (min(anchoDp, altoDp) * fraccion).coerceIn(minimo, maximo)

    /** Ancho y alto de la pieza, en las mismas unidades que [lado]. */
    fun medida(p: Pieza, lado: Float): SizeF =
        if (p.esFoto) SizeF(lado, lado * if (p.texto.isBlank()) 1.12f else 1.22f) else SizeF(lado * 0.85f, lado * 0.8f)

    /** El centro de la pieza dentro de un tablero de w×h, sin que se salga del corcho. */
    fun centro(p: Pieza, w: Float, h: Float, lado: Float, d: Float): PointF {
        val m = medida(p, lado)
        // Medio lado más un poco por el giro (la esquina asoma) y el marco.
        val mx = m.width * 0.56f + (MARCO + 3) * d
        val my = m.height * 0.56f + (MARCO + 3) * d
        val x = if (w > 2 * mx) (p.x * w).coerceIn(mx, w - mx) else w / 2
        val y = if (h > 2 * my) (p.y * h).coerceIn(my, h - my) else h / 2
        return PointF(x, y)
    }

    /**
     * Reparte las piezas en una cuadrícula suelta (cada una un poco corrida) para un tablero de [aspecto]
     * (ancho/alto). Para el tablero automático: la más reciente arriba a la izquierda.
     */
    fun repartir(piezas: List<Pieza>, aspecto: Float): List<Pieza> {
        val n = piezas.size
        if (n == 0) return piezas
        val cols = sqrt(n * aspecto).roundToInt().coerceIn(1, n)
        val filas = ceil(n / cols.toFloat()).toInt()
        return piezas.sortedByDescending { it.orden }.mapIndexed { i, p ->
            val fila = i / cols
            val enFila = if (fila == filas - 1) n - fila * cols else cols
            val azar = Random(p.id.hashCode())
            val x = (i % cols + 0.5f + (cols - enFila) / 2f) / cols + (azar.nextFloat() - 0.5f) * 0.1f / cols
            val y = (fila + 0.5f) / filas + (azar.nextFloat() - 0.5f) * 0.12f / filas
            p.copy(x = x, y = y)
        }.sortedBy { it.orden }
    }

    // ---- El corcho ----

    fun radio(context: Context, d: Float) =
        if (Build.VERSION.SDK_INT >= 31) context.resources.getDimension(android.R.dimen.system_app_widget_background_radius) else 16 * d

    /** El corcho con su marco, siempre con el mismo grano (la misma semilla). */
    fun corcho(c: Canvas, w: Float, h: Float, d: Float, radio: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        c.save()
        c.clipPath(Path().apply { addRoundRect(RectF(0f, 0f, w, h), radio, radio, Path.Direction.CW) })
        p.color = MARCO_COLOR
        c.drawRect(0f, 0f, w, h, p)
        // Luz de arriba en el marco.
        p.shader = LinearGradient(0f, 0f, 0f, h, Color.argb(30, 255, 255, 255), Color.argb(18, 120, 90, 50), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p)
        p.shader = null

        val m = MARCO * d
        val dentro = RectF(m, m, w - m, h - m)
        val r = max(radio - m, 2 * d)
        c.clipPath(Path().apply { addRoundRect(dentro, r, r, Path.Direction.CW) })
        p.color = CORCHO
        c.drawRect(dentro, p)

        // Grano fino: muchos puntitos de cuatro tonos.
        val azar = Random(11)
        val area = dentro.width() * dentro.height()
        repeat((area / (d * d * 3.2f)).toInt()) {
            p.color = GRANO[azar.nextInt(GRANO.size)]
            p.alpha = 80 + azar.nextInt(130)
            c.drawCircle(dentro.left + azar.nextFloat() * dentro.width(), dentro.top + azar.nextFloat() * dentro.height(),
                (0.3f + azar.nextFloat() * 0.5f) * d, p)
        }
        // Algún gránulo un poco más grande y oscuro, como el corcho de verdad.
        repeat((area / (d * d * 140f)).toInt()) {
            p.color = GRANO[3]; p.alpha = 60 + azar.nextInt(70)
            val x = dentro.left + azar.nextFloat() * dentro.width(); val y = dentro.top + azar.nextFloat() * dentro.height()
            val rx = (0.8f + azar.nextFloat()) * d; val ry = (0.5f + azar.nextFloat() * 0.7f) * d
            c.drawOval(x - rx, y - ry, x + rx, y + ry, p)
        }
        // Viñeta: las orillas apenas más oscuras, como si el marco hiciera sombra.
        p.alpha = 255
        p.shader = RadialGradient(w / 2, h / 2, max(w, h) * 0.72f, intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, Color.argb(46, 90, 60, 30)),
            floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(dentro, p)
        p.shader = LinearGradient(0f, m, 0f, m + 5 * d, Color.argb(50, 90, 60, 30), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        c.drawRect(dentro.left, dentro.top, dentro.right, dentro.top + 5 * d, p)
        p.shader = null
        c.restore()

        // El canto interior del marco.
        p.style = Paint.Style.STROKE; p.strokeWidth = 1 * d; p.color = MARCO_CANTO
        c.drawRoundRect(dentro, r, r, p)
    }

    /** Lo que se lee en el corcho cuando no hay nada clavado. */
    fun pista(context: Context, c: Canvas, w: Float, h: Float, d: Float, texto: String) {
        val t = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Papel.letraNegrita(context); textSize = (if (min(w, h) / d < 120) 21 else 26) * d; color = PISTA
        }
        val ancho = (w - 2 * (MARCO + 13) * d).toInt().coerceAtLeast(1)
        val l = StaticLayout.Builder.obtain(texto, 0, texto.length, t, ancho).setAlignment(Layout.Alignment.ALIGN_CENTER).build()
        c.save(); c.translate((w - ancho) / 2, (h - l.height) / 2); l.draw(c); c.restore()
    }

    // ---- Las piezas ----

    /**
     * Una pieza con centro en ([cx], [cy]). [alzada] (0 a 1) es cuánto está despegada del corcho mientras se
     * arrastra: más sombra y más corrida. [chincheta] es la escala de la chincheta (0 = sin chincheta).
     */
    fun pieza(
        context: Context, c: Canvas, p: Pieza, cx: Float, cy: Float, giro: Float, lado: Float, d: Float,
        foto: Bitmap?, escala: Float = 1f, alzada: Float = 0f, chincheta: Float = 1f,
    ) {
        val m = medida(p, lado)
        val r = RectF(-m.width / 2, -m.height / 2, m.width / 2, m.height / 2)
        c.save()
        c.translate(cx, cy); c.rotate(giro); c.scale(escala, escala)

        sombra(c, r, d, alzada)
        val papel = Paint(Paint.ANTI_ALIAS_FLAG)
        if (p.esFoto) {
            papel.color = POLAROID
            c.drawRect(r, papel)
            val borde = m.width * 0.065f
            val cuadro = RectF(r.left + borde, r.top + borde, r.right - borde, r.top + m.width - borde)
            if (foto != null) c.drawBitmap(foto, recorte(foto, cuadro), cuadro, Paint(Paint.FILTER_BITMAP_FLAG))
            else { papel.color = SIN_FOTO; c.drawRect(cuadro, papel) }
            // Un brillo muy leve, como el papel fotográfico.
            papel.shader = LinearGradient(cuadro.left, cuadro.top, cuadro.right, cuadro.bottom,
                Color.argb(22, 255, 255, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP)
            c.drawRect(cuadro, papel); papel.shader = null
            if (p.texto.isNotBlank()) {
                val t = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Papel.letra(context); textSize = lado * 0.16f; color = TINTA_PIE }
                val s = TextUtils.ellipsize(p.texto, t, cuadro.width(), TextUtils.TruncateAt.END).toString()
                val base = cuadro.bottom + (r.bottom - cuadro.bottom) / 2 + t.textSize * 0.32f
                c.drawText(s, -t.measureText(s) / 2, base, t)
            }
        } else {
            papel.color = NOTAS[p.color.coerceIn(0, NOTAS.size - 1)]
            c.drawRect(r, papel)
            // La parte de abajo apenas más oscura: el papel adhesivo se despega un poquito.
            papel.shader = LinearGradient(0f, r.bottom - m.height * 0.35f, 0f, r.bottom, Color.TRANSPARENT, Color.argb(26, 80, 50, 20), Shader.TileMode.CLAMP)
            c.drawRect(r, papel); papel.shader = null
            val t = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Papel.letraNegrita(context); textSize = lado * 0.19f; color = TINTA }
            val pad = m.width * 0.1f
            val arriba = m.height * 0.2f
            val ancho = (m.width - 2 * pad).toInt().coerceAtLeast(1)
            val lineas = ((m.height - arriba - pad * 0.6f) / (t.textSize * 1.0f)).toInt().coerceAtLeast(1)
            val l = StaticLayout.Builder.obtain(p.texto, 0, p.texto.length, t, ancho)
                .setLineSpacing(0f, 0.92f).setIncludePad(false).setMaxLines(lineas).setEllipsize(TextUtils.TruncateAt.END).build()
            c.save(); c.translate(r.left + pad, r.top + arriba); l.draw(c); c.restore()
        }
        if (chincheta > 0f) clavo(c, 0f, r.top + lado * 0.075f, lado * 0.062f * chincheta, CHINCHETAS[p.chincheta.mod(CHINCHETAS.size)], d)
        c.restore()
    }

    /** Sombra suave hecha de capas (funciona igual con o sin aceleración por hardware). */
    private fun sombra(c: Canvas, r: RectF, d: Float, alzada: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(60, 35, 15) }
        val dx = (1f + 3f * alzada) * d
        val dy = (2f + 7f * alzada) * d
        val capas = 4
        for (i in capas downTo 1) {
            val e = i * (0.8f + 1.6f * alzada) * d
            p.alpha = ((14 - 4 * alzada) * (capas + 1 - i) / capas + 4).toInt()
            c.drawRoundRect(r.left + dx - e, r.top + dy - e, r.right + dx + e, r.bottom + dy + e, e, e, p)
        }
    }

    /** Una chincheta vista desde arriba: su sombrita, la cabeza de color y un brillo. */
    private fun clavo(c: Canvas, x: Float, y: Float, radio: Float, color: Int, d: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = Color.argb(70, 30, 15, 5)
        c.drawCircle(x + radio * 0.35f, y + radio * 0.55f, radio * 1.02f, p)
        p.alpha = 255 // el degradado usa la transparencia de la pintura: sin esto la cabeza sale deslavada
        p.shader = RadialGradient(x - radio * 0.35f, y - radio * 0.4f, radio * 1.4f,
            intArrayOf(aclarar(color, 0.35f), color, oscurecer(color, 0.3f)), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(x, y, radio, p)
        p.shader = null
        p.color = Color.argb(180, 255, 255, 255)
        c.drawCircle(x - radio * 0.35f, y - radio * 0.38f, radio * 0.28f, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 0.6f * d; p.color = oscurecer(color, 0.4f); p.alpha = 120
        c.drawCircle(x, y, radio, p)
    }

    private fun aclarar(c: Int, f: Float) = Color.rgb(
        (Color.red(c) + (255 - Color.red(c)) * f).toInt(), (Color.green(c) + (255 - Color.green(c)) * f).toInt(), (Color.blue(c) + (255 - Color.blue(c)) * f).toInt())

    private fun oscurecer(c: Int, f: Float) = Color.rgb((Color.red(c) * (1 - f)).toInt(), (Color.green(c) * (1 - f)).toInt(), (Color.blue(c) * (1 - f)).toInt())

    /** El trozo central de la foto que llena el cuadro (como centerCrop). */
    private fun recorte(b: Bitmap, destino: RectF): Rect {
        val proporcion = destino.width() / destino.height()
        return if (b.width.toFloat() / b.height > proporcion) {
            val w = (b.height * proporcion).toInt(); val x = (b.width - w) / 2
            Rect(x, 0, x + w, b.height)
        } else {
            val h = (b.width / proporcion).toInt(); val y = (b.height - h) / 2
            Rect(0, y, b.width, y + h)
        }
    }

    // ---- El widget ----

    /** Cuántas piezas caben en un widget de ese tamaño: 2 en un 2×2, 3 en un 3×2, 4 en un 4×2 y hasta 7. */
    fun cabenEnWidget(anchoDp: Float, altoDp: Float) = (anchoDp * altoDp / 15000f).toInt().coerceIn(1, Tablero.AUTOMATICAS)

    /**
     * El widget entero como una imagen: el corcho y las piezas más recientes.
     *
     * Si el tablero ya se acomodó en la app, el widget es una copia **a escala** del trozo del tablero donde están
     * esas piezas (mismas distancias, mismos tamaños relativos, mismo giro). Si así quedan muy chicas, se muestran
     * menos piezas (se quita la más vieja) en vez de amontonarlas. Si nadie acomodó nada, se reparten solas.
     */
    fun widget(context: Context, anchoDp: Float, altoDp: Float): Bitmap {
        val d = context.resources.displayMetrics.density
        val bmp = Bitmap.createBitmap((anchoDp * d).roundToInt().coerceAtLeast(1), (altoDp * d).roundToInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        bmp.density = context.resources.displayMetrics.densityDpi
        val c = Canvas(bmp)
        val w = bmp.width.toFloat(); val h = bmp.height.toFloat()
        corcho(c, w, h, d, radio(context, d))

        val estado = Tablero.estado(context)
        val recientes = estado.piezas.sortedByDescending { it.orden }.take(cabenEnWidget(anchoDp, altoDp))
        if (recientes.isEmpty()) {
            pista(context, c, w, h, d, if (estado.automatico) "Toca para clavar sus fotos" else "Toca para clavar algo")
            return bmp
        }
        fun dibujar(p: Pieza, cx: Float, cy: Float, lado: Float) {
            val foto = Tablero.archivo(context, p)?.let { com.roguelito.rommicines.Fotos.reducida(it, lado.toInt(), Bitmap.Config.RGB_565) }
            pieza(context, c, p, cx, cy, p.giro, lado, d, foto)
        }

        if (estado.automatico) {
            val lado = lado(anchoDp, altoDp) * d
            repartir(recientes, anchoDp / altoDp).forEach { p ->
                val ctr = centro(p, w, h, lado, d)
                dibujar(p, ctr.x, ctr.y, lado)
            }
            return bmp
        }

        // El tablero de referencia, en dp, con la forma que tiene en la pantalla de la app.
        val rw = ANCHO_REFERENCIA
        val rh = rw * Tablero.aspecto(context)
        val rlado = rw * LADO_EN_TABLERO
        val margen = MARCO + 13f
        var elegidas = recientes
        var vista: Vista
        while (true) {
            vista = encuadrar(elegidas, rw, rh, rlado, anchoDp - 2 * margen, altoDp - 2 * margen)
            if (rlado * vista.escala >= LADO_MINIMO_WIDGET || elegidas.size == 1) break
            elegidas = elegidas.dropLast(1) // se queda fuera la más vieja
        }
        val escala = vista.escala
        // El trozo del tablero queda centrado en el widget.
        val ox = (anchoDp - vista.caja.width() * escala) / 2 - vista.caja.left * escala
        val oy = (altoDp - vista.caja.height() * escala) / 2 - vista.caja.top * escala
        elegidas.sortedBy { it.orden }.forEach { p ->
            val ctr = vista.centros.getValue(p.id)
            dibujar(p, (ox + ctr.x * escala) * d, (oy + ctr.y * escala) * d, rlado * escala * d)
        }
        return bmp
    }

    private class Vista(val caja: RectF, val escala: Float, val centros: Map<String, PointF>)

    /** El rectángulo del tablero de referencia que ocupan las piezas y la escala para que quepa en ancho×alto. */
    private fun encuadrar(piezas: List<Pieza>, rw: Float, rh: Float, rlado: Float, ancho: Float, alto: Float): Vista {
        val centros = piezas.associate { it.id to centro(it, rw, rh, rlado, 1f) }
        val caja = RectF(Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
        piezas.forEach { p ->
            val ctr = centros.getValue(p.id)
            val m = medida(p, rlado)
            // Con el giro, las esquinas asoman un poco más que la mitad.
            val mx = m.width * 0.56f; val my = m.height * 0.56f
            caja.union(ctr.x - mx, ctr.y - my, ctr.x + mx, ctr.y + my)
        }
        val escala = min(ancho / caja.width(), alto / caja.height())
            .coerceAtMost(LADO_MAXIMO_WIDGET / rlado)
        return Vista(caja, escala, centros)
    }

    /** Para TalkBack: qué hay en el tablero. */
    fun descripcion(context: Context): String {
        val e = Tablero.estado(context)
        val fotos = e.piezas.count { it.esFoto }
        val notas = e.piezas.filter { !it.esFoto }
        val partes = mutableListOf<String>()
        if (fotos > 0) partes += if (fotos == 1) "1 foto" else "$fotos fotos"
        if (notas.isNotEmpty()) partes += notas.joinToString(", ", prefix = "notitas: ") { it.texto }
        return "Nuestro tablero de corcho" + (if (partes.isEmpty()) ", vacío" else ": " + partes.joinToString("; ")) + ". Toca para abrirlo"
    }
}
