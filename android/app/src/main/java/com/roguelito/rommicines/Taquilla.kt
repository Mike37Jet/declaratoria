package com.roguelito.rommicines

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View

/**
 * La marquesina del cine: «ROMMICINES» en dorado entre dos filas de focos que se van
 * encendiendo despacio, uno tras otro. Sin animaciones del sistema, los focos quedan fijos.
 */
class Marquesina(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val d = resources.displayMetrics.density
    private val marco = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2 * d; color = context.getColor(R.color.gold) }
    private val fondo = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.taquilla_panel) }
    private val foco = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.gold_light) }
    private val titulo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.gold); textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD); letterSpacing = 0.14f
    }
    private val sub = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.cream_soft); textAlign = Paint.Align.CENTER; letterSpacing = 0.18f
        textSize = 10 * resources.configuration.fontScale.coerceAtMost(1.3f) * d
    }
    private val caja = RectF()

    /** Qué foco va encendido (avanza de a uno). */
    private var paso = 0
    private val luces = ValueAnimator.ofInt(0, 1000).apply {
        duration = 1000L * 700 // un paso cada 0,7 s: un pasillo de cine, no una alarma
        repeatCount = ValueAnimator.INFINITE
        interpolator = null
        addUpdateListener { val p = it.animatedValue as Int; if (p != paso) { paso = p; invalidate() } }
    }

    init {
        contentDescription = "ROMMICINES, función privada de Roguelito y Mimina"
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    override fun onMeasure(w: Int, h: Int) {
        val alto = (18 * d + titulo(MeasureSpec.getSize(w)).textSize * 1.25f + sub.textSize * 1.6f + 18 * d + 12 * d).toInt()
        setMeasuredDimension(MeasureSpec.getSize(w), alto)
    }

    private fun titulo(ancho: Int): Paint {
        titulo.textSize = 26 * d
        val disponible = ancho - 28 * d
        val mide = titulo.measureText("ROMMICINES")
        if (mide > disponible) titulo.textSize *= disponible / mide
        return titulo
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (ValueAnimator.areAnimatorsEnabled()) luces.start()
    }

    override fun onDetachedFromWindow() {
        luces.cancel()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (!ValueAnimator.areAnimatorsEnabled()) return
        if (isVisible) { if (!luces.isStarted) luces.start() else luces.resume() } else luces.pause()
    }

    override fun onDraw(c: Canvas) {
        val m = marco.strokeWidth
        caja.set(m, m, width - m, height - m)
        c.drawRoundRect(caja, 10 * d, 10 * d, fondo)
        c.drawRoundRect(caja, 10 * d, 10 * d, marco)

        // Focos: arriba y abajo, encendidos de a dos de cada tres, y la «ola» avanza.
        val r = 4.2f * d
        val n = ((width - 32 * d) / (17 * d)).toInt().coerceAtLeast(3)
        val paso2 = (width - 32 * d) / (n - 1)
        for (fila in 0..1) {
            val y = if (fila == 0) 12 * d else height - 12 * d
            for (i in 0 until n) {
                val k = if (fila == 0) i else n - 1 - i // la de abajo va al revés: dan la vuelta
                foco.alpha = if ((k + paso) % 3 == 0) 90 else 255
                c.drawCircle(16 * d + i * paso2, y, r, foco)
            }
        }
        val t = titulo(width)
        val base = 18 * d + t.textSize
        c.drawText("ROMMICINES", width / 2f, base, t)
        c.drawText("FUNCIÓN PRIVADA · ROGUELITO & MIMINA", width / 2f, base + sub.textSize * 1.7f, sub)
    }
}

/**
 * El contador como boleto de cine: «ADMIT TWO», los días y la hora a la izquierda;
 * el talón con el número de función (los días juntos) a la derecha, separado por la línea
 * de corte con sus dos muescas.
 */
class Boleto(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val d = resources.displayMetrics.density
    private val escala = resources.configuration.fontScale.coerceIn(1f, 1.3f)
    private val papel = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.gold_light) }
    private val tinta = context.getColor(R.color.taquilla_tinta)
    private val chico = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tinta; textSize = 10 * escala * d; letterSpacing = 0.16f }
    private val grande = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tinta; textSize = 30 * escala * d; typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD) }
    private val medio = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tinta; textSize = 17 * escala * d; typeface = Typeface.MONOSPACE }
    private val corte = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = tinta; alpha = 140; strokeWidth = 1.5f * d; style = Paint.Style.STROKE
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(5 * d, 4 * d), 0f)
    }
    private val forma = Path()

    var tiempo: Juntos.Tiempo = Juntos.ahora()
        set(v) {
            val cambioDia = v.dias != field.dias
            field = v; invalidate()
            if (cambioDia) contentDescription = descripcion()
        }

    init {
        contentDescription = descripcion()
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    // El lector de pantalla no lee los segundos: cambian cada segundo y no se terminarían de decir.
    private fun descripcion() = "Juntos desde el sí: ${tiempo.dias} ${if (tiempo.dias == 1L) "día" else "días"}. Función número ${tiempo.dias}"

    private val anchoTalon get() = 84 * escala * d

    override fun onMeasure(w: Int, h: Int) {
        val alto = 12 * d + chico.textSize * 1.4f + grande.textSize * 1.1f + medio.textSize * 1.3f + 12 * d
        setMeasuredDimension(MeasureSpec.getSize(w), alto.toInt())
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val x = w - anchoTalon
        val muesca = 7 * d
        // El papel con las dos muescas en la línea de corte (arriba y abajo).
        forma.reset()
        forma.addRoundRect(0f, 0f, w, h, 8 * d, 8 * d, Path.Direction.CW)
        val hueco = Path().apply {
            addCircle(x, 0f, muesca, Path.Direction.CW)
            addCircle(x, h, muesca, Path.Direction.CW)
        }
        forma.op(hueco, Path.Op.DIFFERENCE)
        c.drawPath(forma, papel)
        c.drawLine(x, muesca + 3 * d, x, h - muesca - 3 * d, corte)

        val izq = 14 * d
        var y = 12 * d + chico.textSize
        c.drawText("ADMIT TWO · DESDE EL SÍ", izq, y, chico)
        y += grande.textSize * 1.1f
        val dias = tiempo.dias.toString()
        c.drawText(dias, izq, y, grande)
        medio.textSize = 14 * escala * d
        c.drawText(if (tiempo.dias == 1L) " día" else " días", izq + grande.measureText(dias), y, medio)
        medio.textSize = 17 * escala * d
        y += medio.textSize * 1.3f
        val seg = tiempo.msEnLaHora / 1000
        c.drawText("%02d : %02d : %02d".format(tiempo.horas, seg / 60, seg % 60), izq, y, medio)

        // El talón.
        val centro = x + anchoTalon / 2
        chico.textAlign = Paint.Align.CENTER
        c.drawText("FUNCIÓN", centro, h / 2 - medio.textSize * 0.9f, chico)
        val numero = "Nº ${tiempo.dias}"
        medio.textSize = (20 * escala * d).coerceAtMost((anchoTalon - 8 * d) / (numero.length * 0.62f))
        medio.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD); medio.textAlign = Paint.Align.CENTER
        c.drawText(numero, centro, h / 2 + medio.textSize * 0.35f, medio)
        medio.typeface = Typeface.MONOSPACE; medio.textAlign = Paint.Align.LEFT; medio.textSize = 17 * escala * d
        c.drawText("fila ♥\uFE0E · asiento 2", centro, h / 2 + 22 * escala * d, chico.apply { letterSpacing = 0f })
        chico.letterSpacing = 0.16f; chico.textAlign = Paint.Align.LEFT
    }
}

/** Pone sus hijos uno al lado del otro y pasa al renglón siguiente cuando no caben (las frases del toque). */
class Renglones(context: Context, attrs: AttributeSet? = null) : android.view.ViewGroup(context, attrs) {
    private val hueco = (8 * resources.displayMetrics.density).toInt()

    override fun onMeasure(w: Int, h: Int) {
        val ancho = MeasureSpec.getSize(w) - paddingLeft - paddingRight
        var x = 0; var y = 0; var alto = 0
        for (i in 0 until childCount) {
            val v = getChildAt(i)
            if (v.visibility == GONE) continue
            v.measure(MeasureSpec.makeMeasureSpec(ancho, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
            if (x > 0 && x + v.measuredWidth > ancho) { x = 0; y += alto + hueco; alto = 0 }
            x += v.measuredWidth + hueco
            alto = maxOf(alto, v.measuredHeight)
        }
        setMeasuredDimension(MeasureSpec.getSize(w), y + alto + paddingTop + paddingBottom)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val ancho = r - l - paddingLeft - paddingRight
        var x = 0; var y = 0; var alto = 0
        for (i in 0 until childCount) {
            val v = getChildAt(i)
            if (v.visibility == GONE) continue
            if (x > 0 && x + v.measuredWidth > ancho) { x = 0; y += alto + hueco; alto = 0 }
            v.layout(paddingLeft + x, paddingTop + y, paddingLeft + x + v.measuredWidth, paddingTop + y + v.measuredHeight)
            x += v.measuredWidth + hueco
            alto = maxOf(alto, v.measuredHeight)
        }
    }
}
