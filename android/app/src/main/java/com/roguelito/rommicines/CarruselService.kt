package com.roguelito.rommicines

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import java.io.File

class CarruselService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = Fabrica(applicationContext)
}

private class Fabrica(private val context: Context) : RemoteViewsService.RemoteViewsFactory {
    private var fotos = emptyList<File>()

    override fun onCreate() {}

    override fun onDataSetChanged() {
        fotos = Fotos.lista(context)
    }

    override fun onDestroy() {}

    override fun getCount() = fotos.size

    override fun getViewAt(position: Int): RemoteViews {
        val vista = RemoteViews(context.packageName, R.layout.widget_carrusel_foto)
        val foto = fotos.getOrNull(position) ?: return vista
        // Cada foto cruza al launcher por binder (límite ~1 MB): 560 px en
        // RGB_565 pesa menos de medio mega y se ve bien en un widget.
        Fotos.reducida(foto, 560, Bitmap.Config.RGB_565)?.let { vista.setImageViewBitmap(R.id.foto, it) }
        vista.setOnClickFillInIntent(R.id.foto, Intent())
        return vista
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount() = 1

    override fun getItemId(position: Int) = fotos.getOrNull(position)?.name?.hashCode()?.toLong() ?: position.toLong()

    override fun hasStableIds() = true
}
