package com.roguelito.rommicines

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import com.roguelito.rommicines.libreta.Libreta
import org.json.JSONObject
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.URL
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

/**
 * 💗 Toques: un corazón que uno manda desde su widget y le llega al otro.
 *
 * Los dos celulares escuchan el tema de su pareja en Firebase Cloud Messaging
 * (`pareja-<id de la libreta>`). Para mandar, la app se lo pide al servidor de avisos,
 * que guarda la llave de Firebase. Cada celular ignora sus propios toques.
 */
object Toques {
    /** El tema viejo, que escuchaba cualquiera con la app. Ya no se usa: solo se deja de escuchar. */
    const val TEMA_VIEJO = "toques"

    /** Servidor de avisos (Cloudflare Worker, carpeta `servidor/`). */
    const val SERVIDOR = "https://rommicines-avisos.rommicines.workers.dev/publicar"

    /** El tema que solo escuchan los dos celulares de una pareja. */
    fun tema(pareja: String) = "pareja-$pareja"
    const val ROGUELITO = "Roguelito"
    const val MIMINA = "Mimina"

    /**
     * Escucha el tema de la pareja y deja de escuchar el viejo. Sin libreta no hay nada que escuchar.
     * [listo] dice si la suscripción salió bien.
     */
    fun suscribir(context: Context, listo: (Boolean) -> Unit = {}) {
        val fcm = FirebaseMessaging.getInstance()
        fcm.unsubscribeFromTopic(TEMA_VIEJO)
        val pareja = Libreta.pareja(context) ?: return listo(true)
        fcm.subscribeToTopic(tema(pareja)).addOnCompleteListener { listo(it.isSuccessful) }
    }

    private fun prefs(context: Context) = context.getSharedPreferences("toques", Context.MODE_PRIVATE)

    /** Quién usa este celular: "Roguelito" o "Mimina" (null hasta que se elija en la app). */
    fun yo(context: Context): String? = prefs(context).getString("yo", null)

    fun elegir(context: Context, nombre: String) = prefs(context).edit().putString("yo", nombre).apply()

    fun otro(nombre: String?) = if (nombre == MIMINA) ROGUELITO else MIMINA

    fun preferido(context: Context): TipoToque =
        TipoToque.desde(prefs(context).getString("tipo_preferido", null))
            .takeIf { it in TipoToque.elegibles } ?: TipoToque.EXTRANO

    fun elegirTipo(context: Context, tipo: TipoToque) {
        require(tipo in TipoToque.elegibles)
        prefs(context).edit().putString("tipo_preferido", tipo.codigo).apply()
    }

    /** Identificador de este celular, para no recibir los toques propios. */
    fun idCelular(context: Context): String {
        val p = prefs(context)
        return p.getString("id", null) ?: UUID.randomUUID().toString().also { p.edit().putString("id", it).apply() }
    }

    /** El último toque (mandado o recibido), para mostrarlo en el widget. */
    class Evento(val recibido: Boolean, val nombre: String, val cuando: Long, val fallo: Boolean, val tipo: TipoToque, val motivo: String? = null)

    fun ultimo(context: Context): Evento? {
        val p = prefs(context)
        val cuando = p.getLong("ultimo_cuando", 0)
        if (cuando == 0L) return null
        return Evento(p.getBoolean("ultimo_recibido", false), p.getString("ultimo_nombre", "") ?: "", cuando,
            p.getBoolean("ultimo_fallo", false), TipoToque.desde(p.getString("ultimo_tipo", null)),
            p.getString("ultimo_motivo", null))
    }

    fun registrar(context: Context, recibido: Boolean, nombre: String, fallo: Boolean = false, tipo: TipoToque = TipoToque.TOQUE, motivo: String? = null) {
        prefs(context).edit()
            .putLong("ultimo_cuando", System.currentTimeMillis())
            .putBoolean("ultimo_recibido", recibido)
            .putString("ultimo_nombre", nombre)
            .putBoolean("ultimo_fallo", fallo)
            .putString("ultimo_motivo", motivo)
            .putString("ultimo_tipo", tipo.codigo)
            .apply()
        if (recibido && !fallo) prefs(context).edit()
            .putLong("recibido_cuando", System.currentTimeMillis())
            .putString("recibido_nombre", nombre)
            .putString("recibido_tipo", tipo.codigo)
            .apply()
    }

    /** El último toque que llegó del otro (aunque después hayas mandado uno), para la pantalla principal. */
    fun ultimoRecibido(context: Context): Evento? {
        val p = prefs(context)
        val cuando = p.getLong("recibido_cuando", 0)
        if (cuando == 0L) return null
        return Evento(true, p.getString("recibido_nombre", "") ?: "", cuando, false, TipoToque.desde(p.getString("recibido_tipo", null)))
    }

    /** «hace un momento», «hace 5 min», «hace 2 h», «ayer» o la fecha: siempre en español. */
    fun hace(cuando: Long, ahora: Long = System.currentTimeMillis()): String {
        val min = (ahora - cuando).coerceAtLeast(0) / 60_000
        return when {
            min < 1 -> "hace un momento"
            min < 60 -> "hace $min min"
            min < 24 * 60 -> "hace ${min / 60} h"
            min < 48 * 60 -> "ayer"
            else -> SimpleDateFormat("d 'de' MMMM", Locale.forLanguageTag("es")).format(Date(cuando))
        }
    }

    fun hora(cuando: Long): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(cuando))

    /**
     * Manda un toque al otro celular. Hace red: no llamar desde el hilo principal.
     * Devuelve null si salió bien, o el motivo del fallo en palabras para mostrarle a quien lo mandó.
     */
    fun enviar(context: Context, tipo: TipoToque = preferido(context), para: String? = null): String? {
        val yo = yo(context) ?: return "falta elegir quién usa este celular"
        return publicar(context, MensajeToque(idCelular(context), yo, tipo, para).datos())
    }

    /**
     * Publica [datos] en el tema de la pareja. Hace red. Null si salió bien, o el motivo.
     * La app no lleva la llave de Firebase: se lo pide al servidor de avisos (carpeta `servidor/`
     * del repo), que comprueba con la sesión del celular que es miembro de la libreta.
     */
    fun publicar(context: Context, datos: Map<String, String>, ttl: String = "3600s"): String? = try {
        val pareja = Libreta.pareja(context)
        val usuario = FirebaseAuth.getInstance().currentUser
        if (pareja == null || usuario == null) {
            "primero unan su libreta"
        } else {
            val cuerpo = JSONObject().put("pareja", pareja).put("datos", JSONObject(datos)).put("ttl", ttl).toString()
            fun intento(renovar: Boolean): Pair<Int, String> {
                val sesion = Tasks.await(usuario.getIdToken(renovar), 20, TimeUnit.SECONDS).token
                    ?: throw IllegalStateException("sin sesión")
                // El envío solo se repite si no llegó a salir: así no se duplica el toque.
                return reintentando(noLlegoASalir) { post(SERVIDOR, cuerpo, "application/json", sesion) }
            }
            var (codigo, respuesta) = intento(renovar = false)
            if (codigo == 401) intento(renovar = true).let { codigo = it.first; respuesta = it.second }
            if (codigo != 200) Log.w("Toques", "El servidor respondió $codigo: $respuesta")
            when (codigo) {
                200 -> null
                403 -> "este celular ya no está en su libreta"
                else -> "el servidor respondió $codigo"
            }
        }
    } catch (e: Exception) {
        Log.w("Toques", "No se pudo publicar", e)
        motivo(e)
    }

    /** Fallos en los que el mensaje seguro no llegó a Firebase: repetir no puede duplicar el toque. */
    private val noLlegoASalir: (Exception) -> Boolean = { e ->
        e is UnknownHostException || e is ConnectException ||
            (e is SocketTimeoutException && e.message?.contains("connect", ignoreCase = true) == true)
    }

    private fun <T> reintentando(repetible: (Exception) -> Boolean, intentos: Int = 3, bloque: () -> T): T {
        var n = 1
        while (true) {
            try {
                return bloque()
            } catch (e: Exception) {
                if (n >= intentos || !repetible(e)) throw e
                n++
                Thread.sleep(1_500)
            }
        }
    }

    internal fun motivo(e: Exception): String = when {
        e is UnknownHostException || e is ConnectException -> "sin conexión a internet"
        e is SocketTimeoutException -> "la conexión es muy lenta"
        e is SSLException -> "no se pudo abrir una conexión segura; revisa la hora del celular"
        e is java.util.concurrent.ExecutionException || e is java.util.concurrent.TimeoutException ->
            "no se pudo confirmar la sesión de la libreta"
        else -> "error inesperado"
    }

    private fun post(url: String, cuerpo: String, tipo: String, token: String?): Pair<Int, String> {
        val conexion = URL(url).openConnection() as HttpURLConnection
        try {
            conexion.requestMethod = "POST"
            conexion.connectTimeout = 10_000
            conexion.readTimeout = 10_000
            conexion.doOutput = true
            conexion.setRequestProperty("Content-Type", tipo)
            token?.let { conexion.setRequestProperty("Authorization", "Bearer $it") }
            conexion.outputStream.use { it.write(cuerpo.toByteArray()) }
            val codigo = conexion.responseCode
            val flujo = if (codigo in 200..299) conexion.inputStream else conexion.errorStream
            return codigo to (flujo?.bufferedReader()?.use { it.readText() } ?: "")
        } finally {
            conexion.disconnect()
        }
    }
}
