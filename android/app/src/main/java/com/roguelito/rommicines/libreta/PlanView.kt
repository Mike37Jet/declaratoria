package com.roguelito.rommicines.libreta

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityNodeInfo
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import androidx.dynamicanimation.animation.FloatPropertyCompat
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.roguelito.rommicines.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * Un plan escrito en la hoja. Se tacha arrastrando el dedo sobre él, como con un lápiz;
 * al soltar pasada la mitad, la raya se completa y cae el sello «¡LO HICIMOS!».
 */
@SuppressLint("ViewConstructor")
class PlanView(context: Context, plan: Plan) : View(context) {

    var plan: Plan = plan
        private set

    /** Qué hacer cuando la persona termina de tachar (true) o pide quitar la raya (false). */
    var alTachar: ((Plan, Boolean) -> Unit)? = null
    /** Tocar un plan ya tachado abre su página del álbum. */
    var alTocar: ((Plan) -> Unit)? = null

    private val linea = Papel.linea(context)
    private val margen = Papel.margen(context)
    private val derecha = Papel.dp(context, 20f)
    private val semilla = plan.id.hashCode()

    private val letra = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Papel.letra(context)
        textSize = Papel.sp(context, Papel.LETRA_SP)
    }
    private val raya = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = Papel.dp(context, 2.6f)
        strokeCap = Paint.Cap.ROUND
    }
    private val tintaSello = context.getColor(R.color.sello)
    private val sello = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = tintaSello
        typeface = Papel.letraNegrita(context)
        textSize = Papel.sp(context, 12.5f)
        letterSpacing = 0.06f
    }
    private val selloFecha = Paint(sello).apply { textSize = Papel.sp(context, 10f); letterSpacing = 0.02f }
    private val selloBorde = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = tintaSello
        style = Paint.Style.STROKE
        strokeWidth = Papel.dp(context, 1.8f)
    }

    private var texto: StaticLayout? = null
    private val rayas = mutableListOf<Path>()
    private val medidas = mutableListOf<Float>()
    private var largoTotal = 0f
    private val tramo = Path()

    /** 0..1: cuánto de la letra ya apareció (los planes nuevos del otro se escriben frente a ti). */
    private var revelado = 1f
    /** 0..1: cuánto de la raya está hecho. */
    private var tachado = if (plan.hecho != null) 1f else 0f
    /** 0..1: el sello cayendo sobre la hoja. */
    private var golpe = if (plan.hecho != null) 1f else 0f

    private var escritura: ValueAnimator? = null
    private var selloAnim: ValueAnimator? = null
    private val resorte = SpringAnimation(this, TACHADO, 0f).apply {
        spring = SpringForce(0f).setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY).setStiffness(SpringForce.STIFFNESS_MEDIUM)
    }

    init {
        isClickable = true
        isLongClickable = true
        contentDescription = plan.texto
    }

    fun actualizar(nuevo: Plan, animar: Boolean) {
        val antes = plan
        plan = nuevo
        contentDescription = nuevo.texto
        if (antes.texto != nuevo.texto) { texto = null; requestLayout() }
        val hecho = nuevo.hecho != null
        if (hecho == (antes.hecho != null)) return invalidate()
        // Lo tachó el otro (o se deshizo): la raya se dibuja sola, sin vibrar aquí.
        if (hecho) completar(animar, conVibracion = false) else quitar(animar)
    }

    /** La primera vez que ves un plan que escribió el otro: la tinta corre de izquierda a derecha. */
    fun escribirFrenteATi() {
        if (!Papel.conMovimiento()) return
        val largo = plan.texto.length
        escritura?.cancel()
        revelado = 0f
        escritura = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = (largo * 45L).coerceIn(500L, 1800L)
            interpolator = LinearInterpolator() // la mano escribe a ritmo parejo
            startDelay = 120
            addUpdateListener { revelado = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val ancho = MeasureSpec.getSize(widthMeasureSpec)
        armar(ancho)
        val renglones = texto?.lineCount ?: 1
        setMeasuredDimension(ancho, (renglones * linea).toInt())
    }

    private fun armar(ancho: Int) {
        val util = (ancho - margen - derecha).toInt().coerceAtLeast(1)
        if (texto?.width == util) return
        texto = StaticLayout.Builder.obtain(plan.texto, 0, plan.texto.length, letra, util)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .build()
        rayas.clear(); medidas.clear(); largoTotal = 0f
        val t = texto!!
        for (i in 0 until t.lineCount) {
            val x0 = margen - Papel.dp(context, 4f)
            val x1 = margen + t.getLineWidth(i) + Papel.dp(context, 6f)
            val y = i * linea + linea * 0.58f
            val camino = Papel.rayaAMano(x0, x1, y, Papel.dp(context, 2.2f), semilla + i)
            rayas += camino
            val largo = PathMeasure(camino, false).length
            medidas += largo
            largoTotal += largo
        }
    }

    override fun onDraw(canvas: Canvas) {
        val t = texto ?: return
        val color = context.getColor(plan.tinta.color)
        letra.color = color
        // Un plan cumplido se queda en la hoja, pero un poco más gastado.
        letra.alpha = if (tachado >= 1f) 165 else 255

        // La letra, renglón por renglón, cada uno sobre su raya.
        val porRenglon = 1f / t.lineCount
        for (i in 0 until t.lineCount) {
            val visible = ((revelado - i * porRenglon) / porRenglon).coerceIn(0f, 1f)
            if (visible <= 0f) break
            val base = (i + 1) * linea - Papel.dp(context, 8f)
            val inicio = t.getLineStart(i)
            val fin = t.getLineVisibleEnd(i)
            canvas.save()
            if (visible < 1f) canvas.clipRect(margen, i * linea, margen + t.getLineWidth(i) * visible, (i + 1) * linea)
            canvas.drawText(plan.texto, inicio, fin, margen, base, letra)
            canvas.restore()
        }

        // La raya: se dibuja en orden, renglón tras renglón, tanto como diga [tachado].
        if (tachado > 0f) {
            raya.color = color
            var falta = tachado * largoTotal
            for (i in rayas.indices) {
                if (falta <= 0f) break
                val largo = medidas[i]
                tramo.reset()
                PathMeasure(rayas[i], false).getSegment(0f, minOf(falta, largo), tramo, true)
                canvas.drawPath(tramo, raya)
                falta -= largo
            }
        }

        if (golpe > 0f) dibujarSello(canvas)
    }

    private fun dibujarSello(canvas: Canvas) {
        val titulo = "¡LO HICIMOS!"
        val fecha = plan.hecho?.let { FECHA.format(Date(it)) } ?: ""
        val anchoTitulo = sello.measureText(titulo)
        val relleno = Papel.dp(context, 4f)
        val ancho = anchoTitulo + relleno * 2
        val alto = sello.textSize + selloFecha.textSize + relleno * 2f
        val cx = width - derecha - ancho / 2
        val cy = minOf(height.toFloat(), linea) / 2
        // Cae desde más grande y se asienta; queda un poco transparente, como tinta de sello.
        val escala = 1.6f - 0.6f * golpe
        val giro = -8f + (abs(semilla) % 9) - 4f
        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(giro)
        canvas.scale(escala, escala)
        val alfa = (217 * golpe.coerceIn(0f, 1f)).toInt()
        sello.alpha = alfa; selloFecha.alpha = alfa; selloBorde.alpha = alfa
        canvas.drawRoundRect(-ancho / 2, -alto / 2, ancho / 2, alto / 2, Papel.dp(context, 3f), Papel.dp(context, 3f), selloBorde)
        canvas.drawText(titulo, -anchoTitulo / 2, -alto / 2 + relleno + sello.textSize * 0.8f, sello)
        canvas.drawText(fecha, -selloFecha.measureText(fecha) / 2, alto / 2 - relleno * 0.9f, selloFecha)
        canvas.restore()
    }

    // --- Tachar con el dedo ------------------------------------------------------------

    private val toque = ViewConfiguration.get(context).scaledTouchSlop
    private var x0 = 0f
    private var y0 = 0f
    private var arrastrando = false
    private var desde = 0f
    private var pasoMitad = false
    private var velocidad: VelocityTracker? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (plan.hecho != null) return super.onTouchEvent(e) // tachado: solo pulsación larga
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                x0 = e.x; y0 = e.y; arrastrando = false
                resorte.cancel()
                desde = tachado
                pasoMitad = tachado >= 0.5f
                velocidad = VelocityTracker.obtain().also { it.addMovement(e) }
            }
            MotionEvent.ACTION_MOVE -> {
                velocidad?.addMovement(e)
                val dx = e.x - x0
                if (!arrastrando && abs(dx) > toque && abs(dx) > abs(e.y - y0)) {
                    arrastrando = true
                    x0 = e.x // sin salto: la raya empieza donde el dedo decidió tachar
                    parent.requestDisallowInterceptTouchEvent(true)
                }
                if (arrastrando) {
                    val ancho = (texto?.let { t -> (0 until t.lineCount).maxOf { t.getLineWidth(it) } } ?: width.toFloat())
                        .coerceAtLeast(Papel.dp(context, 60f))
                    tachado = (desde + (e.x - x0) / ancho).coerceIn(0f, 1f)
                    val ahora = tachado >= 0.5f
                    if (ahora != pasoMitad) { pasoMitad = ahora; performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
                    invalidate()
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (arrastrando) {
                    velocidad?.computeCurrentVelocity(1000)
                    val v = velocidad?.xVelocity ?: 0f
                    velocidad?.recycle(); velocidad = null
                    arrastrando = false
                    val rapido = v > Papel.dp(context, 900f) && tachado > 0.15f
                    if (e.actionMasked == MotionEvent.ACTION_UP && (tachado > 0.5f || rapido)) {
                        completar(animar = true, conVibracion = true, velocidadInicial = v)
                        alTachar?.invoke(plan, true)
                    } else {
                        volver()
                    }
                    return true
                }
                velocidad?.recycle(); velocidad = null
            }
        }
        return super.onTouchEvent(e)
    }

    override fun performLongClick(): Boolean {
        if (plan.hecho != null) {
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            alTachar?.invoke(plan, false)
            return true
        }
        return super.performLongClick()
    }

    /** Para TalkBack: tocar dos veces tacha (no hay forma de arrastrar con el lector). */
    override fun performClick(): Boolean {
        super.performClick()
        if (plan.hecho != null) alTocar?.invoke(plan)
        return true
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.stateDescription = if (plan.hecho != null) "Cumplido" else "Pendiente"
        info.addAction(
            if (plan.hecho == null) AccessibilityNodeInfo.AccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, "Tachar")
            else AccessibilityNodeInfo.AccessibilityAction(AccessibilityNodeInfo.ACTION_LONG_CLICK, "Quitar el tachado")
        )
    }

    override fun performAccessibilityAction(action: Int, arguments: android.os.Bundle?): Boolean {
        if (action == AccessibilityNodeInfo.ACTION_CLICK && plan.hecho == null) {
            completar(animar = true, conVibracion = true)
            alTachar?.invoke(plan, true)
            return true
        }
        return super.performAccessibilityAction(action, arguments)
    }

    private fun volver() {
        if (!Papel.conMovimiento()) { tachado = 0f; invalidate(); return }
        resorte.setStartValue(tachado)
        resorte.animateToFinalPosition(0f)
    }

    private fun completar(animar: Boolean, conVibracion: Boolean, velocidadInicial: Float = 0f) {
        resorte.cancel()
        if (!animar || !Papel.conMovimiento()) {
            tachado = 1f; golpe = 1f; invalidate(); return
        }
        if (conVibracion) performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        // La raya termina sola (rápido si el dedo venía rápido) y al llegar cae el sello.
        val inicio = tachado
        ValueAnimator.ofFloat(inicio, 1f).apply {
            duration = if (velocidadInicial > 0) 160 else ((1f - inicio) * 520).toLong().coerceAtLeast(160)
            interpolator = Papel.salida
            addUpdateListener { tachado = it.animatedValue as Float; invalidate() }
            doOnEnd { estampar(conVibracion) }
            start()
        }
    }

    private fun estampar(conVibracion: Boolean) {
        selloAnim?.cancel()
        golpe = 0f
        selloAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 260
            interpolator = OvershootInterpolator(1.4f)
            var golpeo = false
            addUpdateListener {
                golpe = it.animatedValue as Float
                if (!golpeo && it.animatedFraction > 0.55f) {
                    golpeo = true
                    if (conVibracion) performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                }
                invalidate()
            }
            start()
        }
    }

    private fun quitar(animar: Boolean) {
        selloAnim?.cancel()
        if (!animar || !Papel.conMovimiento()) { tachado = 0f; golpe = 0f; invalidate(); return }
        ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 220
            interpolator = Papel.salida
            addUpdateListener {
                val f = it.animatedValue as Float
                golpe = f; tachado = f; invalidate()
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        escritura?.cancel(); selloAnim?.cancel(); resorte.cancel()
    }

    companion object {
        private val FECHA = SimpleDateFormat("d MMM yyyy", Locale.forLanguageTag("es"))

        private val TACHADO = object : FloatPropertyCompat<PlanView>("tachado") {
            override fun getValue(v: PlanView) = v.tachado
            override fun setValue(v: PlanView, valor: Float) { v.tachado = valor.coerceIn(0f, 1f); v.invalidate() }
        }
    }
}

private inline fun ValueAnimator.doOnEnd(crossinline accion: () -> Unit) {
    addListener(object : android.animation.AnimatorListenerAdapter() {
        override fun onAnimationEnd(animation: android.animation.Animator) = accion()
    })
}
