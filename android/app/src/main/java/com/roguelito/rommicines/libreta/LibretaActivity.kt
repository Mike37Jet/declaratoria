package com.roguelito.rommicines.libreta

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.firebase.firestore.ListenerRegistration
import com.roguelito.rommicines.R
import com.roguelito.rommicines.Toques

/**
 * La libreta compartida: una hoja de cuaderno sobre un escritorio, cerrada con su tapa.
 * Lo que escribe uno le aparece al otro al momento (Firestore), con la tinta de quien lo escribió.
 */
class LibretaActivity : Activity() {

    private lateinit var escritorio: FrameLayout
    private var tapa: TapaView? = null
    private var planes: LinearLayout? = null
    private var contador: ContadorView? = null
    private var cuenta: TextView? = null
    private var estado: TextView? = null
    private var hoja: ScrollView? = null
    private val vistas = mutableMapOf<String, PlanView>()
    private val porEscribir = mutableListOf<PlanView>()
    private var hechos = -1

    private var escuchaPlanes: ListenerRegistration? = null
    private var escuchaMiembros: ListenerRegistration? = null

    /** Con la tapa abierta, «atrás» la cierra en vez de salir (Android 13+; antes, onBackPressed). */
    private val cerrarTapa: Any? by lazy {
        if (android.os.Build.VERSION.SDK_INT >= 33) android.window.OnBackInvokedCallback { tapa?.cerrar() } else null
    }

    private fun atrasCierraTapa(si: Boolean) {
        if (android.os.Build.VERSION.SDK_INT < 33) return
        val callback = cerrarTapa as android.window.OnBackInvokedCallback
        if (si) onBackInvokedDispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback)
        else onBackInvokedDispatcher.unregisterOnBackInvokedCallback(callback)
    }

    private val linea by lazy { Papel.linea(this) }
    private val margen by lazy { Papel.margen(this).toInt() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        escritorio = FrameLayout(this).apply { setBackgroundColor(getColor(R.color.escritorio)) }
        escritorio.setOnApplyWindowInsetsListener { v, insets ->
            @Suppress("DEPRECATION")
            v.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            insets
        }
        setContentView(escritorio)
        mostrar()
    }

    private fun mostrar() {
        escritorio.removeAllViews()
        atrasCierraTapa(false)
        tapa = null; planes = null; contador = null; cuenta = null; estado = null; hoja = null
        vistas.clear(); porEscribir.clear(); hechos = -1
        when {
            Toques.yo(this) == null -> tarjeta(
                "Primero dime quién eres",
                "Elige tu nombre en la pantalla principal; así la libreta sabe con qué tinta escribes.",
                listOf("Volver" to { finish() }),
            )
            Libreta.pareja(this) == null -> vincular()
            else -> libreta()
        }
    }

    override fun onStart() {
        super.onStart()
        escuchar()
    }

    override fun onStop() {
        super.onStop()
        escuchaPlanes?.remove(); escuchaPlanes = null
        escuchaMiembros?.remove(); escuchaMiembros = null
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val t = tapa
        if (t != null && t.abierta) t.cerrar() else @Suppress("DEPRECATION") super.onBackPressed()
    }

    // --- Vincular los dos celulares ---------------------------------------------------

    private fun vincular() {
        val otro = Toques.otro(Toques.yo(this))
        tarjeta(
            "Nuestra libreta",
            "Una libreta para los dos: lo que escribe uno le aparece al otro, con su tinta.\n\nUno la crea y le pasa el código al otro.",
            listOf(
                "Crear nuestra libreta" to { crear(otro) },
                "Tengo un código" to { pedirCodigo() },
            ),
        )
    }

    private fun crear(otro: String) {
        mensaje("Abriendo una libreta nueva…")
        Libreta.crear(this, listo = { codigo ->
            tarjeta(
                "Pásale este código a $otro",
                Codigos.mostrar(codigo),
                listOf("Abrir la libreta" to { mostrar(); escuchar() }),
                grande = true,
                copiar = Codigos.mostrar(codigo),
                pie = "En cuanto $otro lo escriba en su celular, la libreta será de los dos.",
            )
            escuchaMiembros?.remove()
            escuchaMiembros = Libreta.escucharMiembros(Libreta.pareja(this)!!) { n ->
                if (n >= 2 && tapa == null) { mostrar(); escuchar() }
            }
        }, fallo = ::mensaje)
    }

    private fun pedirCodigo() {
        val campo = EditText(this).apply {
            hint = "ABC-DEF"
            typeface = Papel.letraNegrita(this@LibretaActivity)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
            gravity = Gravity.CENTER
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            filters = arrayOf(InputFilter.LengthFilter(8), InputFilter.AllCaps())
            setTextColor(getColor(R.color.tinta_lapiz))
        }
        AlertDialog.Builder(this)
            .setTitle("El código de la libreta")
            .setView(FrameLayout(this).apply { setPadding(dp(24), dp(8), dp(24), 0); addView(campo) })
            .setPositiveButton("Unirme") { _, _ ->
                val codigo = Codigos.normalizar(campo.text.toString())
                if (codigo == null) mensaje("Son 6 letras o números, como K7P-QX2.")
                else {
                    mensaje("Uniéndome…")
                    Libreta.unirse(this, codigo, listo = { mostrar(); escuchar() }, fallo = ::mensaje)
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
        campo.requestFocus()
    }

    /** Una hoja suelta en medio del escritorio, con un texto y botones de papel. */
    private fun tarjeta(
        titulo: String,
        texto: String,
        botones: List<Pair<String, () -> Unit>>,
        grande: Boolean = false,
        pie: String? = null,
        copiar: String? = null,
    ) {
        escritorio.removeAllViews()
        val tarjeta = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = HojaDrawable(this@LibretaActivity)
            elevation = dp(6).toFloat()
            setPadding(margen + dp(4), linea.toInt(), dp(20), dp(20))
        }
        tarjeta.addView(letra(titulo, 28f, R.color.tinta_lapiz, negrita = true))
        tarjeta.addView(letra(texto, if (grande) 44f else 21f, if (grande) R.color.tinta_azul else R.color.tinta_suave, negrita = grande).apply {
            if (grande) { letterSpacing = 0.08f; gravity = Gravity.CENTER_HORIZONTAL; setTextIsSelectable(true) }
            (layoutParams as? ViewGroup.MarginLayoutParams)?.topMargin = dp(8)
        })
        copiar?.let { tarjeta.addView(botonCopiar(it)) }
        pie?.let { tarjeta.addView(letra(it, 18f, R.color.tinta_suave)) }
        botones.forEach { (texto, accion) -> tarjeta.addView(boton(texto, accion)) }
        estado = letra("", 17f, R.color.sello).apply { visibility = View.GONE }
        tarjeta.addView(estado)
        escritorio.addView(tarjeta, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER).apply {
            setMargins(dp(20), dp(20), dp(20), dp(20))
        })
        tarjeta.rotation = -0.8f
    }

    // --- La libreta abierta -----------------------------------------------------------

    private fun libreta() {
        val pagina = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = HojaDrawable(this@LibretaActivity)
            setPadding(0, 0, 0, (linea * 3).toInt())
            clipChildren = false
        }
        // Encabezado: dos renglones, el título escrito sobre el segundo.
        pagina.addView(letra("Nuestras aventuras", 30f, R.color.tinta_lapiz, negrita = true).apply {
            gravity = Gravity.BOTTOM
            setPadding(margen, 0, dp(20), dp(5))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (linea * 2).toInt()))

        val fila = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.BOTTOM }
        fila.addView(letra("Contador de aventuras", 20f, R.color.tinta_suave).apply { setPadding(margen, 0, 0, dp(7)) },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        cuenta = letra("0 de ${Libreta.META}", 22f, R.color.tinta_lapiz, negrita = true).apply {
            gravity = Gravity.BOTTOM; setPadding(0, 0, dp(20), dp(6))
        }
        fila.addView(cuenta, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))
        pagina.addView(fila, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, linea.toInt()))

        contador = ContadorView(this)
        pagina.addView(contador)
        pagina.addView(View(this), LinearLayout.LayoutParams(1, linea.toInt()))

        // Sin recorte: el sello cae desde más grande y sobresale un instante del renglón.
        planes = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; clipChildren = false }
        pagina.addView(planes)
        pagina.addView(escribir(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, linea.toInt()))
        estado = letra("", 18f, R.color.tinta_suave).apply { setPadding(margen, dp(4), dp(20), 0); visibility = View.GONE }
        pagina.addView(estado)

        hoja = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(getColor(R.color.hoja))
            isVerticalScrollBarEnabled = false
            addView(pagina)
        }
        val marco = FrameLayout(this).apply { elevation = dp(8).toFloat(); clipChildren = false }
        marco.addView(hoja)
        val t = TapaView(this).apply {
            firma = "${Toques.ROGUELITO} & ${Toques.MIMINA}"
            alCambiar = { abierta -> atrasCierraTapa(abierta); if (abierta) escribirPendientes() }
        }
        tapa = t
        marco.addView(t)
        escritorio.addView(marco, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT).apply {
            setMargins(dp(12), dp(12), dp(12), dp(12))
        })
    }

    /** El renglón en blanco al final: se escribe con el teclado, pero con tu tinta. */
    private fun escribir(): EditText = EditText(this).apply {
        val tinta = getColor(Libreta.tinta(this@LibretaActivity).color)
        background = null
        typeface = Papel.letra(this@LibretaActivity)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, Papel.LETRA_SP)
        setTextColor(tinta)
        setHintTextColor(getColor(R.color.tinta_guia))
        hint = "+ escribir un plan…"
        includeFontPadding = false
        isSingleLine = true
        imeOptions = EditorInfo.IME_ACTION_DONE
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        filters = arrayOf(InputFilter.LengthFilter(Libreta.MAX_TEXTO))
        gravity = Gravity.BOTTOM or Gravity.START
        // Que la letra caiga sobre la raya igual que los planes ya escritos.
        val descenso = paint.fontMetrics.descent
        setPadding(margen, 0, dp(20), (dp(8) - descenso).toInt().coerceAtLeast(0))
        setOnEditorActionListener { campo, accion, _ ->
            if (accion != EditorInfo.IME_ACTION_DONE) return@setOnEditorActionListener false
            val texto = campo.text.toString().trim()
            if (texto.isNotEmpty()) {
                Libreta.agregar(this@LibretaActivity, texto)
                campo.text = null
                hoja?.post { hoja?.fullScroll(View.FOCUS_DOWN); campo.requestFocus() }
            }
            true
        }
    }

    private fun escuchar() {
        val pareja = Libreta.pareja(this) ?: return
        if (planes == null) return
        escuchaPlanes?.remove()
        escuchaPlanes = Libreta.escucharPlanes(pareja, ::pintar) { mensaje(it) }
        escuchaMiembros?.remove()
        escuchaMiembros = Libreta.escucharMiembros(pareja) { n ->
            val codigo = Libreta.codigo(this)
            if (n < 2 && codigo != null) mensaje("Falta ${Toques.otro(Toques.yo(this))}: su código es ${Codigos.mostrar(codigo)}", error = false)
            else if (estado?.text?.startsWith("Falta") == true) mensaje("")
        }
    }

    private fun pintar(lista: List<Plan>) {
        val contenedor = planes ?: return
        val abierta = tapa?.abierta == true
        val yo = Libreta.yo()
        val ids = lista.map { it.id }.toSet()
        vistas.keys.filter { it !in ids }.forEach { id -> vistas.remove(id)?.let { contenedor.removeView(it); porEscribir.remove(it) } }

        lista.forEachIndexed { i, plan ->
            val vista = vistas[plan.id]
            if (vista == null) {
                val nueva = PlanView(this, plan).apply { alTachar = ::tachar }
                vistas[plan.id] = nueva
                contenedor.addView(nueva, i)
                if (plan.autor != yo && !Libreta.visto(this, plan.id)) {
                    if (abierta) { nueva.escribirFrenteATi(); Libreta.marcarVisto(this, plan.id) }
                    else { porEscribir += nueva; nueva.alpha = 0f }
                } else {
                    Libreta.marcarVisto(this, plan.id)
                }
            } else {
                if (contenedor.indexOfChild(vista) != i) { contenedor.removeView(vista); contenedor.addView(vista, i) }
                vista.actualizar(plan, animar = abierta)
            }
        }

        val n = lista.count { it.hecho != null }
        contador?.mostrar(n, animar = abierta && hechos >= 0 && n == hechos + 1)
        cuenta?.text = "$n de ${Libreta.META}"
        hechos = n
    }

    /** Al abrir la tapa, los planes nuevos del otro se escriben uno tras otro. */
    private fun escribirPendientes() {
        porEscribir.forEachIndexed { i, vista ->
            vista.postDelayed({ vista.alpha = 1f; vista.escribirFrenteATi() }, 140L + i * 260L)
            Libreta.marcarVisto(this, vista.plan.id)
        }
        porEscribir.clear()
    }

    private fun tachar(plan: Plan, hecho: Boolean) {
        if (hecho) { Libreta.tachar(this, plan, true); return }
        AlertDialog.Builder(this)
            .setTitle("¿Quitar el tachado?")
            .setMessage("«${plan.texto}» volverá a quedar pendiente.")
            .setPositiveButton("Quitar") { _, _ -> Libreta.tachar(this, plan, false) }
            .setNegativeButton("Dejarlo", null)
            .show()
    }

    // --- Piezas -----------------------------------------------------------------------

    private fun mensaje(texto: String, error: Boolean = true) {
        estado?.apply {
            text = texto
            setTextColor(getColor(if (error) R.color.sello else R.color.tinta_suave))
            visibility = if (texto.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    private fun letra(texto: String, sp: Float, color: Int, negrita: Boolean = false) = TextView(this).apply {
        text = texto
        typeface = if (negrita) Papel.letraNegrita(this@LibretaActivity) else Papel.letra(this@LibretaActivity)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(getColor(color))
        includeFontPadding = false
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    /** Un botón como etiqueta de tela, del mismo verde que la tapa. */
    private fun boton(texto: String, accion: () -> Unit) = TextView(this).apply {
        text = texto
        typeface = Papel.letraNegrita(this@LibretaActivity)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
        setTextColor(getColor(R.color.tapa_etiqueta))
        gravity = Gravity.CENTER
        minHeight = dp(48)
        setPadding(dp(16), dp(8), dp(16), dp(8))
        background = GradientDrawable().apply { setColor(getColor(R.color.tapa_tela)); cornerRadius = dp(6).toFloat() }
        stateListAnimator = android.animation.AnimatorInflater.loadStateListAnimator(this@LibretaActivity, R.animator.presionar)
        paintFlags = paintFlags or Paint.ANTI_ALIAS_FLAG
        isClickable = true
        setOnClickListener { accion() }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(14) }
    }

    /** Un «Copiar» discreto, subrayado como a lápiz, para pasar el código por WhatsApp sin errores. */
    private fun botonCopiar(texto: String) = TextView(this).apply {
        val normal = "Copiar"
        text = normal
        typeface = Papel.letraNegrita(this@LibretaActivity)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 19f)
        setTextColor(getColor(R.color.tinta_suave))
        paintFlags = paintFlags or Paint.UNDERLINE_TEXT_FLAG
        gravity = Gravity.CENTER
        minHeight = dp(48)
        minWidth = dp(96)
        contentDescription = "Copiar el código $texto"
        isClickable = true
        setOnClickListener { v ->
            val portapapeles = getSystemService(android.content.ClipboardManager::class.java)
            portapapeles.setPrimaryClip(android.content.ClipData.newPlainText("Código de la libreta", texto))
            v.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
            text = "¡Copiado!"
            setTextColor(getColor(R.color.tinta_azul))
            removeCallbacks(null)
            postDelayed({ text = normal; setTextColor(getColor(R.color.tinta_suave)) }, 1600)
        }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        }
    }

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
}
