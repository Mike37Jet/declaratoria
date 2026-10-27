package com.roguelito.rommicines

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.Operation
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters

/** Conserva el envío al salir del widget o cerrar la cortina de notificaciones. */
class EnviarToqueWorker(context: Context, parametros: WorkerParameters) : Worker(context, parametros) {
    override fun doWork(): Result {
        val tipo = TipoToque.desde(inputData.getString("tipo"))
        val nombre = inputData.getString("nombre") ?: return Result.failure()
        val aviso = inputData.getString("aviso")
        // Un toque aplazado por Android no debe aparecer horas después.
        val vigente = System.currentTimeMillis() - inputData.getLong("cuando", 0) < 5 * 60_000
        val motivo = if (vigente) Toques.enviar(applicationContext, tipo, inputData.getString("para"))
            else "Android lo retrasó más de 5 minutos"
        val ok = motivo == null
        Toques.registrar(applicationContext, recibido = false, nombre = nombre, fallo = !ok, tipo = tipo, motivo = motivo)
        ContadorWidget.actualizarTodos(applicationContext, soloConPie = true)
        if (aviso != null) {
            ToquesService.resultadoBeso(applicationContext, aviso, nombre, ok)
        } else if (!inputData.getBoolean("silencioso", false)) {
            Handler(Looper.getMainLooper()).post {
                val texto = if (ok) "Enviado a $nombre" else "No se pudo enviar: $motivo."
                Toast.makeText(applicationContext, texto, Toast.LENGTH_SHORT).show()
            }
        }
        // No reintentar automáticamente: una respuesta de red perdida podría duplicar el toque.
        val resultado = Data.Builder().putString("tipo", tipo.codigo).putString("nombre", nombre)
            .putLong("cuando", System.currentTimeMillis()).putString("motivo", motivo).build()
        return if (ok) Result.success(resultado) else Result.failure(resultado)
    }

    companion object {
        const val ENVIO_MANUAL = "toque-widget"

        fun programar(context: Context, tipo: TipoToque, nombre: String, para: String? = null, aviso: String? = null, silencioso: Boolean = false): Operation {
            val datos = Data.Builder()
                .putString("tipo", tipo.codigo)
                .putString("nombre", nombre)
                .putString("para", para)
                .putString("aviso", aviso)
                .putLong("cuando", System.currentTimeMillis())
                .putBoolean("silencioso", silencioso)
                .build()
            val tarea = OneTimeWorkRequest.Builder(EnviarToqueWorker::class.java).setInputData(datos).build()
            // Ignorar pulsaciones repetidas mientras el mismo envío sigue en curso.
            return WorkManager.getInstance(context).enqueueUniqueWork(
                aviso?.let { "beso-$it" } ?: ENVIO_MANUAL, ExistingWorkPolicy.KEEP, tarea,
            )
        }
    }
}
