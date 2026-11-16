package com.roguelito.rommicines

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Outline
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.MediaStore
import android.provider.Settings
import android.util.LruCache
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.Gravity
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.Observer
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.google.firebase.messaging.FirebaseMessaging
import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val trabajo = Executors.newSingleThreadExecutor()
    private val principal = Handler(Looper.getMainLooper())
    private lateinit var boleto: Boleto
    private val miniaturas = LruCache<String, Bitmap>(60)
    private var encolando = false
    private var ultimoEnvio: WorkInfo? = null
    private var ocultarResultado = false
    private var falloSuscripcion = false
    private var escuchaFotos: com.google.firebase.firestore.ListenerRegistration? = null
    private val fotosPrefs by lazy { getSharedPreferences("fotos", MODE_PRIVATE) }
    private val envios by lazy {
        WorkManager.getInstance(this).getWorkInfosForUniqueWorkLiveData(EnviarToqueWorker.ENVIO_MANUAL)
    }
    private val observarEnvios = Observer<List<WorkInfo>> { lista ->
        ultimoEnvio = lista.firstOrNull { !it.state.isFinished }
            ?: lista.maxByOrNull { it.outputData.getLong("cuando", 0) }
        mostrarEnvio()
    }
    private val observarFotos = SharedPreferences.OnSharedPreferenceChangeListener { _, clave ->
        if (clave == "estado" || clave == "ultima_quitada" || clave == "sincronizadas") recargar()
    }
    // La cartelera se lee de la copia que deja la libreta (la misma del widget).
    private val observarLibreta = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> mostrarCartelera() }
    private val observarToques = SharedPreferences.OnSharedPreferenceChangeListener { _, clave ->
        if (clave == "recibido_cuando") mostrarUltimoRecibido()
    }
    private val tic = object : Runnable {
        override fun run() {
            boleto.tiempo = Juntos.ahora()
            principal.postDelayed(this, 1000 - System.currentTimeMillis() % 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        adaptarCabeceras()
        ocultarResultado = savedInstanceState?.getBoolean("ocultar_resultado") ?: false
        findViewById<View>(R.id.raiz).setOnApplyWindowInsetsListener { vista, insets ->
            @Suppress("DEPRECATION")
            vista.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            insets
        }
        boleto = findViewById(R.id.boleto)
        findViewById<View>(R.id.poner_widget).setOnClickListener { elegirWidget() }
        findViewById<View>(R.id.ayuda).setOnClickListener { mostrarAyuda() }
        findViewById<View>(R.id.mandar_toque).apply {
            setOnClickListener { mandarToque() }
            alPresionar(this)
        }
        findViewById<View>(R.id.abrir_libreta).setOnClickListener {
            startActivity(Intent(this, com.roguelito.rommicines.libreta.LibretaActivity::class.java))
        }
        findViewById<View>(R.id.ver_funcion).setOnClickListener {
            runCatching { Juntos.abrirPagina(this).send() }
        }
        mostrarQuienEres()
        prepararToques()
        val contexto = applicationContext
        trabajo.execute {
            Fotos.sembrar(contexto)
            FotosCompartidas.sincronizar(contexto)
            principal.post { if (!isDestroyed) recargar() }
        }
    }

    private fun adaptarCabeceras() {
        if (resources.configuration.fontScale < 1.3f) return
        listOf(R.id.cabecera_fotos to R.id.titulo_fotos).forEach { (fila, titulo) ->
            findViewById<LinearLayout>(fila).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.START
            }
            findViewById<TextView>(titulo).layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
    }

    override fun onStart() {
        super.onStart()
        envios.observeForever(observarEnvios)
        fotosPrefs.registerOnSharedPreferenceChangeListener(observarFotos)
        getSharedPreferences("libreta_widget", MODE_PRIVATE).registerOnSharedPreferenceChangeListener(observarLibreta)
        getSharedPreferences("toques", MODE_PRIVATE).registerOnSharedPreferenceChangeListener(observarToques)
        // Con la libreta vinculada, «Nuestros recuerdos» es el mismo en los dos celulares.
        escuchaFotos = FotosCompartidas.escuchar(this)
    }

    override fun onResume() {
        super.onResume()
        principal.post(tic)
        mostrarAvisoRecepcion()
        mostrarAvisoSegundoPlano()
        pedirSegundoPlano()
        recargar()
        mostrarCartelera()
        mostrarUltimoRecibido()
        ContadorWidget.actualizarTodos(this)
    }

    override fun onPause() {
        super.onPause()
        principal.removeCallbacks(tic)
    }

    override fun onStop() {
        envios.removeObserver(observarEnvios)
        fotosPrefs.unregisterOnSharedPreferenceChangeListener(observarFotos)
        getSharedPreferences("libreta_widget", MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(observarLibreta)
        getSharedPreferences("toques", MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(observarToques)
        escuchaFotos?.remove(); escuchaFotos = null
        FotosCompartidas.dejarDeEscuchar()
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("ocultar_resultado", ocultarResultado)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        trabajo.shutdown()
        super.onDestroy()
    }

    private fun prepararToques() {
        ToquesService.crearCanal(this)
        suscribir()
        if (Toques.yo(this) == null) preguntarQuienEres()
    }

    private fun suscribir() {
        FirebaseMessaging.getInstance().subscribeToTopic(Toques.TEMA).addOnCompleteListener(this) {
            falloSuscripcion = !it.isSuccessful
            mostrarAvisoRecepcion()
        }
    }

    private fun mostrarAvisoRecepcion() {
        val avisos = getSystemService(NotificationManager::class.java).areNotificationsEnabled()
        findViewById<Button>(R.id.aviso_recepcion).apply {
            visibility = if (!avisos || falloSuscripcion) View.VISIBLE else View.GONE
            text = if (!avisos) "Activar avisos de cariño" else "Reintentar recepción de toques"
            setOnClickListener {
                if (avisos) suscribir() else activarAvisos()
            }
        }
    }

    /**
     * Android frena y retrasa el trabajo en segundo plano de las apps que casi no se abren, y un toque
     * retrasado más de 5 minutos se descarta. Dos ajustes lo evitan: sin optimización de batería y sin
     * «quitar permisos si no se usa». El primero se pide con un diálogo del sistema; el segundo solo
     * se puede cambiar a mano, así que se abre su pantalla de ajustes.
     */
    private fun sinOptimizarBateria() = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)

    private fun sinPausaPorInactividad() = Build.VERSION.SDK_INT < 30 || packageManager.isAutoRevokeWhitelisted

    private fun segundoPlanoListo() = sinOptimizarBateria() && sinPausaPorInactividad()

    private fun abrirAjustesSegundoPlano() {
        val paquete = Uri.parse("package:$packageName")
        val intent = if (!sinOptimizarBateria()) Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, paquete)
            else Intent(Intent.ACTION_AUTO_REVOKE_PERMISSIONS, paquete)
        try {
            startActivity(intent)
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, paquete))
        }
    }

    private fun mostrarAvisoSegundoPlano() {
        findViewById<Button>(R.id.aviso_segundo_plano).apply {
            visibility = if (segundoPlanoListo()) View.GONE else View.VISIBLE
            text = if (!sinOptimizarBateria()) "Evitar que Android frene los toques" else "Desactivar la pausa de la app por inactividad"
            setOnClickListener { abrirAjustesSegundoPlano() }
        }
    }

    /** Una sola vez, cuando ya se sabe quién usa el celular: explica y lleva a los ajustes. */
    private fun pedirSegundoPlano() {
        val prefs = getSharedPreferences("permisos", MODE_PRIVATE)
        if (Toques.yo(this) == null || prefs.getBoolean("segundo_plano_pedido", false) || segundoPlanoListo()) return
        prefs.edit().putBoolean("segundo_plano_pedido", true).apply()
        AlertDialog.Builder(this)
            .setTitle("Para que los toques lleguen siempre")
            .setMessage("Android frena las apps que casi no abres, y por eso un toque puede fallar. Te llevo a los ajustes para evitarlo.")
            .setPositiveButton("Configurar") { _, _ -> abrirAjustesSegundoPlano() }
            .setNegativeButton("Ahora no", null)
            .show()
    }

    private fun activarAvisos() {
        val prefs = getSharedPreferences("permisos", MODE_PRIVATE)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            && (!prefs.getBoolean("avisos_pedidos", false) || shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS))) {
            prefs.edit().putBoolean("avisos_pedidos", true).apply()
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), PEDIR_AVISOS)
        } else {
            startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PEDIR_AVISOS) mostrarAvisoRecepcion()
    }

    private fun preguntarQuienEres() {
        val nombres = arrayOf(Toques.ROGUELITO, Toques.MIMINA)
        AlertDialog.Builder(this)
            .setTitle(R.string.quien_eres)
            .setSingleChoiceItems(nombres, nombres.indexOf(Toques.yo(this))) { dialogo, i ->
                Toques.elegir(this, nombres[i])
                ocultarResultado = true
                mostrarQuienEres()
                ContadorWidget.actualizarTodos(this)
                dialogo.dismiss()
                pedirSegundoPlano()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun mostrarQuienEres() {
        val yo = Toques.yo(this)
        findViewById<TextView>(R.id.destinatario).text = if (yo == null) "Un toque de cariño" else "Un toque para ${Toques.otro(yo)}"
        mostrarPastillas()
        mostrarUltimoRecibido()
        mostrarEnvio()
    }

    /** Las frases del toque, a la vista: la elegida va en dorado y es la misma que manda el widget. */
    private fun mostrarPastillas() {
        val fila = findViewById<ViewGroup>(R.id.pastillas)
        val d = resources.displayMetrics.density
        if (fila.childCount != TipoToque.elegibles.size) {
            fila.removeAllViews()
            TipoToque.elegibles.forEach { tipo ->
                fila.addView(TextView(this).apply {
                    text = tipo.etiqueta
                    textSize = 14f
                    setTextColor(getColorStateList(R.color.texto_pastilla))
                    background = getDrawable(R.drawable.pastilla_toque)
                    minHeight = (40 * d).toInt()
                    gravity = Gravity.CENTER
                    setPadding((14 * d).toInt(), 0, (14 * d).toInt(), 0)
                    isClickable = true
                    setOnClickListener {
                        if (Toques.preferido(this@MainActivity) == tipo) return@setOnClickListener
                        performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                        Toques.elegirTipo(this@MainActivity, tipo)
                        ocultarResultado = true
                        mostrarPastillas()
                        mostrarEnvio()
                        ContadorWidget.actualizarTodos(this@MainActivity)
                    }
                    tag = tipo
                })
            }
        }
        val elegido = Toques.preferido(this)
        for (i in 0 until fila.childCount) {
            val v = fila.getChildAt(i)
            val tipo = v.tag as TipoToque
            v.isSelected = tipo == elegido
            v.contentDescription = tipo.frase + if (tipo == elegido) ", elegido" else ". Elegir esta frase"
        }
    }

    /** Lo último que llegó del otro, para que el toque se sienta conversación. */
    private fun mostrarUltimoRecibido() {
        val r = Toques.ultimoRecibido(this)
        findViewById<TextView>(R.id.ultimo_recibido).apply {
            visibility = if (r == null) View.GONE else View.VISIBLE
            if (r != null) text = "Último de ${r.nombre}: «${r.tipo.frase}» · ${Toques.hace(r.cuando)}"
        }
    }

    /** El botón se hunde apenas lo tocas, no al soltarlo. */
    @android.annotation.SuppressLint("ClickableViewAccessibility")
    private fun alPresionar(v: View) {
        v.setOnTouchListener { vista, e ->
            when (e.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> if (vista.isEnabled)
                    vista.animate().scaleX(0.97f).scaleY(0.97f).setDuration(100).start()
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL ->
                    vista.animate().scaleX(1f).scaleY(1f).setDuration(160).start()
            }
            false
        }
    }

    /** Los dos próximos planes de la libreta, como cartelera del cine. */
    private fun mostrarCartelera() {
        val hoja = com.roguelito.rommicines.libreta.LibretaWidget.leer(this)
        val lista = findViewById<LinearLayout>(R.id.planes_cartelera)
        lista.removeAllViews()
        findViewById<TextView>(R.id.cuenta_libreta).text = if (hoja.vinculada) "${hoja.hechos}/${com.roguelito.rommicines.libreta.Libreta.META}" else ""
        val planes = hoja.pendientes.take(2)
        if (planes.isEmpty()) {
            lista.addView(TextView(this).apply {
                text = when {
                    !hoja.vinculada -> "Abran su libreta y escriban la primera aventura."
                    hoja.hechos == 0 -> "Todavía no hay planes en cartelera."
                    else -> "Todo visto. Escriban el próximo estreno."
                }
                setTextColor(getColor(R.color.cream_soft)); textSize = 14f
                typeface = android.graphics.Typeface.SERIF
            })
            return
        }
        planes.forEach { p ->
            lista.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, (3 * resources.displayMetrics.density).toInt(), 0, 0)
                val quien = if (p.tinta == com.roguelito.rommicines.libreta.Tinta.ROSA) Toques.MIMINA else Toques.ROGUELITO
                addView(TextView(context).apply {
                    text = p.texto
                    setTextColor(getColor(R.color.cream)); textSize = 15f
                    typeface = android.graphics.Typeface.SERIF
                    maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(TextView(context).apply {
                    text = if (p.porFirmar) "$quien · por firmar" else quien
                    setTextColor(getColor(R.color.cream_soft)); textSize = 12f
                    setPadding((10 * resources.displayMetrics.density).toInt(), 0, 0, 0)
                })
            })
        }
    }

    private fun mostrarEnvio() {
        val envio = ultimoEnvio
        val ocupado = encolando || envio?.state?.isFinished == false
        val reciente = envio != null && System.currentTimeMillis() - envio.outputData.getLong("cuando", 0) < Juntos.DIA
        val mismoMensaje = envio?.outputData?.getString("tipo") == Toques.preferido(this).codigo
        val fallo = !ocultarResultado && reciente && mismoMensaje && envio?.state == WorkInfo.State.FAILED
        findViewById<Button>(R.id.mandar_toque).apply {
            isEnabled = !ocupado
            alpha = if (ocupado) 0.65f else 1f
            text = when {
                ocupado -> "Enviando…"
                Toques.yo(this@MainActivity) == null -> "Elegir mi nombre"
                fallo -> "Reintentar toque"
                else -> "Enviar toque"
            }
        }
        findViewById<ViewGroup>(R.id.pastillas).let { f -> for (i in 0 until f.childCount) f.getChildAt(i).isEnabled = !ocupado }
        findViewById<TextView>(R.id.estado_toque).apply {
            setTextColor(getColor(if (fallo) R.color.error_texto else R.color.cream_soft))
            text = when {
                ocupado -> ""
                fallo -> "No se pudo enviar: ${envio?.outputData?.getString("motivo") ?: "error inesperado"}."
                !ocultarResultado && reciente && mismoMensaje && envio?.state == WorkInfo.State.SUCCEEDED ->
                    "Enviado a ${envio.outputData.getString("nombre")} · ${Toques.hora(envio.outputData.getLong("cuando", 0))}"
                else -> ""
            }
        }
    }

    private fun mandarToque() {
        if (Toques.yo(this) == null) return preguntarQuienEres()
        if (encolando || ultimoEnvio?.state?.isFinished == false) return
        encolando = true
        ocultarResultado = false
        mostrarEnvio()
        try {
            val operacion = EnviarToqueWorker.programar(this, Toques.preferido(this), Toques.otro(Toques.yo(this)), silencioso = true)
            operacion.result.addListener({
                val fallo = runCatching { operacion.result.get() }.isFailure
                principal.post {
                    if (!isDestroyed) {
                        encolando = false
                        mostrarEnvio()
                        if (fallo) findViewById<TextView>(R.id.estado_toque).text = "No se pudo iniciar el envío. Inténtalo de nuevo."
                    }
                }
            }, Executor { it.run() })
        } catch (e: Exception) {
            encolando = false
            mostrarEnvio()
            findViewById<TextView>(R.id.estado_toque).text = "No se pudo iniciar el envío. Inténtalo de nuevo."
        }
    }

    private fun elegirFotos() {
        if (Fotos.importando) return
        val intent = if (Build.VERSION.SDK_INT >= 33) {
            Intent(MediaStore.ACTION_PICK_IMAGES).putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, MediaStore.getPickImagesMaxLimit())
        } else {
            Intent(Intent.ACTION_GET_CONTENT).setType("image/*")
                .addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        @Suppress("DEPRECATION")
        startActivityForResult(intent, PEDIR_FOTOS)
    }

    @Deprecated("Activity sin AndroidX: recibe el selector de fotos")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != PEDIR_FOTOS || resultCode != RESULT_OK || data == null) return
        val uris = data.clipData?.let { clip -> (0 until clip.itemCount).map { clip.getItemAt(it).uri } }
            ?: listOfNotNull(data.data)
        if (uris.isEmpty()) return
        Fotos.importando = true
        guardarEstadoFotos("Guardando fotos…")
        recargar()
        val contexto = applicationContext
        trabajo.execute {
            val agregadas = Fotos.importar(contexto, uris)
            Fotos.importando = false
            val error = agregadas < uris.size
            val texto = if (error) "No se añadieron ${uris.size - agregadas} fotos. Prueba otra imagen."
                else if (agregadas == 1) "Foto añadida" else "$agregadas fotos añadidas"
            contexto.getSharedPreferences("fotos", MODE_PRIVATE).edit()
                .putString("estado", texto).putBoolean("error", error)
                .putLong("estado_hasta", if (error) Long.MAX_VALUE else System.currentTimeMillis() + 5000).apply()
            CarruselWidget.refrescar(contexto)
            FotosCompartidas.sincronizar(contexto)
        }
    }

    private fun verFoto(foto: File, numero: Int) {
        val imagen = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Recuerdo $numero"
            setImageBitmap(Fotos.reducida(foto, 900))
        }
        val contenedor = android.widget.FrameLayout(this).apply {
            val alto = (resources.displayMetrics.heightPixels * 0.45f).toInt()
            addView(imagen, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, alto))
        }
        AlertDialog.Builder(this).setTitle("Recuerdo $numero").setView(contenedor)
            .setPositiveButton("Cerrar", null)
            .setNeutralButton("Quitar del carrusel") { _, _ -> confirmarQuitar(foto) }
            .show()
    }

    private fun confirmarQuitar(foto: File) {
        AlertDialog.Builder(this)
            .setTitle("¿Quitar esta foto?")
            .setMessage("Puedes deshacerlo. No se borra ninguna foto de tu galería.")
            .setPositiveButton("Quitar") { _, _ ->
                if (Fotos.quitar(this, foto)) {
                    FotosCompartidas.quitar(this, foto)
                    guardarEstadoFotos("Foto quitada", duradero = true)
                    CarruselWidget.refrescar(this)
                } else guardarEstadoFotos("No se pudo quitar. Inténtalo de nuevo.", error = true)
                recargar()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun guardarEstadoFotos(texto: String, error: Boolean = false, duradero: Boolean = false) {
        fotosPrefs.edit().putString("estado", texto).putBoolean("error", error)
            .putLong("estado_hasta", if (error || duradero || Fotos.importando) Long.MAX_VALUE else System.currentTimeMillis() + 5000).apply()
    }

    private fun mostrarEstadoFotos() {
        val texto = if (Fotos.importando) "Guardando fotos…"
            else if (System.currentTimeMillis() < fotosPrefs.getLong("estado_hasta", 0)) fotosPrefs.getString("estado", "").orEmpty() else ""
        findViewById<View>(R.id.resultado_fotos).visibility = if (texto.isBlank()) View.GONE else View.VISIBLE
        findViewById<TextView>(R.id.estado_fotos).apply {
            text = texto
            setTextColor(getColor(if (fotosPrefs.getBoolean("error", false)) R.color.error_texto else R.color.cream_soft))
        }
        val error = fotosPrefs.getBoolean("error", false)
        val deshacer = (texto == "Foto quitada" || texto.startsWith("No se pudo restaurar")) && Fotos.ultimaQuitada(this) != null
        findViewById<Button>(R.id.accion_fotos).apply {
            visibility = if (deshacer) View.VISIBLE else View.GONE
            text = if (error) "Reintentar" else "Deshacer"
            setOnClickListener {
                // Primero se avisa a Firestore, para que la sincronización no la vuelva a quitar.
                Fotos.ultimaQuitada(this@MainActivity)?.let { FotosCompartidas.restaurar(this@MainActivity, it.name) }
                if (Fotos.deshacer(this@MainActivity)) {
                    guardarEstadoFotos("Foto restaurada")
                    CarruselWidget.refrescar(this@MainActivity)
                    FotosCompartidas.sincronizar(this@MainActivity)
                } else guardarEstadoFotos("No se pudo restaurar la foto. Inténtalo de nuevo.", error = true)
                recargar()
            }
        }
        if (texto.isNotBlank() && !deshacer && !error && !Fotos.importando) {
            principal.postDelayed({ if (!isDestroyed) mostrarEstadoFotos() }, 5100)
        }
    }

    private fun elegirWidget() {
        val opciones = mutableListOf("Contador · tiempo juntos", "Carrusel · nuestras fotos", "Cambiar el diseño del contador")
        if (!ContadorWidget.alarmasExactas(this)) opciones.add("Sincronizar la hora del contador")
        AlertDialog.Builder(this).setTitle("Añadir a tu pantalla de inicio")
            .setItems(opciones.toTypedArray()) { _, i ->
                when (i) {
                    0 -> ponerWidget(ContadorWidget::class.java)
                    1 -> ponerWidget(CarruselWidget::class.java)
                    2 -> startActivity(Intent(this, ElegirTemaActivity::class.java))
                    else -> if (Build.VERSION.SDK_INT >= 31) startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
                }
            }
            .setNegativeButton("Cancelar", null).show()
    }

    private fun ponerWidget(widget: Class<*>) {
        val manager = getSystemService(AppWidgetManager::class.java)
        if (!manager.isRequestPinAppWidgetSupported || !manager.requestPinAppWidget(ComponentName(this, widget), null, null)) {
            AlertDialog.Builder(this).setTitle("Añadir un widget").setMessage(R.string.ayuda_widgets)
                .setPositiveButton("Entendido", null).show()
        }
    }

    private fun mostrarAyuda() {
        val temas = arrayOf("Enviar y responder", "Poner un widget", "Mis fotos", "Mi nombre", "Que los toques lleguen siempre")
        val pasos = arrayOf(
            getString(R.string.ayuda_toque) + "\n\nPara responder, toca ‘Un beso de vuelta’ en la notificación.",
            "Toca ‘Añadir widget’ y elige contador o carrusel.\n\nSi no aparece la ventana, mantén pulsado el inicio del celular → Widgets → ROMMICINES.\n\nPara cambiar el diseño del contador (Cielo, Neón, Perla…): ‘Añadir widget’ → ‘Cambiar el diseño del contador’, o mantén presionado el widget y toca el lápiz.",
            "Desliza la tira de película y toca ‘+’ al final para añadir fotos. Toca un fotograma para verlo o quitarlo del carrusel. Después puedes usar ‘Deshacer’.",
            "",
            "",
        )
        AlertDialog.Builder(this).setTitle("¿En qué te ayudo?")
            .setItems(temas) { _, i ->
                if (i == 3) return@setItems preguntarQuienEres()
                if (i == 4) return@setItems if (!getSystemService(NotificationManager::class.java).areNotificationsEnabled()) activarAvisos()
                    else if (!segundoPlanoListo()) abrirAjustesSegundoPlano()
                    else { AlertDialog.Builder(this).setTitle(temas[i]).setMessage("Todo listo: los avisos están activos y Android no frena la app.")
                        .setPositiveButton("Entendido", null).show(); Unit }
                AlertDialog.Builder(this).setTitle(temas[i]).setMessage(pasos[i])
                    .setPositiveButton("Entendido", null).show()
            }
            .setNegativeButton("Cerrar", null).show()
    }

    private fun recargar() {
        val fotos = Fotos.lista(this)
        val tira = findViewById<LinearLayout>(R.id.tira)
        val d = resources.displayMetrics.density
        val alto = (78 * d).toInt(); val ancho = (104 * d).toInt(); val hueco = (5 * d).toInt()
        tira.removeAllViews()
        fotos.forEachIndexed { i, foto ->
            tira.addView(Fotograma(this).apply {
                contentDescription = "Recuerdo ${i + 1}. Ver foto"
                miniaturas.get(foto.name)?.let { setImageBitmap(it) } ?: run {
                    trabajo.execute {
                        val b = Fotos.reducida(foto, 360) ?: return@execute
                        miniaturas.put(foto.name, b)
                        principal.post { if (!isDestroyed) setImageBitmap(b) }
                    }
                }
                setOnClickListener { verFoto(foto, i + 1) }
                setOnLongClickListener { confirmarQuitar(foto); true }
            }, LinearLayout.LayoutParams(ancho, alto).apply { marginEnd = hueco })
        }
        // Al final, el hueco para añadir más.
        tira.addView(TextView(this).apply {
            text = if (Fotos.importando) "…" else "+"
            textSize = 26f
            gravity = Gravity.CENTER
            setTextColor(getColor(R.color.gold_light))
            background = getDrawable(R.drawable.fotograma)
            contentDescription = if (Fotos.importando) "Guardando fotos" else getString(R.string.anadir_fotos)
            isEnabled = !Fotos.importando
            setOnClickListener { elegirFotos() }
        }, LinearLayout.LayoutParams(if (fotos.isEmpty()) ancho * 2 else alto, alto))
        findViewById<TextView>(R.id.pie_tira).text = when {
            fotos.isEmpty() -> getString(R.string.sin_fotos)
            fotos.size == 1 -> "1 fotograma · toca ‘+’ para añadir"
            else -> "${fotos.size} fotogramas · desliza o toca ‘+’ para añadir"
        }
        mostrarEstadoFotos()
    }

    /** Un fotograma de la tira: la foto recortada, con esquinas apenas redondeadas. */
    private class Fotograma(context: Context) : ImageView(context) {
        init {
            scaleType = ScaleType.CENTER_CROP
            setBackgroundColor(context.getColor(R.color.velvet_dark))
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, 2 * resources.displayMetrics.density)
                }
            }
        }
    }

    private companion object {
        const val PEDIR_FOTOS = 7
        const val PEDIR_AVISOS = 8
    }
}
