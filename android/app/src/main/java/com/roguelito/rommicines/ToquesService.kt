package com.roguelito.rommicines

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.app.Notification
import android.os.Bundle
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import java.util.UUID

/** Recibe los toques del otro celular: actualiza el widget y avisa con un latido. */
class ToquesService : FirebaseMessagingService() {

    override fun onMessageReceived(mensaje: RemoteMessage) {
        val toque = MensajeToque.paraEsteCelular(mensaje.data, Toques.idCelular(this)) ?: return

        Toques.registrar(this, recibido = true, nombre = toque.nombre, tipo = toque.tipo)
        ContadorWidget.actualizarTodos(this, soloConPie = true)
        avisar(this, toque)
    }

    // Se escucha un tema, así que el token del celular no hace falta guardarlo.
    override fun onNewToken(token: String) {}

    companion object {
        private const val CANAL = "toques"
        private const val NOTIFICACION = 1
        private const val AVISO = "toque_aviso"
        private const val NOMBRE = "toque_nombre"
        private const val REMITENTE = "toque_remitente"
        private const val ESTADO = "toque_estado"

        fun crearCanal(context: Context) {
            val canal = NotificationChannel(CANAL, "Toques", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Cuando te mandan un corazón"
                enableVibration(true)
                // Un latido: pum-pum.
                vibrationPattern = longArrayOf(0, 90, 120, 160)
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(canal)
        }

        private fun avisar(context: Context, toque: MensajeToque) {
            crearCanal(context)
            val abrir = PendingIntent.getActivity(
                context, 3, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val id = UUID.randomUUID().toString()
            val puedeResponder = Toques.yo(context) != null
            val texto = when (toque.tipo) {
                TipoToque.TOQUE -> "Te mandó un toque"
                TipoToque.BESO -> "Te mando un beso"
                else -> toque.tipo.frase
            }
            val aviso = Notification.Builder(context, CANAL)
                .setSmallIcon(R.drawable.ic_corazon)
                .setColor(context.getColor(R.color.velvet))
                .setContentTitle("${toque.tipo.emoji} ${toque.nombre}")
                .setContentText(texto)
                .setStyle(Notification.BigTextStyle().bigText(texto))
                .setContentIntent(abrir)
                .setAutoCancel(true)
                .addExtras(Bundle().apply {
                    putString(AVISO, id)
                    putString(NOMBRE, toque.nombre)
                    putString(REMITENTE, toque.de)
                    putString(ESTADO, "recibido")
                })
            if (puedeResponder) aviso.addAction(accionBeso(context, id, toque.nombre, toque.de))
            publicar(context, aviso.build())
        }

        private fun accionBeso(context: Context, id: String, nombre: String, para: String) =
            Notification.Action.Builder(null, "💋 Un beso de vuelta", ToqueReceiver.beso(context, id, nombre, para)).build()

        private fun actual(context: Context, id: String): Notification? =
            context.getSystemService(NotificationManager::class.java).activeNotifications
                .firstOrNull { it.id == NOTIFICACION && it.notification.extras.getString(AVISO) == id }?.notification

        /** Quita la acción mientras se envía para evitar besos duplicados por doble pulsación. */
        @Synchronized
        fun prepararBeso(context: Context, id: String): Boolean {
            val actual = actual(context, id) ?: return false
            if (actual.extras.getString(ESTADO) !in listOf("recibido", "fallo")) return false
            val aviso = estado(context, actual, "💋 Un beso de vuelta", "Enviando un beso…", "enviando").build()
            publicar(context, aviso)
            return true
        }

        @Synchronized
        fun resultadoBeso(context: Context, id: String, nombre: String, ok: Boolean) {
            // No reemplazar un toque más nuevo ni recuperar un aviso descartado por el usuario.
            val actual = actual(context, id) ?: return
            val texto = if (ok) "" else "Revisa tu conexión y vuelve a intentarlo."
            val aviso = estado(context, actual,
                if (ok) "💋 Beso enviado a $nombre" else "No se pudo mandar el beso", texto,
                if (ok) "enviado" else "fallo")
            if (!ok) {
                val para = actual.extras.getString(REMITENTE) ?: return
                aviso.addAction(accionBeso(context, id, nombre, para))
            }
            publicar(context, aviso.build())
        }

        // Crear una notificación nueva evita que recoverBuilder conserve acciones antiguas.
        private fun estado(context: Context, anterior: Notification, titulo: String, texto: String, estado: String) =
            Notification.Builder(context, CANAL)
                .setSmallIcon(R.drawable.ic_corazon)
                .setColor(context.getColor(R.color.velvet))
                .setContentTitle(titulo)
                .setContentText(texto)
                .setStyle(Notification.BigTextStyle().bigText(texto))
                .setContentIntent(anterior.contentIntent)
                .setWhen(anterior.`when`)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .addExtras(Bundle().apply {
                    putString(AVISO, anterior.extras.getString(AVISO))
                    putString(NOMBRE, anterior.extras.getString(NOMBRE))
                    putString(REMITENTE, anterior.extras.getString(REMITENTE))
                    putString(ESTADO, estado)
                })

        private fun publicar(context: Context, aviso: Notification) {
            try {
                context.getSystemService(NotificationManager::class.java).notify(NOTIFICACION, aviso)
            } catch (e: SecurityException) {
                // Sin permiso de notificaciones: el widget igual muestra el toque.
            }
        }
    }
}
