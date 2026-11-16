package com.roguelito.rommicines.libreta

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.roguelito.rommicines.R
import com.roguelito.rommicines.Toques
import java.util.concurrent.TimeUnit

/** Lo que pasó en la libreta y merece avisarle al otro. */
enum class Evento(val codigo: String) {
    PLAN("plan"), PROPUESTA("propuesta"), FIRMA("firma"), HECHO("hecho"), RECUERDO("recuerdo");

    companion object {
        fun de(codigo: String?) = entries.firstOrNull { it.codigo == codigo }
    }
}

/** Un aviso de la libreta tal como viaja por FCM (todo en texto). */
data class AvisoLibreta(val pareja: String, val de: String, val quien: String, val evento: Evento, val plan: String, val texto: String) {
    fun datos(): Map<String, String> = mapOf(
        "clase" to CLASE, "pareja" to pareja, "de" to de, "quien" to quien,
        "evento" to evento.codigo, "plan" to plan, "texto" to texto,
    )

    /** Título y texto de la notificación, en palabras de la libreta. */
    fun titulo(): String = when (evento) {
        Evento.PLAN -> "✏️ $quien escribió en la libreta"
        Evento.PROPUESTA -> "📎 $quien te propone algo"
        Evento.FIRMA -> "✍️ $quien firmó tu propuesta"
        Evento.HECHO -> "¡Lo hicieron!"
        Evento.RECUERDO -> "📷 $quien pegó una foto en el álbum"
    }

    fun cuerpo(): String = when (evento) {
        Evento.PROPUESTA -> "«$texto» · Ábrela para firmarla"
        Evento.HECHO -> "$quien tachó «$texto»"
        else -> "«$texto»"
    }

    companion object {
        // Va en un campo propio y sin "nombre": así un APK viejo no lo confunde con un toque.
        const val CLASE = "libreta"

        fun esDeLibreta(datos: Map<String, String>) = datos["clase"] == CLASE

        /** El aviso, si es para este celular: de su misma libreta y no mandado por él mismo. */
        fun paraEsteCelular(datos: Map<String, String>, miPareja: String?, miId: String): AvisoLibreta? {
            if (!esDeLibreta(datos)) return null
            val pareja = datos["pareja"]?.takeIf { it.isNotBlank() && it == miPareja } ?: return null
            val de = datos["de"]?.takeIf { it.isNotBlank() && it != miId } ?: return null
            val evento = Evento.de(datos["evento"]) ?: return null
            return AvisoLibreta(pareja, de, datos["quien"].orEmpty().ifBlank { "Tu persona" }, evento,
                datos["plan"].orEmpty(), datos["texto"].orEmpty())
        }
    }
}

/**
 * Avisos tipo toque cuando el otro escribe, propone, firma, tacha o pega una foto.
 * Viajan por el mismo tema de FCM que los toques; si no hay internet esperan a que vuelva.
 */
object AvisosLibreta {
    private const val CANAL = "libreta"

    /** Si la libreta está abierta en este celular, no hace falta avisar (ya se ve en vivo). */
    @Volatile var libretaAbierta = false

    fun enviar(context: Context, evento: Evento, plan: String, texto: String) {
        val pareja = Libreta.pareja(context) ?: return
        val aviso = AvisoLibreta(pareja, Toques.idCelular(context), Libreta.nombre(context) ?: "", evento, plan, texto.take(120))
        val datos = Data.Builder().putAll(aviso.datos()).putLong("cuando", System.currentTimeMillis()).build()
        val tarea = OneTimeWorkRequest.Builder(Enviar::class.java)
            .setInputData(datos)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueue(tarea)
    }

    /** Lo llama ToquesService. Devuelve true si el mensaje era de la libreta (aunque no se muestre). */
    fun recibir(context: Context, datos: Map<String, String>): Boolean {
        if (!AvisoLibreta.esDeLibreta(datos)) return false
        val aviso = AvisoLibreta.paraEsteCelular(datos, Libreta.pareja(context), Toques.idCelular(context)) ?: return true
        if (!libretaAbierta) mostrar(context, aviso)
        return true
    }

    /** Al abrir la libreta, sus avisos ya se leyeron (los de toques se quedan). */
    fun limpiar(context: Context) {
        val gestor = context.getSystemService(NotificationManager::class.java)
        gestor.activeNotifications.filter { it.tag?.startsWith("libreta-") == true && it.tag != "libreta-recuerdo" }
            .forEach { gestor.cancel(it.tag, it.id) }
    }

    private fun mostrar(context: Context, aviso: AvisoLibreta) {
        val gestor = context.getSystemService(NotificationManager::class.java)
        gestor.createNotificationChannel(NotificationChannel(CANAL, "La libreta", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Cuando el otro escribe, propone, firma o tacha un plan"
            vibrationPattern = longArrayOf(0, 40) // un golpecito de lápiz, nada más
            enableVibration(true)
        })
        val destino = if (aviso.evento == Evento.RECUERDO)
            Intent(context, AlbumActivity::class.java).putExtra(AlbumActivity.IR_A, aviso.plan)
        else Intent(context, LibretaActivity::class.java)
        val abrir = PendingIntent.getActivity(context, aviso.plan.hashCode(), destino.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(context, CANAL)
            .setSmallIcon(R.drawable.ic_corazon)
            .setColor(context.getColor(R.color.tapa_tela))
            .setContentTitle(aviso.titulo())
            .setContentText(aviso.cuerpo())
            .setStyle(Notification.BigTextStyle().bigText(aviso.cuerpo()))
            .setContentIntent(abrir)
            .setAutoCancel(true)
            .build()
        // Un aviso por plan y evento: si llega repetido (reintento), reemplaza al anterior.
        gestor.notify("libreta-${aviso.evento.codigo}", aviso.plan.hashCode(), n)
    }

    class Enviar(context: Context, parametros: WorkerParameters) : Worker(context, parametros) {
        override fun doWork(): Result {
            // Un aviso de hace más de un día ya no es noticia.
            if (System.currentTimeMillis() - inputData.getLong("cuando", 0) > 24 * 3600_000L) return Result.success()
            val datos = inputData.keyValueMap.filterValues { it is String }.mapValues { it.value as String }
            val motivo = Toques.publicar(applicationContext, datos, ttl = "86400s")
            // Repetir es seguro: el aviso duplicado reemplaza al mismo en el otro celular.
            return if (motivo == null) Result.success() else if (runAttemptCount < 5) Result.retry() else Result.failure()
        }
    }
}
