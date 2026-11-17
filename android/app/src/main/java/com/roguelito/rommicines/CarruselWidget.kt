package com.roguelito.rommicines

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.RemoteViews
import com.roguelito.rommicines.libreta.LibretaWidget
import com.roguelito.rommicines.tablero.DibujoTablero
import com.roguelito.rommicines.tablero.TableroActivity
import java.util.concurrent.Executors

/**
 * El widget de fotos: el tablero de corcho con las fotos y notitas más recientes, tal como quedaron
 * acomodadas en la app. Es una sola imagen (un widget no deja arrastrar); tocarlo abre el tablero.
 *
 * Se sigue llamando CarruselWidget (antes era un carrusel) para que los widgets ya puestos no se pierdan.
 */
class CarruselWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = dibujar(context, ids)

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, opciones: Bundle) =
        dibujar(context, intArrayOf(id))

    private fun dibujar(context: Context, ids: IntArray) {
        val pendiente = goAsync()
        val app = context.applicationContext
        trabajo.execute {
            try { ids.forEach { actualizar(app, AppWidgetManager.getInstance(app), it) } } finally { pendiente.finish() }
        }
    }

    companion object {
        /** Dibujar el corcho y bajar las fotos toma su tiempo: fuera del hilo principal, de a uno. */
        private val trabajo = Executors.newSingleThreadExecutor()

        private fun actualizar(context: Context, manager: AppWidgetManager, id: Int) {
            try {
                val (ancho, alto) = LibretaWidget.tamaño(context, manager, id)
                val abrir = PendingIntent.getActivity(context, 30, Intent(context, TableroActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                val v = RemoteViews(context.packageName, R.layout.widget_tablero)
                v.setImageViewBitmap(R.id.tablero, DibujoTablero.widget(context, ancho, alto))
                v.setContentDescription(R.id.tablero, DibujoTablero.descripcion(context))
                v.setOnClickPendingIntent(R.id.tablero, abrir)
                manager.updateAppWidget(id, v)
            } catch (e: Exception) {
                Log.w("CarruselWidget", "No se pudo dibujar el tablero", e)
            }
        }

        /** Vuelve a dibujar todos los tableros (cambiaron las fotos o el acomodo). */
        fun refrescar(context: Context) {
            val app = context.applicationContext
            val manager = AppWidgetManager.getInstance(app)
            val ids = manager.getAppWidgetIds(ComponentName(app, CarruselWidget::class.java))
            if (ids.isEmpty()) return
            trabajo.execute { ids.forEach { actualizar(app, manager, it) } }
        }
    }
}
