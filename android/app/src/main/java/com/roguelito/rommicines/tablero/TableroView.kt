package com.roguelito.rommicines.tablero

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.OvershootInterpolator
import com.roguelito.rommicines.Fotos
import com.roguelito.rommicines.libreta.Papel
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.random.Random

/**
 * El tablero de corcho en la app: dibuja lo mismo que el widget y deja arrastrar cada pieza.
 *
 * Al tocar una pieza se despega (sale la chincheta, crece un poco y su sombra se aleja); mientras se arrastra
 * sigue al dedo desde donde se agarró y se inclina según la velocidad; al soltarla se asienta con un giro
 * nuevo y la chincheta se vuelve a clavar. Tocar sin arrastrar la abre ([alTocar]).
 */
class TableroView(context: Context) : View(context) {

    /** Una pieza se soltó en otro sitio: hay que guardarla. */
    var alSoltar: ((Pieza) -> Unit)? = null
    /** Se tocó una pieza sin moverla. */
    var alTocar: ((Pieza) -> Unit)? = null

    var automatico = false; private set
    private var crudas: List<Pieza> = emptyList()
    private var piezas: List<Pieza> = emptyList()

    private val d = resources.displayMetrics.density
    private var lado = 0f
    private var fondo: Bitmap? = null
    private val principal = Handler(Looper.getMainLooper())

    // Las fotos, reducidas a la medida de la polaroid.
    private val fotos = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val cargando = mutableSetOf<String>()
    private val carga = Executors.newSingleThreadExecutor()

    // El arrastre.
    private var arrastrada: String? = null
    private var cx = 0f; private var cy = 0f        // centro actual, en px
    private var agarreX = 0f; private var agarreY = 0f  // dónde se agarró, respecto del centro
    private var inicioX = 0f; private var inicioY = 0f
    private var ultimoX = 0f; private var ultimoT = 0L
    private var giroBase = 0f; private var giro = 0f
    private var escala = 1f; private var alzada = 0f
    private var movio = false
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var animacion: ValueAnimator? = null
    /** La escala de cada chincheta (0 mientras la pieza está despegada; vuelve con un saltito). */
    private val chinchetas = mutableMapOf<String, Float>()

    init {
        isFocusable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    /** Las piezas a mostrar. Si el tablero es [auto]mático, aquí se reparten según la forma de la pantalla. */
    fun mostrar(lista: List<Pieza>, auto: Boolean) {
        crudas = lista; automatico = auto
        recomponer()
    }

    /** Todas las piezas con la posición que tienen ahora (para guardar el acomodo automático). */
    fun todas(): List<Pieza> = piezas

    /** Una pieza recién agregada cae al corcho: se ve despegada y se asienta. */
    fun dejarCaer(id: String) {
        val p = piezas.find { it.id == id } ?: return
        if (!Papel.conMovimiento()) return
        chinchetas[id] = 0f
        val c = DibujoTablero.centro(p, width.toFloat(), height.toFloat(), lado, d)
        arrastrada = id; cx = c.x; cy = c.y - 21 * d; giro = p.giro * 2.5f; escala = 1.12f; alzada = 1f
        asentar(p, c.x, c.y, p.giro, guardar = false)
    }

    private fun recomponer() {
        if (width == 0 || height == 0) return
        val base = if (automatico) DibujoTablero.repartir(crudas, width.toFloat() / height) else crudas
        // La que se está arrastrando no salta aunque llegue un cambio del otro celular.
        val viva = arrastrada?.let { id -> piezas.find { it.id == id } }
        piezas = base.map { if (it.id == viva?.id) viva else it }.sortedBy { it.orden }
        contentDescription = DibujoTablero.descripcion(context).removeSuffix(". Toca para abrirlo")
        cargarFotos()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        lado = DibujoTablero.lado(w / d, h / d, fraccion = 0.3f, maximo = 144f) * d
        fondo = Bitmap.createBitmap(w.coerceAtLeast(1), h.coerceAtLeast(1), Bitmap.Config.ARGB_8888).also {
            DibujoTablero.corcho(Canvas(it), w.toFloat(), h.toFloat(), d, 21 * d)
        }
        fotos.evictAll()
        recomponer()
    }

    private fun cargarFotos() {
        val ancho = lado.toInt().coerceAtLeast(1)
        piezas.filter { it.esFoto }.forEach { p ->
            val nombre = p.foto ?: return@forEach
            if (fotos.get(nombre) != null || !cargando.add(nombre)) return@forEach
            val archivo = Tablero.archivo(context, p) ?: return@forEach
            carga.execute {
                val b = Fotos.reducida(archivo, ancho)
                principal.post {
                    cargando.remove(nombre)
                    if (b != null) { fotos.put(nombre, b); invalidate() }
                }
            }
        }
    }

    override fun onDraw(c: Canvas) {
        fondo?.let { c.drawBitmap(it, 0f, 0f, null) }
        if (piezas.isEmpty()) {
            DibujoTablero.pista(context, c, width.toFloat(), height.toFloat(), d, "Clava aquí sus fotos\ny notitas")
            return
        }
        piezas.forEach { p ->
            val foto = p.foto?.let { fotos.get(it) }
            if (p.id == arrastrada) {
                DibujoTablero.pieza(context, c, p, cx, cy, giro, lado, d, foto, escala, alzada, chinchetas[p.id] ?: 1f)
            } else {
                val ctr = DibujoTablero.centro(p, width.toFloat(), height.toFloat(), lado, d)
                DibujoTablero.pieza(context, c, p, ctr.x, ctr.y, p.giro, lado, d, foto, chincheta = chinchetas[p.id] ?: 1f)
            }
        }
    }

    // ---- Tocar y arrastrar ----

    /** La pieza de más arriba bajo el dedo (se mira en su sistema girado). */
    private fun bajo(x: Float, y: Float): Pieza? {
        val m = Matrix(); val punto = FloatArray(2)
        return piezas.asReversed().firstOrNull { p ->
            val ctr = DibujoTablero.centro(p, width.toFloat(), height.toFloat(), lado, d)
            val med = DibujoTablero.medida(p, lado)
            m.reset(); m.setRotate(-p.giro)
            punto[0] = x - ctr.x; punto[1] = y - ctr.y
            m.mapPoints(punto)
            val holgura = 8 * d
            abs(punto[0]) <= med.width / 2 + holgura && abs(punto[1]) <= med.height / 2 + holgura
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (arrastrada != null) animacion?.end()
                val p = bajo(e.x, e.y) ?: return false
                parent?.requestDisallowInterceptTouchEvent(true)
                val ctr = DibujoTablero.centro(p, width.toFloat(), height.toFloat(), lado, d)
                arrastrada = p.id; cx = ctr.x; cy = ctr.y
                agarreX = e.x - cx; agarreY = e.y - cy
                inicioX = e.x; inicioY = e.y; ultimoX = e.x; ultimoT = e.eventTime
                giroBase = p.giro; giro = p.giro; movio = false
                // Pasa encima de todas en cuanto se toca.
                piezas = piezas.map { if (it.id == p.id) it.copy(orden = System.currentTimeMillis()) else it }.sortedBy { it.orden }
                despegar()
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (arrastrada == null) return false
                if (!movio && hypot(e.x - inicioX, e.y - inicioY) > slop) movio = true
                if (!movio) return true
                cx = (e.x - agarreX).coerceIn(0f, width.toFloat())
                cy = (e.y - agarreY).coerceIn(0f, height.toFloat())
                // Se inclina hacia donde va, como un papel que se lleva de una esquina.
                val dt = (e.eventTime - ultimoT).coerceAtLeast(1)
                val velocidad = (e.x - ultimoX) / dt * 1000f / d // dp/s
                ultimoX = e.x; ultimoT = e.eventTime
                val objetivo = giroBase + (velocidad * 0.012f).coerceIn(-10f, 10f)
                giro += (objetivo - giro) * 0.25f
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val id = arrastrada ?: return false
                val p = piezas.find { it.id == id } ?: return false
                if (!movio) {
                    // Un toque: vuelve a su sitio tal cual y se abre.
                    asentar(p, cx, cy, giroBase, guardar = false)
                    if (e.actionMasked == MotionEvent.ACTION_UP) { performClick(); alTocar?.invoke(p) }
                } else {
                    asentar(p, cx, cy, Tablero.giroDe(Random(System.nanoTime())), guardar = true)
                }
                return true
            }
        }
        return super.onTouchEvent(e)
    }

    override fun performClick(): Boolean = super.performClick()

    private fun despegar() {
        val id = arrastrada ?: return
        animacion?.cancel()
        if (!Papel.conMovimiento()) { escala = 1.04f; alzada = 1f; chinchetas[id] = 0f; invalidate(); return }
        val desdeEscala = escala; val desdeAlzada = alzada; val desdeClavo = chinchetas[id] ?: 1f
        animacion = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 160; interpolator = Papel.salida
            addUpdateListener {
                val t = it.animatedFraction
                escala = desdeEscala + (1.06f - desdeEscala) * t
                alzada = desdeAlzada + (1f - desdeAlzada) * t
                chinchetas[id] = desdeClavo * (1 - t).coerceAtLeast(0f) * (1 - t)
                invalidate()
            }
            start()
        }
    }

    /** Se pega al corcho en ([x], [y]) con el giro [nuevoGiro]; la chincheta vuelve con un saltito. */
    private fun asentar(p: Pieza, x: Float, y: Float, nuevoGiro: Float, guardar: Boolean) {
        animacion?.cancel()
        val id = p.id
        val w = width.toFloat(); val h = height.toFloat()
        val final = p.copy(
            x = if (guardar) (x / w).coerceIn(0f, 1f) else p.x,
            y = if (guardar) (y / h).coerceIn(0f, 1f) else p.y,
            giro = nuevoGiro,
            orden = if (guardar) System.currentTimeMillis() else p.orden,
        )
        // Dónde queda de verdad (el centro se ajusta para que no se salga del corcho).
        val destino = DibujoTablero.centro(final, w, h, lado, d)
        val terminar = {
            piezas = piezas.map { if (it.id == id) final else it }.sortedBy { it.orden }
            arrastrada = null; escala = 1f; alzada = 0f
            invalidate()
            if (guardar) {
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                alSoltar?.invoke(final)
            }
        }
        if (!Papel.conMovimiento()) { chinchetas.remove(id); terminar(); return }

        val desdeX = cx; val desdeY = cy; val desdeGiro = giro; val desdeEscala = escala; val desdeAlzada = alzada
        val rebote = OvershootInterpolator(1.4f)
        animacion = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 260; interpolator = Papel.salida
            addUpdateListener {
                val t = it.animatedFraction
                cx = desdeX + (destino.x - desdeX) * t
                cy = desdeY + (destino.y - desdeY) * t
                giro = desdeGiro + (nuevoGiro - desdeGiro) * t
                escala = desdeEscala + (1f - desdeEscala) * t
                alzada = desdeAlzada * (1 - t)
                // La chincheta entra en el último tramo, cuando el papel ya tocó el corcho.
                val clavo = ((t - 0.45f) / 0.55f).coerceIn(0f, 1f)
                chinchetas[id] = rebote.getInterpolation(clavo)
                invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                private var cancelada = false
                override fun onAnimationCancel(animation: android.animation.Animator) { cancelada = true }
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    chinchetas.remove(id)
                    // Si otro toque la interrumpió, ese toque sigue desde aquí; igual se guarda dónde quedó.
                    if (!cancelada) terminar() else if (guardar) alSoltar?.invoke(final.copy(x = (cx / w).coerceIn(0f, 1f), y = (cy / h).coerceIn(0f, 1f)))
                }
            })
            start()
        }
    }
}
