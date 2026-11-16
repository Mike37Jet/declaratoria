package com.roguelito.rommicines.libreta

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.view.View
import com.roguelito.rommicines.R
import kotlin.random.Random

/** Cómo se reparten las marcas: grupos de cinco (cuatro palitos y uno cruzado), varios por renglón. */
object Marcas {
    const val POR_GRUPO = 5

    fun grupos(n: Int) = (n + POR_GRUPO - 1) / POR_GRUPO

    /** Cuántos renglones hacen falta para [n] marcas si caben [porRenglon] grupos en cada uno. */
    fun renglones(n: Int, porRenglon: Int) = maxOf(1, (grupos(n) + porRenglon - 1) / maxOf(1, porRenglon))
}

/**
 * El contador de aventuras dibujado a mano: |||| con la quinta cruzada.
 * Cuando se cumple un plan, la marca nueva se traza frente a ti.
 */
class ContadorView(context: Context) : View(context) {

    private val linea = Papel.linea(context)
    private val margen = Papel.margen(context)
    private val separacion = Papel.dp(context, 9f)
    private val entreGrupos = Papel.dp(context, 16f)
    private val anchoGrupo = separacion * 3 + entreGrupos + Papel.dp(context, 6f)
    private val lapiz = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.tinta_lapiz)
        style = Paint.Style.STROKE
        strokeWidth = Papel.dp(context, 2.2f)
        strokeCap = Paint.Cap.ROUND
    }
    private val tramo = Path()

    private var cuenta = 0
    /** 0..1 de la última marca (la recién ganada). */
    private var ultima = 1f
    private var anim: ValueAnimator? = null

    fun mostrar(n: Int, animar: Boolean) {
        val antes = cuenta
        cuenta = n
        contentDescription = "$n de ${Libreta.META} aventuras"
        if (Marcas.renglones(antes, porRenglon()) != Marcas.renglones(n, porRenglon())) requestLayout()
        anim?.cancel()
        if (animar && n == antes + 1 && Papel.conMovimiento()) {
            ultima = 0f
            anim = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 220
                startDelay = 380 // después del sello: primero se celebra, luego se cuenta
                interpolator = Papel.salida
                addUpdateListener { ultima = it.animatedValue as Float; invalidate() }
                start()
            }
        } else {
            ultima = 1f
        }
        invalidate()
    }

    private fun porRenglon(): Int =
        if (width == 0) 6 else ((width - margen - Papel.dp(context, 16f)) / anchoGrupo).toInt().coerceAtLeast(1)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val ancho = MeasureSpec.getSize(widthMeasureSpec)
        val porRenglon = ((ancho - margen - Papel.dp(context, 16f)) / anchoGrupo).toInt().coerceAtLeast(1)
        setMeasuredDimension(ancho, (Marcas.renglones(cuenta, porRenglon) * linea).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        if (cuenta == 0) return
        val porRenglon = porRenglon()
        for (i in 0 until cuenta) {
            val grupo = i / Marcas.POR_GRUPO
            val enGrupo = i % Marcas.POR_GRUPO
            val renglon = grupo / porRenglon
            val x = margen + (grupo % porRenglon) * anchoGrupo
            val arriba = renglon * linea + linea * 0.2f
            val abajo = (renglon + 1) * linea - linea * 0.14f
            val azar = Random(i * 31 + 7)
            val bambolea = { azar.nextFloat() * Papel.dp(context, 2.4f) - Papel.dp(context, 1.2f) }
            val camino = Path()
            if (enGrupo < 4) {
                val px = x + enGrupo * separacion
                camino.moveTo(px + bambolea(), arriba + bambolea())
                camino.quadTo(px + bambolea(), (arriba + abajo) / 2, px + bambolea(), abajo + bambolea())
            } else {
                // La quinta cruza las otras cuatro en diagonal.
                camino.moveTo(x - Papel.dp(context, 5f), abajo - Papel.dp(context, 4f) + bambolea())
                camino.quadTo(x + separacion * 1.5f, (arriba + abajo) / 2 + bambolea(), x + separacion * 3 + Papel.dp(context, 5f), arriba + Papel.dp(context, 4f) + bambolea())
            }
            if (i == cuenta - 1 && ultima < 1f) {
                val medida = PathMeasure(camino, false)
                tramo.reset()
                medida.getSegment(0f, medida.length * ultima, tramo, true)
                canvas.drawPath(tramo, lapiz)
            } else {
                canvas.drawPath(camino, lapiz)
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        anim?.cancel()
    }
}
