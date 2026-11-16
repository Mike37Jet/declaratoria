package com.roguelito.rommicines.libreta

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.google.firebase.firestore.ListenerRegistration
import com.roguelito.rommicines.R
import kotlin.math.abs

/** El álbum de recortes: un recuerdo por página, y las páginas se pasan con el dedo. */
class AlbumActivity : Activity() {

    private lateinit var libro: LibroView
    private lateinit var pagina: TextView
    private var escucha: ListenerRegistration? = null
    private var irA: String? = null
    private var recien = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        irA = intent.getStringExtra(IR_A)
        recien = intent.getBooleanExtra(RECIEN, false)
        val d = resources.displayMetrics.density
        val escritorio = FrameLayout(this).apply { setBackgroundColor(getColor(R.color.escritorio)) }
        escritorio.setOnApplyWindowInsetsListener { v, insets ->
            @Suppress("DEPRECATION")
            v.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            insets
        }
        libro = LibroView(this)
        escritorio.addView(libro, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT).apply {
            setMargins((12 * d).toInt(), (12 * d).toInt(), (12 * d).toInt(), (52 * d).toInt())
        })
        pagina = TextView(this).apply {
            typeface = Papel.letra(this@AlbumActivity)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setTextColor(getColor(R.color.tapa_etiqueta))
            gravity = Gravity.CENTER
        }
        escritorio.addView(pagina, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (52 * d).toInt(), Gravity.BOTTOM))
        libro.alCambiar = { i, total ->
            pagina.text = when (total) {
                0 -> ""
                1 -> "1 de 1"
                else -> "${i + 1} de $total · desliza para pasar la página"
            }
        }
        setContentView(escritorio)
    }

    override fun onStart() {
        super.onStart()
        val pareja = Libreta.pareja(this) ?: return finish()
        escucha = Album.escuchar(pareja) { lista ->
            val destino = irA
            libro.poner(lista, destino)
            if (destino != null && lista.any { it.id == destino }) {
                irA = null
                if (recien) { recien = false; libro.pegarActual() }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        escucha?.remove(); escucha = null
    }

    companion object {
        const val IR_A = "ir_a"
        const val RECIEN = "recien"
    }
}

/**
 * Las páginas del álbum. Arriba va la página que gira (sobre el lomo, a la izquierda);
 * abajo, la que queda al descubierto. Pasar hacia adelante: la de arriba se va de 0° a -180°.
 * Hacia atrás: la anterior vuelve de -180° a 0° encima de la actual.
 */
class LibroView(context: Context) : FrameLayout(context) {

    var alCambiar: ((Int, Int) -> Unit)? = null

    private val abajo = RecuerdoView(context)
    private val arriba = RecuerdoView(context)
    private var paginas: List<Recuerdo> = emptyList()
    private var actual = 0

    private val d = context.resources.displayMetrics.density
    private val giro = SpringAnimation(arriba, DynamicAnimation.ROTATION_Y, 0f).apply {
        spring = SpringForce(0f).setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY).setStiffness(220f)
        addUpdateListener { _, valor, _ -> pintarGiro(valor) }
        addEndListener { _, cancelada, _, _ -> if (!cancelada) alTerminar?.invoke().also { alTerminar = null } }
    }
    private var alTerminar: (() -> Unit)? = null

    init {
        addView(abajo, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(arriba, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        arriba.cameraDistance = 9000 * d
        elevation = 8 * d
        isClickable = true
        alpha = 0f // hasta que lleguen los recuerdos, para no mostrar «todavía no hay» de pasada
    }
    private var cargado = false

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        arriba.pivotX = 0f
        arriba.pivotY = h / 2f
    }

    /** Pone los recuerdos; se queda en la página que se estaba viendo, o salta a [ir]. */
    fun poner(lista: List<Recuerdo>, ir: String?) {
        val vista = paginas.getOrNull(actual)?.id
        paginas = lista
        actual = (lista.indexOfFirst { it.id == (ir ?: vista) }.takeIf { it >= 0 } ?: actual).coerceIn(0, maxOf(0, lista.size - 1))
        if (!giro.isRunning) quieta()
        alCambiar?.invoke(actual, paginas.size)
        if (!cargado) {
            cargado = true
            if (Papel.conMovimiento()) animate().alpha(1f).setDuration(150).start() else alpha = 1f
        }
    }

    fun pegarActual() = arriba.pegar()

    private fun mostrar(v: RecuerdoView, i: Int) {
        val r = paginas.getOrNull(i)
        v.mostrar(r, r?.let { Album.foto(it) })
    }

    private fun quieta() {
        mostrar(arriba, actual)
        mostrar(abajo, actual)
        arriba.rotationY = 0f
        pintarGiro(0f)
    }

    private fun pintarGiro(valor: Float) {
        // Pasados los 90° la página está de canto: se esconde y deja ver la de abajo.
        arriba.alpha = if (valor < -90f) 0f else 1f
        arriba.sombra = (abs(valor) / 90f).coerceIn(0f, 1f)
    }

    // --- El dedo ----------------------------------------------------------------------

    private val toque = ViewConfiguration.get(context).scaledTouchSlop
    private var x0 = 0f
    private var direccion = 0 // 1 adelante, -1 atrás
    private var puede = false
    private var velocidad: VelocityTracker? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (giro.isRunning) { giro.skipToEnd() }
                x0 = e.x; direccion = 0
                velocidad = VelocityTracker.obtain().also { it.addMovement(e) }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                velocidad?.addMovement(e)
                val dx = e.x - x0
                if (direccion == 0 && abs(dx) > toque) empezar(if (dx < 0) 1 else -1, e.x)
                if (direccion != 0) {
                    val p = (abs(e.x - x0) / width).coerceIn(0f, 1f)
                    // Sin página a donde ir, apenas se levanta la esquina.
                    val avance = if (puede) p else minOf(p, 0.06f)
                    arriba.rotationY = if (direccion == 1) -180f * avance else -180f * (1f - avance)
                    pintarGiro(arriba.rotationY)
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                velocidad?.computeCurrentVelocity(1000)
                val v = velocidad?.xVelocity ?: 0f
                velocidad?.recycle(); velocidad = null
                if (direccion == 0) {
                    if (e.actionMasked == MotionEvent.ACTION_UP) {
                        // Un toque: mitad derecha adelante, mitad izquierda atrás.
                        val dir = if (e.x > width / 2f) 1 else -1
                        empezar(dir, e.x)
                        soltar(cumplir = puede, vGrados = 0f)
                    }
                    return true
                }
                val p = abs(arriba.rotationY + if (direccion == 1) 0f else 180f) / 180f
                val rapido = abs(v) > 600 * d && (v < 0) == (direccion == 1)
                soltar(cumplir = puede && e.actionMasked == MotionEvent.ACTION_UP && (p > 0.35f || rapido), vGrados = v / width * 180f)
                return true
            }
        }
        return super.onTouchEvent(e)
    }

    private fun empezar(dir: Int, x: Float) {
        direccion = dir
        x0 = x
        parent?.requestDisallowInterceptTouchEvent(true)
        if (dir == 1) {
            puede = actual < paginas.size - 1
            mostrar(arriba, actual)
            mostrar(abajo, if (puede) actual + 1 else actual)
            arriba.rotationY = 0f
        } else {
            puede = actual > 0
            if (puede) {
                mostrar(arriba, actual - 1)
                mostrar(abajo, actual)
                arriba.rotationY = -180f
            } else {
                // Primera página: se levanta un poco y vuelve.
                direccion = 1
                mostrar(arriba, actual); mostrar(abajo, actual)
                arriba.rotationY = 0f
            }
        }
        pintarGiro(arriba.rotationY)
    }

    private fun soltar(cumplir: Boolean, vGrados: Float) {
        val dir = direccion
        direccion = 0
        if (!Papel.conMovimiento()) {
            if (cumplir) actual += dir
            quieta(); alCambiar?.invoke(actual, paginas.size); return
        }
        val destino = when {
            dir == 1 && cumplir -> -180f
            dir == 1 -> 0f
            cumplir -> 0f
            else -> -180f
        }
        alTerminar = {
            if (cumplir) {
                actual += dir
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            }
            quieta()
            alCambiar?.invoke(actual, paginas.size)
        }
        giro.setStartValue(arriba.rotationY)
        giro.setStartVelocity(vGrados)
        giro.animateToFinalPosition(destino)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        giro.cancel()
    }
}
