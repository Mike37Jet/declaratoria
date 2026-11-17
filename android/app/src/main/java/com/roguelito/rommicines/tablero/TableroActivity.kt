package com.roguelito.rommicines.tablero

import android.animation.AnimatorInflater
import android.app.Activity
import android.content.SharedPreferences
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.firebase.firestore.ListenerRegistration
import com.roguelito.rommicines.Fotos
import com.roguelito.rommicines.FotosCompartidas
import com.roguelito.rommicines.R
import com.roguelito.rommicines.libreta.Papel
import java.util.concurrent.Executors

/**
 * El tablero de corcho: se arrastran fotos y notitas, quedan un poco torcidas y se pegan donde se sueltan.
 * Con la libreta vinculada es el mismo en los dos celulares. Lo que se ve en el widget es esto mismo.
 */
class TableroActivity : Activity() {

    private lateinit var tablero: TableroView
    private lateinit var raiz: FrameLayout
    private var capa: View? = null
    private var escucha: ListenerRegistration? = null
    private var escuchaFotos: ListenerRegistration? = null
    private val trabajo = Executors.newSingleThreadExecutor()
    private val d by lazy { resources.displayMetrics.density }

    private val cambios = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> recargar() }

    private val cerrarCapa: Any? by lazy {
        if (Build.VERSION.SDK_INT >= 33) android.window.OnBackInvokedCallback { quitarCapa() } else null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        raiz = FrameLayout(this).apply { setBackgroundColor(getColor(R.color.corcho_pared)) }
        raiz.setOnApplyWindowInsetsListener { v, insets ->
            @Suppress("DEPRECATION")
            v.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            insets
        }
        val columna = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        // Arriba: volver y el título.
        val barra = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(5), dp(8), dp(13), 0)
        }
        barra.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_tablero_atras)
            setColorFilter(getColor(R.color.corcho_tinta))
            contentDescription = "Volver"
            scaleType = ImageView.ScaleType.CENTER
            background = getDrawable(android.R.drawable.list_selector_background)
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(55), dp(55)))
        barra.addView(texto("Nuestro tablero", 34f, R.color.corcho_tinta, negrita = true).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            isFocusable = true
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        columna.addView(barra)

        tablero = TableroView(this)
        tablero.alSoltar = { p -> aplicar(p) }
        tablero.alTocar = { p -> editar(p) }
        columna.addView(tablero, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply {
            setMargins(dp(13), dp(8), dp(13), 0)
        })

        // Abajo: clavar una foto o una notita.
        val botones = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(13), dp(13), dp(13), dp(13))
        }
        botones.addView(boton("Foto", R.drawable.ic_libreta_foto) { elegirFoto() },
            LinearLayout.LayoutParams(0, dp(55), 1f).apply { marginEnd = dp(4) })
        botones.addView(boton("Notita", R.drawable.ic_libreta_lapiz) { escribirNota(null) },
            LinearLayout.LayoutParams(0, dp(55), 1f).apply { marginStart = dp(4) })
        columna.addView(botones)

        raiz.addView(columna, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setContentView(raiz)
        trabajo.execute { Fotos.sembrar(applicationContext) }
    }

    override fun onStart() {
        super.onStart()
        getSharedPreferences("tablero", MODE_PRIVATE).registerOnSharedPreferenceChangeListener(cambios)
        getSharedPreferences("fotos", MODE_PRIVATE).registerOnSharedPreferenceChangeListener(cambios)
        escucha = Tablero.escuchar(this)
        // Las fotos del otro se bajan también aquí: una foto clavada por él no sale hasta tener la copia.
        escuchaFotos = FotosCompartidas.escuchar(this)
        recargar()
    }

    override fun onStop() {
        getSharedPreferences("tablero", MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(cambios)
        getSharedPreferences("fotos", MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(cambios)
        escucha?.remove(); escucha = null
        escuchaFotos?.remove(); escuchaFotos = null
        super.onStop()
    }

    private fun recargar() {
        val e = Tablero.estado(this)
        tablero.mostrar(e.piezas, e.automatico)
    }

    /**
     * Guarda un cambio. Si el tablero todavía se armaba solo, el primer cambio guarda el acomodo entero
     * (tal como se ve en esta pantalla) para que no se reacomode después.
     */
    private fun aplicar(cambio: Pieza?, quitar: String? = null) {
        if (tablero.automatico) {
            val actuales = tablero.todas()
            val todas = actuales.filter { it.id != quitar }.map { if (it.id == cambio?.id) cambio else it } +
                listOfNotNull(cambio?.takeIf { c -> actuales.none { it.id == c.id } })
            Tablero.fijar(this, todas)
        } else {
            cambio?.let { Tablero.guardar(this, it) }
            quitar?.let { Tablero.quitar(this, it) }
        }
    }

    private fun clavar(p: Pieza) {
        aplicar(p)
        recargar()
        tablero.post { tablero.dejarCaer(p.id) }
    }

    // ---- La notita para escribir ----

    private fun editar(p: Pieza) = if (p.esFoto) escribirPie(p) else escribirNota(p)

    private fun escribirNota(p: Pieza?) {
        var color = p?.color ?: (0..2).random()
        val tarjeta = tarjeta(DibujoTablero.NOTAS[color])
        tarjeta.addView(texto(if (p == null) "Una notita nueva" else "Esta notita", 26f, R.color.corcho_tinta, negrita = true))
        val campo = campo(p?.texto ?: "", "Algo bonito para el corcho")
        tarjeta.addView(campo)

        // Los tres colores de papel.
        val colores = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val muestras = DibujoTablero.NOTAS.indices.map { i ->
            View(this).apply {
                contentDescription = "Papel ${DibujoTablero.NOMBRES_NOTAS[i]}"
                isClickable = true
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(DibujoTablero.NOTAS[i]) }
            }.also { colores.addView(it, LinearLayout.LayoutParams(dp(34), dp(34)).apply { marginEnd = dp(13) }) }
        }
        fun marcar() = muestras.forEachIndexed { i, v ->
            (v.background as GradientDrawable).setStroke(if (i == color) dp(3) else dp(1), getColor(if (i == color) R.color.corcho_tinta else R.color.corcho_tinta_suave))
            v.isSelected = i == color
            (tarjeta.background as GradientDrawable).setColor(DibujoTablero.NOTAS[color])
        }
        muestras.forEachIndexed { i, v -> v.setOnClickListener { color = i; marcar() } }
        marcar()
        tarjeta.addView(colores, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)).apply { topMargin = dp(8) })

        tarjeta.addView(acciones(
            quitar = p?.let { { aplicar(null, quitar = it.id); quitarCapa() } },
            listo = if (p == null) "Clavar" else "Guardar",
        ) {
            val t = campo.text.toString().trim()
            if (t.isEmpty()) { campo.error = "Escribe algo primero"; return@acciones }
            if (p == null) clavar(Tablero.nueva(Tablero.NOTA, texto = t, color = color))
            else aplicar(p.copy(texto = t.take(Tablero.LARGO_TEXTO), color = color))
            quitarCapa()
        })
        mostrarCapa(tarjeta, campo)
    }

    private fun escribirPie(p: Pieza) {
        val tarjeta = tarjeta(getColor(R.color.polaroid))
        tarjeta.addView(texto("Al pie de la foto", 26f, R.color.corcho_tinta, negrita = true))
        val campo = campo(p.texto, "Dónde, cuándo… (si quieren)")
        campo.maxLines = 1
        campo.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        campo.filters = arrayOf(InputFilter.LengthFilter(28))
        tarjeta.addView(campo)
        tarjeta.addView(acciones(quitar = { aplicar(null, quitar = p.id); quitarCapa() }, listo = "Guardar") {
            aplicar(p.copy(texto = campo.text.toString().trim()))
            quitarCapa()
        })
        mostrarCapa(tarjeta, campo)
    }

    // ---- Elegir una foto de «Nuestros recuerdos» ----

    private fun elegirFoto() {
        val clavadas = tablero.todas().mapNotNull { it.foto }.toSet()
        val libres = Fotos.lista(this).filter { it.name !in clavadas }.asReversed()
        val tarjeta = tarjeta(getColor(R.color.polaroid))
        tarjeta.addView(texto("Elige una foto", 26f, R.color.corcho_tinta, negrita = true))
        if (libres.isEmpty()) {
            tarjeta.addView(texto(
                if (Fotos.lista(this).isEmpty()) "Todavía no hay fotos. Agrégalas en «Nuestros recuerdos», en la pantalla principal."
                else "Todas sus fotos ya están en el tablero. Agrega más en «Nuestros recuerdos».",
                21f, R.color.corcho_tinta_suave).apply { setPadding(0, dp(8), 0, dp(8)) })
            tarjeta.addView(acciones(listo = "Entendido", cancelar = null) { quitarCapa() })
            mostrarCapa(tarjeta, null)
            return
        }
        val fila = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        libres.forEachIndexed { i, archivo ->
            val miniatura = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(getColor(R.color.corcho_canto))
                contentDescription = "Foto ${i + 1} de ${libres.size}. Clavarla en el tablero"
                setPadding(dp(4), dp(4), dp(4), dp(13))
                background = GradientDrawable().apply { setColor(getColor(R.color.polaroid)); setStroke(dp(1), getColor(R.color.corcho_canto)) }
                setOnClickListener {
                    quitarCapa()
                    clavar(Tablero.nueva(Tablero.FOTO, foto = archivo.name))
                }
            }
            fila.addView(miniatura, LinearLayout.LayoutParams(dp(89), dp(98)).apply { marginEnd = dp(8) })
            trabajo.execute {
                val b = Fotos.reducida(archivo, dp(89))
                runOnUiThread { if (b != null) miniatura.setImageBitmap(b) }
            }
        }
        tarjeta.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            clipToPadding = false
            addView(fila)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(13) })
        tarjeta.addView(acciones(listo = null) { })
        mostrarCapa(tarjeta, null)
    }

    // ---- La capa encima del tablero ----

    private fun mostrarCapa(tarjeta: View, foco: EditText?) {
        quitarCapa()
        val fondo = FrameLayout(this).apply {
            setBackgroundColor(0x73000000)
            isClickable = true
            setOnClickListener { quitarCapa() }
            addView(tarjeta, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER).apply {
                setMargins(dp(21), dp(21), dp(21), dp(21))
            })
        }
        raiz.addView(fondo, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        capa = fondo
        if (Build.VERSION.SDK_INT >= 33) onBackInvokedDispatcher.registerOnBackInvokedCallback(
            android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, cerrarCapa as android.window.OnBackInvokedCallback)
        if (Papel.conMovimiento()) {
            fondo.alpha = 0f
            fondo.animate().alpha(1f).setDuration(200).setInterpolator(Papel.salida).start()
            tarjeta.translationY = dp(34).toFloat()
            tarjeta.animate().translationY(0f).setDuration(260).setInterpolator(Papel.salida).start()
        }
        foco?.let {
            it.requestFocus()
            it.setSelection(it.text.length)
            it.post { getSystemService(InputMethodManager::class.java).showSoftInput(it, InputMethodManager.SHOW_IMPLICIT) }
        }
    }

    private fun quitarCapa() {
        val c = capa ?: return
        capa = null
        getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(c.windowToken, 0)
        if (Build.VERSION.SDK_INT >= 33) onBackInvokedDispatcher.unregisterOnBackInvokedCallback(cerrarCapa as android.window.OnBackInvokedCallback)
        if (Papel.conMovimiento()) c.animate().alpha(0f).setDuration(150).withEndAction { raiz.removeView(c) }.start()
        else raiz.removeView(c)
    }

    @Deprecated("Antes de Android 13")
    override fun onBackPressed() {
        if (capa != null) quitarCapa() else @Suppress("DEPRECATION") super.onBackPressed()
    }

    // ---- Piezas de la interfaz ----

    private fun dp(n: Int) = (n * d).toInt()

    /** Un papel encima del tablero, un poquito torcido, como todo lo demás. */
    private fun tarjeta(color: Int) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable().apply { setColor(color); cornerRadius = 2 * d }
        elevation = dp(10).toFloat()
        setPadding(dp(21), dp(21), dp(21), dp(8))
        rotation = -1.2f
        isClickable = true
    }

    private fun texto(s: String, sp: Float, color: Int, negrita: Boolean = false) = TextView(this).apply {
        text = s
        typeface = if (negrita) Papel.letraNegrita(this@TableroActivity) else Papel.letra(this@TableroActivity)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(getColor(color))
        includeFontPadding = false
    }

    private fun campo(inicial: String, pista: String) = EditText(this).apply {
        setText(inicial)
        hint = pista
        typeface = Papel.letra(this@TableroActivity)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
        setTextColor(getColor(R.color.corcho_tinta))
        setHintTextColor(getColor(R.color.corcho_tinta_suave))
        background = null
        setPadding(0, dp(13), 0, dp(8))
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        imeOptions = EditorInfo.IME_ACTION_DONE
        setRawInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
        maxLines = 3
        filters = arrayOf(InputFilter.LengthFilter(Tablero.LARGO_TEXTO))
        isSingleLine = false
    }

    /** La fila de abajo de una notita: «Quitar del tablero» a la izquierda; «Cancelar» y la acción a la derecha. */
    private fun acciones(quitar: (() -> Unit)? = null, listo: String? = "Clavar", cancelar: String? = "Cancelar", accion: () -> Unit): View {
        val fila = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        fun enlace(s: String, negrita: Boolean, alTocar: () -> Unit) = texto(s, 21f, if (negrita) R.color.corcho_tinta else R.color.corcho_tinta_suave, negrita).apply {
            gravity = Gravity.CENTER
            minHeight = dp(48)
            setPadding(dp(8), 0, dp(8), 0)
            if (negrita) paintFlags = paintFlags or Paint.UNDERLINE_TEXT_FLAG
            isClickable = true
            background = getDrawable(android.R.drawable.list_selector_background)
            setOnClickListener { alTocar() }
        }
        if (quitar != null) fila.addView(enlace("Quitar del tablero", false, quitar).apply { setPadding(0, 0, dp(8), 0) })
        fila.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        if (cancelar != null) fila.addView(enlace(cancelar, false) { quitarCapa() })
        if (listo != null) fila.addView(enlace(listo, true, accion))
        return fila.also { it.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) } }
    }

    /** Un botón de cartulina con su ícono de trazo. */
    private fun boton(s: String, icono: Int, accion: () -> Unit) = TextView(this).apply {
        val lado = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 21f, resources.displayMetrics).toInt()
        val trazo = getDrawable(icono)!!.mutate().apply { setTint(getColor(R.color.corcho_tinta)); setBounds(0, 0, lado, lado) }
        text = android.text.SpannableStringBuilder()
            .append("·", android.text.style.ImageSpan(trazo, android.text.style.DynamicDrawableSpan.ALIGN_BASELINE), 0)
            .append("  ").append(s)
        contentDescription = if (s == "Foto") "Clavar una foto" else "Escribir una notita"
        typeface = Papel.letraNegrita(this@TableroActivity)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 21f)
        setTextColor(getColor(R.color.corcho_tinta))
        gravity = Gravity.CENTER
        background = GradientDrawable().apply {
            setColor(getColor(R.color.polaroid)); cornerRadius = 8 * d; setStroke(dp(1), getColor(R.color.corcho_canto))
        }
        elevation = dp(2).toFloat()
        stateListAnimator = AnimatorInflater.loadStateListAnimator(this@TableroActivity, R.animator.presionar)
        isClickable = true
        setOnClickListener { accion() }
    }
}
