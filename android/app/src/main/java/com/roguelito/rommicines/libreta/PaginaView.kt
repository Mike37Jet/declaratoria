package com.roguelito.rommicines.libreta

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.LinearLayout
import kotlin.math.abs
import kotlin.math.hypot

/**
 * La hoja de la libreta. Las manchas de café (las dibuja [HojaDrawable]) se limpian frotando
 * con el dedo de un lado a otro; al quedar limpia se avisa con [alLimpiar] y se quita en los dos celulares.
 */
class PaginaView(context: Context, private val papel: HojaDrawable) : LinearLayout(context) {

    /** Se llama al terminar de limpiar una mancha. */
    var alLimpiar: ((Int) -> Unit)? = null

    private val d = resources.displayMetrics.density

    init {
        orientation = VERTICAL
        background = papel
    }

    private var mancha: HojaDrawable.Mancha? = null
    private var frotando = false
    private var ultimoX = 0f
    private var sentido = 0 // hacia dónde va el dedo en x: 1, -1
    private var tramo = 0f // lo recorrido desde la última vuelta
    private var vueltas = 0

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                ultimoX = e.x
                sentido = 0; tramo = 0f; vueltas = 0; frotando = false
                mancha = papel.visibles().firstOrNull { hypot(it.cx - e.x, it.cy - e.y) < it.r * 1.25f }
            }
            MotionEvent.ACTION_MOVE -> if (mancha != null && contarVaiven(e.x)) {
                // Ida y vuelta sobre la mancha: es frotar, no tachar ni desplazar.
                frotando = true
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> mancha = null
        }
        return false
    }

    /** Cuenta las veces que el dedo cambia de sentido en x; con una ya se nota que frota. */
    private fun contarVaiven(x: Float): Boolean {
        val paso = x - ultimoX
        ultimoX = x
        if (abs(paso) < 0.5f) return vueltas >= 1
        val ahora = if (paso > 0) 1 else -1
        if (sentido != 0 && ahora != sentido && tramo > 10 * d) { vueltas++; tramo = 0f }
        sentido = ahora
        tramo += abs(paso)
        return vueltas >= 1
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val ma = mancha ?: return false
        // Si el dedo cayó en papel vacío (sin plan debajo), el gesto llega aquí directo.
        if (e.actionMasked == MotionEvent.ACTION_DOWN) return true
        when (e.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                if (!frotando) {
                    if (contarVaiven(e.x)) { frotando = true; parent?.requestDisallowInterceptTouchEvent(true) }
                    return true
                }
                val paso = abs(e.x - ultimoX)
                contarVaiven(e.x)
                // Unas cuatro o cinco pasadas de lado a lado la dejan limpia.
                if (papel.frotar(ma.numero, paso / (ma.r * 9f)) >= 1f) limpiar(ma.numero)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { mancha = null; frotando = false }
        }
        return true
    }

    private fun limpiar(numero: Int) {
        performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        papel.limpias = papel.limpias + numero
        alLimpiar?.invoke(numero)
        mancha = null; frotando = false
    }

    // Con lector de pantalla: una acción para limpiar la primera mancha.
    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        if (papel.visibles().isNotEmpty())
            info.addAction(AccessibilityNodeInfo.AccessibilityAction(ACCION_LIMPIAR, "Limpiar una mancha de café"))
    }

    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
        if (action == ACCION_LIMPIAR) papel.visibles().firstOrNull()?.let { limpiar(it.numero); return true }
        return super.performAccessibilityAction(action, arguments)
    }

    companion object {
        private val ACCION_LIMPIAR = com.roguelito.rommicines.R.id.accion_limpiar_mancha
    }
}
