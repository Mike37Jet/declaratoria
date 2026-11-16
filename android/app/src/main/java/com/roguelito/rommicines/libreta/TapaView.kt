package com.roguelito.rommicines.libreta

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.FloatPropertyCompat
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.roguelito.rommicines.R
import kotlin.math.abs

/**
 * La tapa dura de la libreta, de tela, con su etiqueta y el elástico que la mantiene cerrada.
 * Se abre deslizando el elástico hacia la derecha: se resiste un poco, se suelta y la tapa gira
 * sobre el lomo como un libro de verdad.
 */
class TapaView(context: Context) : View(context) {

    /** Se llama cuando la tapa terminó de abrirse (o de cerrarse, con false). */
    var alCambiar: ((abierta: Boolean) -> Unit)? = null

    var abierta = false
        private set

    var titulo = "Nuestras aventuras"
    var firma = ""

    private val d = context.resources.displayMetrics.density
    // Medido en lo que se ve estirado (ya con la resistencia): son ~70 dp de dedo.
    private val umbral = 30 * d
    private val anchoElastico = 11 * d
    private val lomo = 18 * d

    private val tela = context.getColor(R.color.tapa_tela)
    private val telaOscura = context.getColor(R.color.tapa_tela_oscura)
    private val trama = Paint().apply { color = 0x14FFFFFF; strokeWidth = 1 * d }
    private val tramaOscura = Paint().apply { color = 0x10000000; strokeWidth = 1 * d }
    private val lomoPintura = Paint()
    private val etiqueta = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.tapa_etiqueta) }
    private val etiquetaBorde = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.tinta_suave); style = Paint.Style.STROKE; strokeWidth = 1 * d; alpha = 90
    }
    private val sombra = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33000000 }
    private val letraTitulo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Papel.letraNegrita(context); textSize = Papel.sp(context, 30f); color = context.getColor(R.color.tinta_lapiz)
    }
    private val letraFirma = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Papel.letra(context); textSize = Papel.sp(context, 19f); color = context.getColor(R.color.tinta_suave)
    }
    private val letraPista = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Papel.letra(context); textSize = Papel.sp(context, 19f); color = context.getColor(R.color.tapa_etiqueta); alpha = 190
    }
    private val elastico = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.elastico) }
    private val brillo = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x2EFFFFFF; strokeWidth = 1.5f * d }

    /** Cuánto se ha corrido el elástico hacia la derecha (px). */
    private var estirado = 0f
        set(v) { field = v; invalidate() }

    private val resorteElastico = SpringAnimation(this, ESTIRADO, 0f).apply {
        // Rebota: el elástico es lo único de la libreta que tiene impulso propio.
        spring = SpringForce(0f).setDampingRatio(0.45f).setStiffness(SpringForce.STIFFNESS_MEDIUM)
    }
    private val giro = SpringAnimation(this, DynamicAnimation.ROTATION_Y, 0f).apply {
        spring = SpringForce(0f).setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY).setStiffness(170f)
        addUpdateListener { _, valor, _ ->
            // Pasados los 90° ya se vería la tapa por dentro: se oculta y deja ver la hoja.
            alpha = if (valor < -90f) 0f else 1f
        }
        addEndListener { _, cancelada, valor, _ ->
            if (cancelada) return@addEndListener
            if (valor <= -100f) { visibility = GONE; alCambiar?.invoke(true) }
            else alCambiar?.invoke(false)
        }
    }

    init {
        isClickable = true
        contentDescription = "Tapa de la libreta. Toca dos veces para abrirla."
        cameraDistance = 8000 * d
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        pivotX = 0f // gira sobre el lomo
        pivotY = h / 2f
        lomoPintura.shader = LinearGradient(0f, 0f, lomo, 0f, 0x40000000, 0x00000000, Shader.TileMode.CLAMP)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawColor(tela)
        // Tela: una trama fina en diagonal, apenas visible.
        var x = -h
        val paso = 5 * d
        while (x < w) {
            canvas.drawLine(x, h, x + h, 0f, trama)
            canvas.drawLine(x + paso / 2, 0f, x + paso / 2 + h, h, tramaOscura)
            x += paso
        }
        canvas.drawRect(0f, 0f, lomo, h, lomoPintura)
        canvas.drawLine(lomo, 0f, lomo, h, Paint().apply { color = telaOscura; strokeWidth = 2 * d })

        // La etiqueta pegada, un poco chueca.
        val anchoEtiqueta = minOf(w * 0.68f, 300 * d)
        val altoEtiqueta = 104 * d
        val cx = lomo + (w - lomo) * 0.47f
        val cy = h * 0.36f
        canvas.save()
        canvas.rotate(-2.2f, cx, cy)
        canvas.drawRoundRect(cx - anchoEtiqueta / 2 + 2 * d, cy - altoEtiqueta / 2 + 3 * d, cx + anchoEtiqueta / 2 + 2 * d, cy + altoEtiqueta / 2 + 3 * d, 6 * d, 6 * d, sombra)
        canvas.drawRoundRect(cx - anchoEtiqueta / 2, cy - altoEtiqueta / 2, cx + anchoEtiqueta / 2, cy + altoEtiqueta / 2, 6 * d, 6 * d, etiqueta)
        canvas.drawRoundRect(cx - anchoEtiqueta / 2 + 6 * d, cy - altoEtiqueta / 2 + 6 * d, cx + anchoEtiqueta / 2 - 6 * d, cy + altoEtiqueta / 2 - 6 * d, 4 * d, 4 * d, etiquetaBorde)
        canvas.drawText(titulo, cx - letraTitulo.measureText(titulo) / 2, cy + 2 * d, letraTitulo)
        if (firma.isNotEmpty()) canvas.drawText(firma, cx - letraFirma.measureText(firma) / 2, cy + 30 * d, letraFirma)
        canvas.restore()

        // La pista se desvanece en cuanto el elástico empieza a moverse.
        val pista = "desliza el elástico →"
        letraPista.alpha = (190 * (1f - (estirado / umbral)).coerceIn(0f, 1f)).toInt()
        canvas.drawText(pista, (w - letraPista.measureText(pista)) / 2 + lomo / 2, h - 40 * d, letraPista)

        // El elástico: abraza la tapa de arriba a abajo cerca del borde derecho.
        val ex = w - 34 * d + estirado
        canvas.drawRect(ex, 0f, ex + anchoElastico, h, elastico)
        canvas.drawLine(ex + 2.5f * d, 0f, ex + 2.5f * d, h, brillo)
    }

    // --- Deslizar el elástico ---------------------------------------------------------

    private val toque = ViewConfiguration.get(context).scaledTouchSlop
    private var x0 = 0f
    private var desde = 0f
    private var arrastrando = false
    private var velocidad: VelocityTracker? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (abierta) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                resorteElastico.cancel()
                x0 = e.x; desde = estirado; arrastrando = false
                velocidad = VelocityTracker.obtain().also { it.addMovement(e) }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                velocidad?.addMovement(e)
                val dx = e.x - x0
                if (!arrastrando && abs(dx) > toque) { arrastrando = true; x0 = e.x }
                if (arrastrando) {
                    // Sigue al dedo, pero cada vez cuesta más: es un elástico.
                    estirado = gomita(desde + (e.x - x0)).coerceAtLeast(-4 * d)
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                velocidad?.computeCurrentVelocity(1000)
                val v = velocidad?.xVelocity ?: 0f
                velocidad?.recycle(); velocidad = null
                if (!arrastrando) {
                    if (e.actionMasked == MotionEvent.ACTION_UP) performClick()
                    return true
                }
                arrastrando = false
                if (e.actionMasked == MotionEvent.ACTION_UP && (estirado > umbral || (v > 500 * d && estirado > 10 * d))) abrir(v)
                else soltar(v)
                return true
            }
        }
        return super.onTouchEvent(e)
    }

    /** Un toque sin arrastrar: el elástico tiembla para decir «desliza aquí». */
    override fun performClick(): Boolean {
        super.performClick()
        if (abierta) return true
        if (!Papel.conMovimiento()) return true
        resorteElastico.setStartVelocity(900 * d)
        resorteElastico.animateToFinalPosition(0f)
        return true
    }

    override fun performAccessibilityAction(action: Int, arguments: android.os.Bundle?): Boolean {
        if (action == android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK && !abierta) { abrir(0f); return true }
        return super.performAccessibilityAction(action, arguments)
    }

    private fun gomita(dx: Float): Float {
        if (dx <= 0) return dx
        val limite = width * 0.5f
        return (dx * limite * 0.55f) / (limite + 0.55f * dx)
    }

    private fun soltar(v: Float) {
        resorteElastico.setStartVelocity(v)
        resorteElastico.animateToFinalPosition(0f)
    }

    fun abrir(v: Float = 0f) {
        if (abierta) return
        abierta = true
        performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        if (!Papel.conMovimiento()) {
            animate().alpha(0f).setDuration(180).withEndAction { visibility = GONE; alCambiar?.invoke(true) }.start()
            return
        }
        // El elástico se zafa por el borde y, ya libre, la tapa gira sobre el lomo.
        resorteElastico.cancel()
        val zafar = SpringAnimation(this, ESTIRADO, 60 * d + 34 * d).apply {
            spring.setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY).setStiffness(SpringForce.STIFFNESS_MEDIUM)
            setStartVelocity(maxOf(v, 400 * d))
        }
        zafar.start()
        postDelayed({
            giro.setStartVelocity(-maxOf(v / d, 200f) * 0.25f)
            giro.animateToFinalPosition(-110f)
        }, 90)
    }

    /** Vuelve a cerrar la libreta (botón atrás con la tapa abierta). */
    fun cerrar() {
        if (!abierta) return
        abierta = false
        visibility = VISIBLE
        if (!Papel.conMovimiento()) {
            rotationY = 0f; estirado = 0f; alpha = 0f
            animate().alpha(1f).setDuration(180).withEndAction { alCambiar?.invoke(false) }.start()
            return
        }
        alpha = 0f
        rotationY = -110f
        estirado = 0f
        giro.animateToFinalPosition(0f)
    }

    /** Mostrarla abierta sin animación (al volver a la pantalla). */
    fun dejarAbierta() {
        abierta = true
        rotationY = -110f
        visibility = GONE
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        resorteElastico.cancel(); giro.cancel()
    }

    companion object {
        private val ESTIRADO = object : FloatPropertyCompat<TapaView>("estirado") {
            override fun getValue(v: TapaView) = v.estirado
            override fun setValue(v: TapaView, valor: Float) { v.estirado = valor }
        }
    }
}
