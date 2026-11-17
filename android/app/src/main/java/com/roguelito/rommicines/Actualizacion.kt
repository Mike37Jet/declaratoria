package com.roguelito.rommicines

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/**
 * ¿Hay una versión nueva de la app? Se le pregunta a GitHub cuál es el último Release publicado
 * (solo el número; no se manda nada del celular) y se compara con la versión instalada.
 * El botón «Actualizar la app» solo aparece cuando la publicada es más nueva.
 */
object Actualizacion {
    private const val ULTIMO = "https://api.github.com/repos/Mike37Jet/declaratoria/releases/latest"
    /** No se pregunta más de una vez cada 3 horas (GitHub limita las consultas sin cuenta). */
    private const val CADA = 3 * 60 * 60 * 1000L

    private fun prefs(context: Context) = context.getSharedPreferences("actualizacion", Context.MODE_PRIVATE)

    fun instalada(context: Context): String? =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()

    /** La versión nueva que hay para bajar, o null si la app está al día (o todavía no se sabe). */
    fun nueva(context: Context): String? {
        val publicada = prefs(context).getString("publicada", null) ?: return null
        val mia = instalada(context) ?: return null
        return publicada.takeIf { esMasNueva(it, mia) }
    }

    /** Pregunta a GitHub en segundo plano y llama a [listo] (desde otro hilo) si se supo algo. */
    fun consultar(context: Context, listo: () -> Unit) {
        val p = prefs(context.applicationContext)
        val ahora = System.currentTimeMillis()
        if (ahora - p.getLong("consultada", 0) < CADA) return
        p.edit().putLong("consultada", ahora).apply()
        thread(name = "actualizacion") {
            try {
                val c = URL(ULTIMO).openConnection() as HttpURLConnection
                c.connectTimeout = 8000; c.readTimeout = 8000
                c.setRequestProperty("Accept", "application/vnd.github+json")
                c.setRequestProperty("User-Agent", "ROMMICINES")
                try {
                    if (c.responseCode != 200) return@thread
                    val etiqueta = JSONObject(c.inputStream.bufferedReader().use { it.readText() }).optString("tag_name")
                    val version = etiqueta.removePrefix("v").trim()
                    if (version.isEmpty()) return@thread
                    p.edit().putString("publicada", version).apply()
                    listo()
                } finally { c.disconnect() }
            } catch (e: Exception) {
                // Sin internet no pasa nada: se vuelve a intentar la próxima vez que se abra la app.
                p.edit().remove("consultada").apply()
                Log.i("Actualizacion", "No se pudo consultar la última versión: ${e.message}")
            }
        }
    }

    /** «1.10» es más nueva que «1.9»: se compara número por número. */
    fun esMasNueva(publicada: String, instalada: String): Boolean {
        fun partes(v: String) = v.split('.', '-').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        val a = partes(publicada); val b = partes(instalada)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
