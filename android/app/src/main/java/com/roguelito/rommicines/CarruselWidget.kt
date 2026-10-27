package com.roguelito.rommicines

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.RemoteViews

/**
 * Widget carrusel: las fotos pasan solas cada pocos segundos (AdapterViewFlipper).
 * Tocar una foto abre la app para agregar más; el botoncito abre la página.
 */
class CarruselWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { manager.updateAppWidget(it, vistas(context, it)) }
    }

    companion object {
        private fun vistas(context: Context, id: Int): RemoteViews {
            val servicio = Intent(context, CarruselService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                // Un data distinto por widget para que Android no los mezcle.
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            val abrirApp = Intent(context, MainActivity::class.java)
            // La plantilla de clics de una colección tiene que ser mutable.
            val mutable = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
            val plantilla = PendingIntent.getActivity(context, 1, abrirApp, PendingIntent.FLAG_UPDATE_CURRENT or mutable)
            val abrir = PendingIntent.getActivity(
                context, 0, abrirApp, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            return RemoteViews(context.packageName, R.layout.widget_carrusel).apply {
                setRemoteAdapter(R.id.flipper, servicio)
                setEmptyView(R.id.flipper, R.id.vacio)
                setPendingIntentTemplate(R.id.flipper, plantilla)
                setOnClickPendingIntent(R.id.vacio, abrir)
                setOnClickPendingIntent(R.id.ir_pagina, Juntos.abrirPagina(context))
            }
        }

        /** Avisa a todos los carruseles que la lista de fotos cambió. */
        fun refrescar(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, CarruselWidget::class.java))
            manager.notifyAppWidgetViewDataChanged(ids, R.id.flipper)
        }
    }
}
