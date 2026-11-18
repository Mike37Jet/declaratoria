package com.roguelito.rommicines.libreta

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.BitmapFactory
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
import android.widget.ImageView
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.firebase.firestore.ListenerRegistration
import com.roguelito.rommicines.R
import com.roguelito.rommicines.Toques
import java.util.concurrent.Executors

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
    private var hojaPapel: HojaDrawable? = null
    private var pagina: PaginaView? = null
    private var escuchaManchas: ListenerRegistration? = null
    private val vistas = mutableMapOf<String, PlanView>()
    private val porEscribir = mutableListOf<PlanView>()
    private var hechos = -1

    /** Lo que pidió el widget (tachar, firmar, abrir un plan o escribir uno nuevo), hasta que llegan los planes. */
    private var porHacer: Pair<String, String?>? = null
    private var campoNuevo: EditText? = null

    private var escuchaPlanes: ListenerRegistration? = null
    private var escuchaMiembros: ListenerRegistration? = null
    private var escuchaRecuerdos: ListenerRegistration? = null

    /** Lo último que llegó de Firestore, para numerar el boleto y saber qué planes ya tienen foto. */
    private var ultimosPlanes: List<Plan> = emptyList()
    /** Los planes tal como llegan de Firestore, antes de ponerles la tinta actual de cada uno. */
    private var crudos: List<Plan> = emptyList()
    /** Vuelve a entintar el renglón nuevo (campo y lapicero) con otra tinta. */
    private var ponerTinta: ((Tinta) -> Unit)? = null
    private var conRecuerdo: Set<String> = emptySet()

    // La hoja de «¡Lo hicieron!» que pide la foto, si está abierta.
    private var capa: FrameLayout? = null
    private var planDeCapa: Plan? = null
    private var fotoLista: ByteArray? = null
    private var vistaFoto: ImageView? = null
    private var avisoFoto: TextView? = null
    private var botonPegar: TextView? = null
    private val trabajo = Executors.newSingleThreadExecutor()

    /** Con la tapa abierta, «atrás» la cierra en vez de salir (Android 13+; antes, onBackPressed). */
    private val cerrarTapa: Any? by lazy {
        if (android.os.Build.VERSION.SDK_INT >= 33) android.window.OnBackInvokedCallback { atras() } else null
    }

    /** Primero se cierra la hoja de la foto; luego, la tapa. */
    private fun atras() {
        if (capa != null) cerrarCapa() else tapa?.cerrar()
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
        tomarAccion(intent)
        mostrar()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        tomarAccion(intent)
        if (hechos >= 0) aplicarAccion()
    }

    private fun tomarAccion(intent: Intent?) {
        val accion = intent?.getStringExtra(AccionLibretaActivity.ACCION) ?: return
        porHacer = accion to intent.getStringExtra(AccionLibretaActivity.PLAN)
    }

    /**
     * Lo que pidió el widget, ya con los planes cargados: se abre la tapa, se baja hasta el plan y
     * se tacha (con su sello y luego la foto), se firma (con la notita) o se deja listo el renglón nuevo.
     */
    private fun aplicarAccion() {
        val (accion, id) = porHacer ?: return
        if (planes == null) return
        porHacer = null
        val t = tapa
        val espera = if (t != null && !t.abierta) { t.abrir(); 700L } else 0L
        escritorio.postDelayed({
            if (isFinishing) return@postDelayed
            if (accion == AccionLibretaActivity.NUEVO) {
                hoja?.fullScroll(View.FOCUS_DOWN)
                campoNuevo?.let { campo ->
                    campo.requestFocus()
                    getSystemService(android.view.inputmethod.InputMethodManager::class.java)
                        ?.showSoftInput(campo, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                }
                return@postDelayed
            }
            val plan = ultimosPlanes.firstOrNull { it.id == id } ?: return@postDelayed
            vistas[plan.id]?.let { v ->
                hoja?.smoothScrollTo(0, (v.top + (planes?.top ?: 0) - linea * 2).toInt().coerceAtLeast(0))
            }
            when (accion) {
                AccionLibretaActivity.TACHAR ->
                    if (plan.hecho == null && !plan.porFirmar) escritorio.postDelayed({ if (!isFinishing) tachar(plan, true) }, 350)
                AccionLibretaActivity.FIRMAR -> if (plan.porFirmar) firmar(plan)
            }
        }, espera)
    }

    private fun mostrar() {
        escritorio.removeAllViews()
        atrasCierraTapa(false)
        tapa = null; hojaPapel = null; pagina = null; planes = null; contador = null; cuenta = null; estado = null; hoja = null; capa = null
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
        AvisosLibreta.libretaAbierta = true
        AvisosLibreta.limpiar(this)
        escuchar()
    }

    override fun onStop() {
        super.onStop()
        AvisosLibreta.libretaAbierta = false
        escuchaPlanes?.remove(); escuchaPlanes = null
        escuchaMiembros?.remove(); escuchaMiembros = null
        escuchaRecuerdos?.remove(); escuchaRecuerdos = null
        escuchaManchas?.remove(); escuchaManchas = null
    }

    override fun onDestroy() {
        super.onDestroy()
        trabajo.shutdown()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val t = tapa
        if (capa != null || (t != null && t.abierta)) atras() else @Suppress("DEPRECATION") super.onBackPressed()
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
            escuchaMiembros = Libreta.escucharMiembros(this, Libreta.pareja(this)!!) { n ->
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
        botones.forEach { (texto, accion) -> tarjeta.addView(boton(texto, accion = accion)) }
        estado = letra("", 17f, R.color.sello).apply { visibility = View.GONE }
        tarjeta.addView(estado)
        escritorio.addView(tarjeta, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER).apply {
            setMargins(dp(20), dp(20), dp(20), dp(20))
        })
        tarjeta.rotation = -0.8f
    }

    // --- La libreta abierta -----------------------------------------------------------

    private fun libreta() {
        val papel = HojaDrawable(this).apply { semilla = Libreta.pareja(this@LibretaActivity).hashCode() }
        hojaPapel = papel
        val pagina = PaginaView(this, papel).apply {
            alLimpiar = { n -> Libreta.limpiarMancha(this@LibretaActivity, n) }
            this@LibretaActivity.pagina = this
            setPadding(0, 0, 0, (linea * 3).toInt())
            clipChildren = false
        }
        // Encabezado: dos renglones, el título escrito sobre el segundo.
        val encabezado = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.BOTTOM }
        encabezado.addView(letra("Nuestras aventuras", 30f, R.color.tinta_lapiz, negrita = true).apply {
            gravity = Gravity.BOTTOM
            setPadding(margen, 0, 0, dp(5))
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        encabezado.addView(pestanaAlbum(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.TOP; topMargin = dp(10); rightMargin = dp(14)
        })
        pagina.addView(encabezado, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (linea * 2).toInt()))

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
        pagina.addView(renglonNuevo(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, linea.toInt()))
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
            alCambiar = { abierta ->
                atrasCierraTapa(abierta)
                if (abierta) escribirPendientes()
            }
        }
        tapa = t
        marco.addView(t)
        escritorio.addView(marco, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT).apply {
            setMargins(dp(12), dp(12), dp(12), dp(12))
        })
    }

    /** El renglón en blanco al final: el campo, el clip (para proponer) y el lapicero. */
    private fun renglonNuevo(): LinearLayout {
        val tinta = getColor(Libreta.tinta(this).color)
        val campo = escribir(tinta)
        campoNuevo = campo
        val clip = BotonClip(this)
        val lapicero = BotonLapicero(this, tinta)
        fun ponerLetra(l: Lapicero) { campo.typeface = if (l.negrita) Papel.letraNegrita(this) else Papel.letra(this) }
        ponerLetra(lapicero.lapicero)
        lapicero.alCambiar = ::ponerLetra
        lapicero.alPedirTinta = ::elegirTinta
        ponerTinta = { t -> getColor(t.color).let { campo.setTextColor(it); lapicero.tinta = it } }
        clip.alCambiar = { puesto -> campo.hint = if (puesto) "+ proponer un plan…" else "+ escribir un plan…" }
        campo.setOnEditorActionListener { _, accion, _ ->
            if (accion != EditorInfo.IME_ACTION_DONE) return@setOnEditorActionListener false
            val texto = campo.text.toString().trim()
            if (texto.isNotEmpty()) {
                Libreta.agregar(this, texto, propuesta = clip.puesto)
                campo.text = null
                // El clip es para una sola propuesta: luego se vuelve a escribir normal.
                if (clip.puesto) { clip.puesto = false; clip.alCambiar?.invoke(false) }
                hoja?.post { hoja?.fullScroll(View.FOCUS_DOWN); campo.requestFocus() }
            }
            true
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(campo, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
            addView(clip, LinearLayout.LayoutParams(dp(40), ViewGroup.LayoutParams.MATCH_PARENT))
            addView(lapicero, LinearLayout.LayoutParams(dp(44), ViewGroup.LayoutParams.MATCH_PARENT).apply { rightMargin = dp(10) })
        }
    }

    /** El campo se escribe con el teclado, pero con tu tinta. */
    private fun escribir(tinta: Int): EditText = EditText(this).apply {
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
        setPadding(margen, 0, dp(4), (dp(8) - descenso).toInt().coerceAtLeast(0))
    }

    private fun escuchar() {
        val pareja = Libreta.pareja(this) ?: return
        if (planes == null) return
        escuchaPlanes?.remove()
        escuchaPlanes = Libreta.escucharPlanes(pareja, { crudos = it; pintar(Libreta.conTintas(this, it)) }) { mensaje(it) }
        escuchaMiembros?.remove()
        escuchaRecuerdos?.remove()
        escuchaRecuerdos = Album.escuchar(pareja) { lista -> conRecuerdo = lista.map { it.id }.toSet() }
        escuchaManchas?.remove()
        escuchaManchas = Libreta.escucharManchas(pareja) { limpias -> hojaPapel?.limpias = limpias }
        escuchaMiembros = Libreta.escucharMiembros(this, pareja, alCambiarTintas = ::alCambiarTintas) { n ->
            val codigo = Libreta.codigo(this)
            if (n < 2 && codigo != null) mensaje("Falta ${Toques.otro(Toques.yo(this))}: su código es ${Codigos.mostrar(codigo)}", error = false)
            else if (estado?.text?.startsWith("Falta") == true) mensaje("")
        }
    }

    /** Cambió una tinta (la tuya o la del otro): se repintan los planes, el renglón nuevo y el widget. */
    private fun alCambiarTintas() {
        ponerTinta?.invoke(Libreta.tinta(this))
        if (planes != null) pintar(Libreta.conTintas(this, crudos))
    }

    private fun pintar(lista: List<Plan>) {
        val contenedor = planes ?: return
        ultimosPlanes = lista
        val abierta = tapa?.abierta == true
        val yo = Libreta.yo()
        val ids = lista.map { it.id }.toSet()
        vistas.keys.filter { it !in ids }.forEach { id -> vistas.remove(id)?.let { contenedor.removeView(it); porEscribir.remove(it) } }

        lista.forEachIndexed { i, plan ->
            val vista = vistas[plan.id]
            if (vista == null) {
                val nueva = PlanView(this, plan).apply { alTachar = ::tachar; alTocar = ::abrirRecuerdo; alFirmar = ::firmar; alCorregir = ::corregir }
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
        hojaPapel?.manchas = Libreta.manchas(n)
        hechos = n
        LibretaWidget.guardar(this, lista)
        aplicarAccion()
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
        if (hecho) {
            Libreta.tachar(this, plan, true)
            // Que el sello caiga y se asiente antes de pedir la foto.
            if (plan.id !in conRecuerdo) escritorio.postDelayed({
                if (!isFinishing && capa == null) pedirFoto(plan.copy(hecho = System.currentTimeMillis()))
            }, 900)
            return
        }
        nota("¿Quitamos el tachón?", "«${plan.texto}» vuelve a quedar pendiente, sin sello.", "Sí, quitar el tachón", "Dejarlo así") {
            Libreta.tachar(this, plan, false)
        }
    }

    /**
     * Pulsación larga en un plan sin tachar: una notita para corregir lo que dice,
     * y abajo la opción de borrarlo (por si se escribió por error).
     */
    private fun corregir(plan: Plan) {
        if (capa != null) return
        val tarjeta = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = HojaDrawable(this@LibretaActivity)
            elevation = dp(10).toFloat()
            setPadding(margen + dp(4), linea.toInt(), dp(20), dp(14))
            rotation = 1.2f
            isClickable = true
        }
        tarjeta.addView(letra("Corregir el plan", 28f, R.color.tinta_lapiz, negrita = true))
        val campo = EditText(this).apply {
            background = null
            typeface = if (plan.lapicero.negrita) Papel.letraNegrita(this@LibretaActivity) else Papel.letra(this@LibretaActivity)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, Papel.LETRA_SP)
            setTextColor(getColor(plan.tinta.color))
            setText(plan.texto)
            includeFontPadding = false
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            imeOptions = EditorInfo.IME_ACTION_DONE
            isSingleLine = true
            filters = arrayOf(InputFilter.LengthFilter(Libreta.MAX_TEXTO))
            minHeight = linea.toInt()
            setPadding(0, dp(6), 0, dp(4))
        }
        val guardar = { Libreta.editar(this, plan, campo.text.toString()); cerrarCapa() }
        campo.setOnEditorActionListener { _, accion, _ -> if (accion == EditorInfo.IME_ACTION_DONE) { guardar(); true } else false }
        tarjeta.addView(campo)
        tarjeta.addView(boton("Guardar") { guardar() })
        tarjeta.addView(letra("Borrar este plan", 19f, R.color.sello, negrita = true).apply {
            paintFlags = paintFlags or Paint.UNDERLINE_TEXT_FLAG
            gravity = Gravity.CENTER
            minHeight = dp(48)
            isClickable = true
            setOnClickListener { cerrarCapa(); borrar(plan) }
        })
        tarjeta.addView(letra("Dejarlo", 19f, R.color.tinta_suave, negrita = true).apply {
            paintFlags = paintFlags or Paint.UNDERLINE_TEXT_FLAG
            gravity = Gravity.CENTER
            minHeight = dp(48)
            isClickable = true
            setOnClickListener { cerrarCapa() }
        })
        mostrarCapa(tarjeta)
        campo.post { campo.requestFocus(); campo.setSelection(campo.text.length) }
    }

    /** Borrar un plan escrito por error: el renglón se desvanece y se va de los dos celulares. */
    private fun borrar(plan: Plan) {
        val que = if (plan.propuesta && plan.firmadaPor == null) "la propuesta" else "el plan"
        nota("¿Lo borramos?", "«${plan.texto}» se borra de la libreta, para los dos.", "Sí, borrar $que", "Dejarlo") {
            val vista = vistas[plan.id]
            if (vista == null || !Papel.conMovimiento()) { Libreta.borrar(this, plan); return@nota }
            vista.animate().alpha(0f).translationX(-dp(12).toFloat()).setDuration(180).setInterpolator(Papel.salida)
                .withEndAction { Libreta.borrar(this, plan) }.start()
        }
    }

    /** Una propuesta la firma el otro; quien la propuso solo puede esperar. */
    private fun firmar(plan: Plan) {
        val otro = Toques.otro(Toques.yo(this))
        if (plan.autor == Libreta.yo()) {
            val texto = "Falta que $otro la firme"
            mensaje(texto, error = false, icono = R.drawable.ic_libreta_clip)
            escritorio.postDelayed({ if (estado?.text?.toString()?.startsWith(texto) == true) mensaje("") }, 3000)
            return
        }
        nota("¿Lo firmas?", "«${plan.texto}»\nCon tu firma queda como plan de los dos.", "Firmar", "Todavía no", siIcono = R.drawable.ic_libreta_firma) {
            Libreta.firmar(this, plan)
        }
    }

    // --- El álbum ---------------------------------------------------------------------

    private fun abrirAlbum(irA: String? = null, recien: Boolean = false) {
        startActivity(Intent(this, AlbumActivity::class.java).apply {
            irA?.let { putExtra(AlbumActivity.IR_A, it) }
            putExtra(AlbumActivity.RECIEN, recien)
        })
    }

    /** Tocar un plan tachado: su página del álbum, o la hoja para pegarle una foto si aún no tiene. */
    private fun abrirRecuerdo(plan: Plan) {
        if (plan.id in conRecuerdo) abrirAlbum(plan.id) else if (capa == null) pedirFoto(plan)
    }

    /** Una pestaña de cartulina asomando por el borde de la hoja, como separador. */
    private fun pestanaAlbum() = TextView(this).apply {
        text = "Álbum"
        typeface = Papel.letraNegrita(this@LibretaActivity)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 19f)
        setTextColor(getColor(R.color.boleto_tinta))
        gravity = Gravity.CENTER
        minHeight = dp(40)
        setPadding(dp(14), dp(4), dp(14), dp(4))
        background = GradientDrawable().apply { setColor(getColor(R.color.cinta_miel)); cornerRadius = dp(4).toFloat() }
        rotation = 2f
        elevation = dp(2).toFloat()
        stateListAnimator = android.animation.AnimatorInflater.loadStateListAnimator(this@LibretaActivity, R.animator.presionar)
        contentDescription = "Abrir el álbum de recuerdos"
        isClickable = true
        setOnClickListener { abrirAlbum() }
    }

    /** «¡Lo hicieron!»: una hoja encima de la libreta que pide la foto y una línea. */
    private fun pedirFoto(plan: Plan) {
        planDeCapa = plan
        fotoLista = null
        val tinta = getColor(Libreta.tinta(this).color)

        val tarjeta = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = HojaDrawable(this@LibretaActivity)
            elevation = dp(10).toFloat()
            setPadding(margen + dp(4), linea.toInt(), dp(20), dp(18))
            rotation = -0.8f
            isClickable = true // que los toques no atraviesen la hoja
        }
        tarjeta.addView(letra("¡Lo hicieron!", 30f, R.color.tinta_lapiz, negrita = true))
        tarjeta.addView(letra("«${plan.texto}»\nPeguen una foto en el álbum para recordarlo.", 20f, R.color.tinta_suave).apply {
            (layoutParams as ViewGroup.MarginLayoutParams).topMargin = dp(4)
        })

        // La polaroid vacía: se toca para elegir la foto.
        val lado = dp(176)
        val polaroid = FrameLayout(this).apply {
            setBackgroundColor(getColor(R.color.polaroid))
            setPadding(dp(10), dp(10), dp(10), dp(30))
            elevation = dp(3).toFloat()
            rotation = 2.2f
            contentDescription = "Elegir una foto"
            isClickable = true
            setOnClickListener { elegirFoto() }
        }
        vistaFoto = ImageView(this).apply {
            setBackgroundColor(getColor(R.color.foto_vacia))
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        polaroid.addView(vistaFoto, FrameLayout.LayoutParams(lado, lado))
        avisoFoto = letra("+ elegir foto", 22f, R.color.tinta_suave, negrita = true).apply {
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(lado, lado)
        }
        polaroid.addView(avisoFoto)
        tarjeta.addView(polaroid, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(18); bottomMargin = dp(10)
        })

        val campo = EditText(this).apply {
            background = null
            typeface = Papel.letra(this@LibretaActivity)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setTextColor(tinta)
            setHintTextColor(getColor(R.color.tinta_guia))
            hint = "Una línea para recordarlo…"
            includeFontPadding = false
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            imeOptions = EditorInfo.IME_ACTION_DONE
            isSingleLine = true
            filters = arrayOf(InputFilter.LengthFilter(Album.MAX_LINEA))
            minHeight = linea.toInt()
            setPadding(0, 0, 0, dp(4))
        }
        tarjeta.addView(campo)

        botonPegar = boton("Pegar en el álbum") { pegar(campo.text.toString()) }.apply { isEnabled = false; alpha = 0.45f }
        tarjeta.addView(botonPegar)
        tarjeta.addView(letra("Ahora no", 19f, R.color.tinta_suave, negrita = true).apply {
            paintFlags = paintFlags or Paint.UNDERLINE_TEXT_FLAG
            gravity = Gravity.CENTER
            minHeight = dp(48)
            isClickable = true
            setOnClickListener { cerrarCapa() }
        })
        tarjeta.addView(letra("Puedes pegarla después tocando el plan tachado.", 17f, R.color.tinta_guia).apply { gravity = Gravity.CENTER })

        mostrarCapa(tarjeta)
    }

    /** Pone una hoja suelta encima de la libreta, con el escritorio oscurecido detrás. */
    private fun mostrarCapa(tarjeta: View) {
        val desplazable = ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            addView(FrameLayout(this@LibretaActivity).apply {
                addView(tarjeta, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER).apply {
                    setMargins(dp(22), dp(24), dp(22), dp(24))
                })
            })
        }
        val nueva = FrameLayout(this).apply {
            setBackgroundColor(0x73000000)
            elevation = dp(30).toFloat() // por encima del marco de la libreta, que también flota
            isClickable = true
            setOnClickListener { cerrarCapa() }
            addView(desplazable)
        }
        capa = nueva
        escritorio.addView(nueva, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        atrasCierraTapa(false); atrasCierraTapa(true) // «atrás» ya pasa por la hoja

        if (Papel.conMovimiento()) {
            nueva.alpha = 0f
            nueva.animate().alpha(1f).setDuration(200).setInterpolator(Papel.salida).start()
            tarjeta.translationY = dp(40).toFloat()
            tarjeta.alpha = 0f
            tarjeta.animate().translationY(0f).alpha(1f).setDuration(260).setInterpolator(Papel.salida).start()
        }
    }

    /**
     * Una notita de papel para preguntar algo (en vez del diálogo gris del sistema):
     * título a mano, el plan entre comillas y dos respuestas.
     */
    private fun nota(titulo: String, texto: String, si: String, no: String, siIcono: Int? = null, accion: () -> Unit) {
        if (capa != null) return
        val tarjeta = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = HojaDrawable(this@LibretaActivity)
            elevation = dp(10).toFloat()
            setPadding(margen + dp(4), linea.toInt(), dp(20), dp(14))
            rotation = 1.2f
            isClickable = true
        }
        tarjeta.addView(letra(titulo, 28f, R.color.tinta_lapiz, negrita = true))
        tarjeta.addView(letra(texto, 20f, R.color.tinta_suave).apply {
            (layoutParams as ViewGroup.MarginLayoutParams).topMargin = dp(4)
        })
        tarjeta.addView(boton(si, siIcono) { cerrarCapa(); accion() })
        tarjeta.addView(letra(no, 19f, R.color.tinta_suave, negrita = true).apply {
            paintFlags = paintFlags or Paint.UNDERLINE_TEXT_FLAG
            gravity = Gravity.CENTER
            minHeight = dp(48)
            isClickable = true
            setOnClickListener { cerrarCapa() }
        })
        mostrarCapa(tarjeta)
    }

    /**
     * La notita de las tintas: un manchón de cada una con su nombre escrito en ella. La del otro
     * sale ocupada y no se puede tomar, para que nunca escriban los dos del mismo color.
     */
    private fun elegirTinta() {
        if (capa != null) return
        val mia = Libreta.tinta(this)
        val ocupada = Libreta.tintaOcupada(this)
        val otro = Toques.otro(Toques.yo(this))
        val tarjeta = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = HojaDrawable(this@LibretaActivity)
            elevation = dp(10).toFloat()
            setPadding(margen + dp(4), linea.toInt(), dp(20), dp(14))
            rotation = -1f
            isClickable = true
        }
        tarjeta.addView(letra("Tu tinta", 28f, R.color.tinta_lapiz, negrita = true).apply {
            text = conIcono("Tu tinta", trazo(R.drawable.ic_libreta_tinta, getColor(R.color.tinta_lapiz), Papel.sp(this@LibretaActivity, 26f)), alFinal = true)
        })
        tarjeta.addView(letra("Con ella se ven tus planes en los dos celulares. La de $otro es solo suya.", 19f, R.color.tinta_suave).apply {
            (layoutParams as ViewGroup.MarginLayoutParams).apply { topMargin = dp(4); bottomMargin = dp(6) }
        })
        Tinta.entries.forEach { t ->
            val tomada = t == ocupada
            val color = getColor(t.color)
            tarjeta.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(48)
                addView(ManchonTinta(this@LibretaActivity, color, elegida = t == mia), LinearLayout.LayoutParams(dp(34), dp(34)).apply { rightMargin = dp(13) })
                addView(letra(t.nombre, 23f, t.color, negrita = t == mia).apply {
                    if (tomada) text = "${t.nombre} · es la de $otro"
                    if (t == mia) text = "${t.nombre} · la tuya"
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                if (tomada) {
                    alpha = 0.38f
                    contentDescription = "${t.nombre}, es la tinta de $otro"
                } else {
                    isClickable = true
                    contentDescription = t.nombre + if (t == mia) ", elegida" else ""
                    setOnClickListener {
                        if (t != mia && Libreta.elegirTinta(this@LibretaActivity, t)) {
                            performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                            alCambiarTintas() // también reescribe la copia del widget
                        }
                        cerrarCapa()
                    }
                }
            })
        }
        tarjeta.addView(letra("Dejarla así", 19f, R.color.tinta_suave, negrita = true).apply {
            paintFlags = paintFlags or Paint.UNDERLINE_TEXT_FLAG
            gravity = Gravity.CENTER
            minHeight = dp(48)
            isClickable = true
            setOnClickListener { cerrarCapa() }
        })
        mostrarCapa(tarjeta)
    }

    private fun cerrarCapa() {
        val c = capa ?: return
        capa = null; planDeCapa = null; fotoLista = null; vistaFoto = null; avisoFoto = null; botonPegar = null
        currentFocus?.let { getSystemService(android.view.inputmethod.InputMethodManager::class.java).hideSoftInputFromWindow(it.windowToken, 0) }
        if (tapa?.abierta != true) atrasCierraTapa(false)
        if (!Papel.conMovimiento()) { escritorio.removeView(c); return }
        c.isClickable = false
        c.animate().alpha(0f).setDuration(160).setInterpolator(Papel.salida).withEndAction { escritorio.removeView(c) }.start()
    }

    private fun elegirFoto() {
        val pedir = if (android.os.Build.VERSION.SDK_INT >= 33) Intent(android.provider.MediaStore.ACTION_PICK_IMAGES)
        else Intent(Intent.ACTION_GET_CONTENT).apply { addCategory(Intent.CATEGORY_OPENABLE) }
        pedir.type = "image/*"
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(pedir, ELEGIR_FOTO)
        } catch (e: android.content.ActivityNotFoundException) {
            avisoFoto?.text = "No encontré la galería"
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != ELEGIR_FOTO || resultCode != RESULT_OK) return
        // Algunos selectores devuelven la foto en clipData aunque se pida una sola.
        val uri = data?.data ?: data?.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri ?: return
        avisoFoto?.text = "Revelando…"
        avisoFoto?.visibility = View.VISIBLE
        trabajo.execute {
            val bytes = Album.comprimir(this, uri)
            val vista = bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
            runOnUiThread {
                if (capa == null) return@runOnUiThread
                if (bytes == null || vista == null) { avisoFoto?.text = "Esa foto no se pudo abrir"; return@runOnUiThread }
                fotoLista = bytes
                vistaFoto?.setImageBitmap(vista)
                avisoFoto?.visibility = View.GONE
                if (Papel.conMovimiento()) {
                    vistaFoto?.alpha = 0f
                    vistaFoto?.animate()?.alpha(1f)?.setDuration(500)?.setInterpolator(Papel.salida)?.start()
                }
                botonPegar?.apply { isEnabled = true; animate().alpha(1f).setDuration(150).start() }
            }
        }
    }

    private fun pegar(texto: String) {
        val plan = planDeCapa ?: return
        val foto = fotoLista ?: return
        // Si el tachado todavía no volvió de Firestore, se cuenta con la hora local.
        val actual = ultimosPlanes.find { it.id == plan.id }?.takeIf { it.hecho != null } ?: plan
        val lista = ultimosPlanes.filter { it.id != plan.id } + actual
        Album.pegar(this, actual, Album.numeroDeBoleto(lista, plan.id), foto, texto)
        conRecuerdo = conRecuerdo + plan.id
        cerrarCapa()
        abrirAlbum(plan.id, recien = true)
    }

    // --- Piezas -----------------------------------------------------------------------

    private fun mensaje(texto: String, error: Boolean = true, icono: Int? = null) {
        estado?.apply {
            val color = getColor(if (error) R.color.sello else R.color.tinta_suave)
            text = if (icono == null) texto else conIcono(texto, trazo(icono, color, textSize), alFinal = true)
            setTextColor(color)
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
    /** Un ícono de trazo (Phosphor, licencia MIT) del color de la tinta y del alto de la letra. */
    private fun trazo(icono: Int, color: Int, lado: Float) = getDrawable(icono)!!.mutate().apply {
        setTint(color)
        setBounds(0, 0, lado.toInt(), lado.toInt())
    }

    /** El texto con su ícono pegado, dentro de la misma línea (así se centran juntos). */
    private fun conIcono(texto: String, icono: android.graphics.drawable.Drawable, alFinal: Boolean = false): CharSequence {
        val marca = android.text.SpannableStringBuilder()
        val span = android.text.style.ImageSpan(icono, android.text.style.DynamicDrawableSpan.ALIGN_BASELINE)
        if (alFinal) marca.append(texto).append("  ").append("·", span, 0)
        else marca.append("·", span, 0).append("  ").append(texto)
        return marca
    }

    private fun boton(texto: String, icono: Int? = null, accion: () -> Unit) = TextView(this).apply {
        text = if (icono == null) texto else conIcono(texto, trazo(icono, getColor(R.color.tapa_etiqueta), Papel.sp(this@LibretaActivity, 22f)))
        contentDescription = texto
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

    companion object {
        private const val ELEGIR_FOTO = 41
    }
}
