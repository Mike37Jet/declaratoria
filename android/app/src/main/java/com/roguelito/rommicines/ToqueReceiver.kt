package com.roguelito.rommicines

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import java.util.concurrent.Executor

/** Acciones explícitas del widget y del botón «Un beso de vuelta». */
class ToqueReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Toques.yo(context) == null) return
        val beso = intent.action == BESO
        if (!beso && intent.action != ENVIAR) return
        val aviso = if (beso) intent.getStringExtra("aviso") ?: return else null
        val nombre = if (beso) intent.getStringExtra("nombre") ?: return else Toques.otro(Toques.yo(context))
        val para = if (beso) intent.getStringExtra("para") ?: return else null
        if (aviso != null && !ToquesService.prepararBeso(context, aviso)) return

        val pendiente = goAsync()
        try {
            val operacion = EnviarToqueWorker.programar(
                context, if (beso) TipoToque.BESO else Toques.preferido(context), nombre, para, aviso,
            )
            operacion.result.addListener({
                try {
                    operacion.result.get()
                } catch (e: Exception) {
                    Log.w("Toques", "No se pudo programar el toque", e)
                    if (aviso != null) ToquesService.resultadoBeso(context, aviso, nombre, false)
                } finally {
                    pendiente.finish()
                }
            }, Executor { it.run() })
        } catch (e: Exception) {
            Log.w("Toques", "No se pudo programar el toque", e)
            if (aviso != null) ToquesService.resultadoBeso(context, aviso, nombre, false)
            pendiente.finish()
        }
    }

    companion object {
        private const val ENVIAR = "com.roguelito.rommicines.ENVIAR"
        private const val BESO = "com.roguelito.rommicines.BESO"

        fun enviar(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context, 1, Intent(context, ToqueReceiver::class.java).setAction(ENVIAR),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        fun beso(context: Context, aviso: String, nombre: String, para: String): PendingIntent = PendingIntent.getBroadcast(
            context, 5, Intent(context, ToqueReceiver::class.java)
                .setAction(BESO)
                .setData(Uri.parse("rommicines://beso/$aviso"))
                .putExtra("aviso", aviso).putExtra("nombre", nombre).putExtra("para", para),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
