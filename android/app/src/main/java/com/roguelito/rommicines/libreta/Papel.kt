package com.roguelito.rommicines.libreta

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.animation.PathInterpolator
import com.roguelito.rommicines.R
import kotlin.random.Random

/**
 * Medidas y materiales compartidos por las piezas de la libreta.
 * Todo se alinea a [linea]: cada renglón de texto cae sobre una raya de la hoja.
 */
object Papel {
    /** Tamaño de la letra de los planes. */
    const val LETRA_SP = 24f

    fun dp(context: Context, n: Float) = n * context.resources.displayMetrics.density
    fun sp(context: Context, n: Float) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, n, context.resources.displayMetrics)

    /** Alto de un renglón: crece con el tamaño de letra del celular para que nada se encime. */
    fun linea(context: Context) = maxOf(dp(context, 34f), sp(context, LETRA_SP) * 1.42f)

    /** Desde dónde empieza a escribirse, a la derecha del margen rojo. */
    fun margen(context: Context) = dp(context, 40f)

    fun letra(context: Context): Typeface = context.resources.getFont(R.font.caveat)
    fun letraNegrita(context: Context): Typeface = Typeface.create(letra(context), Typeface.BOLD)

    /** Si el celular tiene las animaciones desactivadas, todo pasa sin movimiento. */
    fun conMovimiento() = ValueAnimator.areAnimatorsEnabled()

    /** Salida fuerte: arranca rápido y se asienta suave. */
    val salida = PathInterpolator(0.23f, 1f, 0.32f, 1f)

    /**
     * Una raya hecha a mano de [x0] a [x1]: sube y baja un poco, nunca recta.
     * [semilla] hace que el mismo plan tenga siempre la misma raya.
     */
    fun rayaAMano(x0: Float, x1: Float, y: Float, amplitud: Float, semilla: Int): Path {
        val azar = Random(semilla)
        val tramos = 4
        val paso = (x1 - x0) / tramos
        return Path().apply {
            moveTo(x0, y + (azar.nextFloat() - 0.5f) * amplitud)
            for (i in 1..tramos) {
                val x = x0 + paso * i
                cubicTo(
                    x - paso * 0.66f, y + (azar.nextFloat() - 0.5f) * amplitud * 2,
                    x - paso * 0.33f, y + (azar.nextFloat() - 0.5f) * amplitud * 2,
                    x, y + (azar.nextFloat() - 0.5f) * amplitud,
                )
            }
        }
    }
}

/** La hoja: papel crema, renglones azules cada [Papel.linea] y el margen rojo. */
class HojaDrawable(context: Context) : Drawable() {
    private val linea = Papel.linea(context)
    private val margen = Papel.margen(context) - Papel.dp(context, 6f)
    private val primera = linea // el primer renglón queda libre, como en un cuaderno
    private val fondo = context.getColor(R.color.hoja)
    private val renglon = Paint().apply { color = context.getColor(R.color.renglon); strokeWidth = Papel.dp(context, 1f) }
    private val rojo = Paint().apply { color = context.getColor(R.color.margen); strokeWidth = Papel.dp(context, 1.5f) }

    private val d = Papel.dp(context, 1f)
    private val cafe = context.getColor(R.color.cafe)
    private val aro = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND }
    private val charco = Paint(Paint.ANTI_ALIAS_FLAG)

    /** Manchas de taza de café: aparecen con el uso. La semilla (la pareja) las deja siempre en su sitio. */
    var manchas = 0
        set(v) { if (field != v) { field = v; invalidateSelf() } }
    var semilla = 0

    /** Las que ya se limpiaron (no se dibujan) y cuánto se lleva frotada cada una (0..1). */
    var limpias: Set<Int> = emptySet()
        set(v) { if (field != v) { field = v; invalidateSelf() } }
    private val frotada = mutableMapOf<Int, Float>()

    data class Mancha(val numero: Int, val cx: Float, val cy: Float, val r: Float)

    /** Las manchas que se ven ahora, con su sitio (para saber si el dedo está encima). */
    fun visibles(): List<Mancha> = (0 until manchas).filter { it !in limpias }.map { lugar(it) }

    fun frotar(numero: Int, cuanto: Float): Float {
        val v = ((frotada[numero] ?: 0f) + cuanto).coerceIn(0f, 1f)
        frotada[numero] = v
        invalidateSelf()
        return v
    }

    private fun lugar(i: Int): Mancha {
        val azar = java.util.Random(semilla * 31L + i * 977L)
        val b = bounds
        val r = (30 + azar.nextFloat() * 12) * d
        val alto = minOf(b.height().toFloat(), 760 * d)
        val cx = b.left + b.width() * (0.3f + azar.nextFloat() * 0.6f)
        val cy = b.top + linea * 3 + (alto - linea * 3 - r) * azar.nextFloat()
        return Mancha(i, cx, cy, r)
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        canvas.drawColor(fondo)
        var y = b.top + primera
        while (y < b.bottom) {
            canvas.drawLine(b.left.toFloat(), y, b.right.toFloat(), y, renglon)
            y += linea
        }
        canvas.drawLine(b.left + margen, b.top.toFloat(), b.left + margen, b.bottom.toFloat(), rojo)
        for (i in 0 until manchas) if (i !in limpias) mancha(canvas, i)
    }

    /** El aro que deja el fondo de una taza: irregular, más oscuro en el borde y abierto donde se levantó. */
    private fun mancha(canvas: Canvas, i: Int) {
        val azar = java.util.Random(semilla * 31L + i * 977L)
        repeat(3) { azar.nextFloat() } // los mismos tres números que gastó lugar()
        val (_, cx, cy, r) = lugar(i)
        // Frotada se va aclarando; nunca del todo hasta que se da por limpia.
        val queda = 1f - (frotada[i] ?: 0f) * 0.85f
        val giro = azar.nextFloat() * 360f
        val fase = azar.nextFloat() * 6.28f
        fun borde(radio: Float, desde: Float, hasta: Float): Path = Path().apply {
            var a = desde
            var primero = true
            while (a <= hasta) {
                val t = Math.toRadians((a + giro).toDouble())
                val rr = radio * (1f + 0.035f * kotlin.math.sin(3 * t + fase).toFloat() + 0.02f * kotlin.math.sin(7 * t).toFloat())
                val px = cx + rr * kotlin.math.cos(t).toFloat(); val py = cy + rr * kotlin.math.sin(t).toFloat()
                if (primero) moveTo(px, py) else lineTo(px, py)
                primero = false
                a += 6f
            }
        }
        charco.color = cafe; charco.alpha = (14 * queda).toInt()
        canvas.drawPath(borde(r * 0.97f, 0f, 360f).apply { close() }, charco)
        aro.color = cafe
        aro.alpha = (72 * queda).toInt(); aro.strokeWidth = 2.4f * d
        canvas.drawPath(borde(r, 0f, 300f + azar.nextFloat() * 40f), aro)
        aro.alpha = (32 * queda).toInt(); aro.strokeWidth = 1.2f * d
        canvas.drawPath(borde(r * 0.9f, 40f, 250f), aro)
        // Una gota que cayó al lado.
        charco.alpha = (40 * queda).toInt()
        val t = Math.toRadians(giro + 200.0)
        canvas.drawCircle(cx + r * 1.35f * kotlin.math.cos(t).toFloat(), cy + r * 1.35f * kotlin.math.sin(t).toFloat(), 2.6f * d, charco)
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.OPAQUE
}
