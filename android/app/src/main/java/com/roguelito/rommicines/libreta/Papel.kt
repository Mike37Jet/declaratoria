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

    override fun draw(canvas: Canvas) {
        val b = bounds
        canvas.drawColor(fondo)
        var y = b.top + primera
        while (y < b.bottom) {
            canvas.drawLine(b.left.toFloat(), y, b.right.toFloat(), y, renglon)
            y += linea
        }
        canvas.drawLine(b.left + margen, b.top.toFloat(), b.left + margen, b.bottom.toFloat(), rojo)
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.OPAQUE
}
