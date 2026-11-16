package com.roguelito.rommicines

import android.content.Context
import android.util.Base64
import android.util.Log
import org.json.JSONObject
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.URL
import java.net.SocketTimeoutException
import java.net.URLEncoder
import java.net.UnknownHostException
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.net.ssl.SSLException

/**
 * 💗 Toques: un corazón que uno manda desde su widget y le llega al otro.
 *
 * Los dos celulares escuchan el mismo tema de Firebase Cloud Messaging. Para
 * mandar, la app firma un JWT con la llave de la cuenta de servicio (assets/
 * firebase-llave.json), lo cambia por un token de Google y publica en el tema.
 * Cada celular ignora sus propios toques.
 */
object Toques {
    const val TEMA = "toques"
    const val ROGUELITO = "Roguelito"
    const val MIMINA = "Mimina"

    private const val ALCANCE = "https://www.googleapis.com/auth/firebase.messaging"

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

    /** Publica [datos] en el tema de los dos celulares. Hace red. Null si salió bien, o el motivo. */
    fun publicar(context: Context, datos: Map<String, String>, ttl: String = "3600s"): String? = try {
        val mensaje = JSONObject().put(
            "message",
            JSONObject()
                .put("topic", TEMA)
                .put("data", JSONObject(datos))
                .put("android", JSONObject().put("priority", "high").put("ttl", ttl)),
        )
        val llave = llave(context)
        val url = "https://fcm.googleapis.com/v1/projects/${llave.getString("project_id")}/messages:send"
        // El token se puede pedir de nuevo sin riesgo; el envío solo se repite si no llegó a salir.
        val token = reintentando(cualquiera) { tokenDeAcceso(context, llave) }
        val (codigo, respuesta) = reintentando(noLlegoASalir) { post(url, mensaje.toString(), "application/json", token) }
        if (codigo != 200) Log.w("Toques", "FCM respondió $codigo: $respuesta")
        if (codigo == 200) null else "Firebase respondió $codigo"
    } catch (e: Exception) {
        Log.w("Toques", "No se pudo publicar en FCM", e)
        motivo(e)
    }

    private val cualquiera: (Exception) -> Boolean = { true }

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
        e is IllegalStateException && e.message?.startsWith("Google no dio token") == true ->
            "Google no aceptó la llave (revisa la hora del celular)"
        else -> "error inesperado"
    }

    private fun llave(context: Context) =
        JSONObject(context.assets.open("firebase-llave.json").bufferedReader().use { it.readText() })

    private var token: String? = null
    private var tokenVence = 0L

    /** Token OAuth de Google (dura una hora), a partir de un JWT firmado con la llave. */
    @Synchronized
    private fun tokenDeAcceso(context: Context, llave: JSONObject): String {
        token?.let { if (System.currentTimeMillis() < tokenVence) return it }

        val ahora = System.currentTimeMillis() / 1000
        val cabecera = JSONObject().put("alg", "RS256").put("typ", "JWT")
        val datos = JSONObject()
            .put("iss", llave.getString("client_email"))
            .put("scope", ALCANCE)
            .put("aud", llave.getString("token_uri"))
            .put("iat", ahora)
            .put("exp", ahora + 3600)
        val sinFirma = b64(cabecera.toString().toByteArray()) + "." + b64(datos.toString().toByteArray())

        val pem = llave.getString("private_key")
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\\s".toRegex(), "")
        val privada = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(Base64.decode(pem, Base64.DEFAULT)))
        val firma = Signature.getInstance("SHA256withRSA").run {
            initSign(privada)
            update(sinFirma.toByteArray())
            sign()
        }
        val jwt = sinFirma + "." + b64(firma)

        val cuerpo = "grant_type=" + URLEncoder.encode("urn:ietf:params:oauth:grant-type:jwt-bearer", "UTF-8") + "&assertion=" + jwt
        val (codigo, respuesta) = post(llave.getString("token_uri"), cuerpo, "application/x-www-form-urlencoded", null)
        check(codigo == 200) { "Google no dio token ($codigo): $respuesta" }
        val json = JSONObject(respuesta)
        tokenVence = System.currentTimeMillis() + (json.getLong("expires_in") - 60) * 1000
        return json.getString("access_token").also { token = it }
    }

    private fun b64(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

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
