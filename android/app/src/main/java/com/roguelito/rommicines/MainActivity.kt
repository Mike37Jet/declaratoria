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
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.GridView
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
    private val adaptador = Adaptador()
    private lateinit var contador: TextView
    private var todasLasFotos = false
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
    private val tic = object : Runnable {
        override fun run() {
            contador.text = Juntos.texto(Juntos.ahora())
            principal.postDelayed(this, 1000 - System.currentTimeMillis() % 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        adaptarCabeceras()
        todasLasFotos = savedInstanceState?.getBoolean("todas_fotos") ?: false
        ocultarResultado = savedInstanceState?.getBoolean("ocultar_resultado") ?: false
        findViewById<View>(R.id.raiz).setOnApplyWindowInsetsListener { vista, insets ->
            @Suppress("DEPRECATION")
            vista.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            insets
        }
        contador = findViewById(R.id.contador)
        findViewById<View>(R.id.agregar).setOnClickListener { elegirFotos() }
        findViewById<View>(R.id.poner_widget).setOnClickListener { elegirWidget() }
        findViewById<View>(R.id.ayuda).setOnClickListener { mostrarAyuda() }
        findViewById<View>(R.id.identidad).setOnClickListener { preguntarQuienEres() }
        findViewById<View>(R.id.mandar_toque).setOnClickListener { mandarToque() }
        findViewById<View>(R.id.abrir_libreta).setOnClickListener {
            startActivity(Intent(this, com.roguelito.rommicines.libreta.LibretaActivity::class.java))
        }
        findViewById<View>(R.id.elegir_toque).setOnClickListener { elegirToque() }
        findViewById<View>(R.id.ver_fotos).setOnClickListener {
            todasLasFotos = !todasLasFotos
            recargar()
        }
        findViewById<GridView>(R.id.fotos).apply {
            adapter = adaptador
            setOnItemClickListener { _, _, posicion, _ -> verFoto(adaptador.fotos[posicion], posicion + 1) }
            setOnItemLongClickListener { _, _, posicion, _ ->
                confirmarQuitar(adaptador.fotos[posicion])
                true
            }
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
        listOf(R.id.cabecera to R.id.titulo_app, R.id.cabecera_fotos to R.id.titulo_fotos).forEach { (fila, titulo) ->
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
        ContadorWidget.actualizarTodos(this)
    }

    override fun onPause() {
        super.onPause()
        principal.removeCallbacks(tic)
    }

    override fun onStop() {
        envios.removeObserver(observarEnvios)
        fotosPrefs.unregisterOnSharedPreferenceChangeListener(observarFotos)
        escuchaFotos?.remove(); escuchaFotos = null
        FotosCompartidas.dejarDeEscuchar()
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("todas_fotos", todasLasFotos)
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
        findViewById<Button>(R.id.identidad).apply {
            text = if (yo == null) getString(R.string.elegir_nombre) else "$yo → ${Toques.otro(yo)} ▾"
            contentDescription = if (yo == null) "Elegir quién usa este celular" else "Soy $yo. Cambiar mi nombre"
        }
        findViewById<TextView>(R.id.destinatario).text = if (yo == null) "Un toque de cariño" else "Un toque para ${Toques.otro(yo)}"
        findViewById<Button>(R.id.elegir_toque).apply {
            text = "${Toques.preferido(this@MainActivity).frase} ▾"
            contentDescription = "Mensaje: ${Toques.preferido(this@MainActivity).frase}. Cambiar mensaje"
        }
        mostrarEnvio()
    }

    private fun elegirToque() {
        val tipos = TipoToque.elegibles
        AlertDialog.Builder(this)
            .setTitle("Tu mensaje · también en el widget")
            .setSingleChoiceItems(tipos.map { it.frase }.toTypedArray(), tipos.indexOf(Toques.preferido(this))) { dialogo, i ->
                Toques.elegirTipo(this, tipos[i])
                ocultarResultado = true
                mostrarQuienEres()
                ContadorWidget.actualizarTodos(this)
                dialogo.dismiss()
            }
            .setNegativeButton("Cancelar", null)
            .show()
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
        findViewById<View>(R.id.elegir_toque).isEnabled = !ocupado
        findViewById<View>(R.id.identidad).isEnabled = !ocupado
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
        val temas = arrayOf("Enviar y responder", "Poner un widget", "Mis fotos", "Mi nombre")
        val pasos = arrayOf(
            getString(R.string.ayuda_toque) + "\n\nPara responder, toca ‘Un beso de vuelta’ en la notificación.",
            "Toca ‘Añadir widget’ y elige contador o carrusel.\n\nSi no aparece la ventana, mantén pulsado el inicio del celular → Widgets → ROMMICINES.\n\nPara cambiar el diseño del contador (Cielo, Neón, Perla…): ‘Añadir widget’ → ‘Cambiar el diseño del contador’, o mantén presionado el widget y toca el lápiz.",
            "Toca ‘Añadir fotos’ para elegir imágenes. Toca una foto para verla o quitarla del carrusel. Después puedes usar ‘Deshacer’.",
            "Toca tu nombre bajo ROMMICINES para cambiarlo. Elige un nombre distinto en cada celular."
        )
        AlertDialog.Builder(this).setTitle("¿En qué te ayudo?")
            .setItems(temas) { _, i ->
                AlertDialog.Builder(this).setTitle(temas[i]).setMessage(pasos[i])
                    .setPositiveButton("Entendido", null).show()
            }
            .setNegativeButton("Cerrar", null).show()
    }

    private fun recargar() {
        val fotos = Fotos.lista(this)
        adaptador.fotos = if (todasLasFotos) fotos else fotos.take(6)
        findViewById<TextView>(R.id.sin_fotos).visibility = if (fotos.isEmpty()) View.VISIBLE else View.GONE
        findViewById<Button>(R.id.ver_fotos).apply {
            visibility = if (fotos.size > 6) View.VISIBLE else View.GONE
            text = if (todasLasFotos) "Ver menos" else "Ver las ${fotos.size} fotos"
        }
        findViewById<Button>(R.id.agregar).apply {
            isEnabled = !Fotos.importando
            text = if (Fotos.importando) "Guardando…" else getString(R.string.anadir_fotos)
        }
        mostrarEstadoFotos()
    }

    private inner class Adaptador : BaseAdapter() {
        var fotos = emptyList<File>()
            set(value) { field = value; notifyDataSetChanged() }
        private val miniaturas = LruCache<String, Bitmap>(60)
        override fun getCount() = fotos.size
        override fun getItem(position: Int) = fotos[position]
        override fun getItemId(position: Int) = fotos[position].name.hashCode().toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val vista = convertView as? Cuadrada ?: Cuadrada(parent.context)
            val foto = fotos[position]
            vista.setImageBitmap(miniaturas.get(foto.name) ?: Fotos.reducida(foto, 360)?.also { miniaturas.put(foto.name, it) })
            vista.contentDescription = "Recuerdo ${position + 1}. Ver foto"
            return vista
        }
    }

    private class Cuadrada(context: Context) : ImageView(context) {
        init {
            scaleType = ScaleType.CENTER_CROP
            setBackgroundColor(context.getColor(R.color.velvet_dark))
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, 8 * resources.displayMetrics.density)
                }
            }
        }
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            super.onMeasure(widthMeasureSpec, widthMeasureSpec)
        }
    }

    private companion object {
        const val PEDIR_FOTOS = 7
        const val PEDIR_AVISOS = 8
    }
}
