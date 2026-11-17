package com.roguelito.rommicines

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.SizeF
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews

/**
 * Widget contador. Los widgets no se pueden redibujar cada segundo, así que:
 * - minutos y segundos los corre el propio launcher con un Chronometer
 *   (no gasta batería y nunca pasa de 59:59 porque arranca en cada hora);
 * - días y horas se reescriben con una alarma justo cuando cambia la hora.
 *
 * Hay tres diseños según el tamaño: completo (4×2), franja (3×1, 4×1: las mismas
 * cajitas en una fila) y compacto (una línea + minutos:segundos). Cada widget además
 * tiene su [Tema] (Cine, Cielo, Neón…), que se elige en [ElegirTemaActivity].
 * En el compacto, tocar el contador manda un toque y el ♥ abre la página.
 * En el grande, el botón ♥ manda el toque (ver [Toques]).
 */
class ContadorWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        actualizarTodos(context)
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, opciones: Bundle) {
        actualizarTodos(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            CAMBIO_DE_HORA,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
            -> actualizarTodos(context)
        }
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        Temas.olvidar(context, ids)
    }

    override fun onDisabled(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(alarma(context))
    }

    /** Los tres tamaños, para las vistas previas de [ElegirTemaActivity]. */
    enum class Vista { COMPLETO, FRANJA, MINI }

    companion object {
        private const val CAMBIO_DE_HORA = "com.roguelito.rommicines.CAMBIO_DE_HORA"

        /** El diseño grande muestra el último toque durante un día. */
        private const val TOQUE_VISIBLE = Juntos.DIA

        /**
         * [soloConPie]: tras un toque enviado o recibido solo cambia el pie del diseño
         * completo. Reescribir los demás los vuelve a inflar y se ve un parpadeo.
         */
        fun actualizarTodos(context: Context, soloConPie: Boolean = false) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, ContadorWidget::class.java))
                .filter { !soloConPie || muestraPie(manager, it) }
            if (ids.isEmpty()) return

            val t = Juntos.ahora()
            ids.forEach { manager.updateAppWidget(it, vistas(context, manager, it, t)) }
            programar(context, System.currentTimeMillis() + t.faltaParaLaHora + 300)
        }

        /** Solo un widget: al cambiarle el diseño, los demás no se vuelven a inflar (parpadearían). */
        fun actualizar(context: Context, id: Int) {
            val manager = AppWidgetManager.getInstance(context)
            val t = Juntos.ahora()
            manager.updateAppWidget(id, vistas(context, manager, id, t))
            programar(context, System.currentTimeMillis() + t.faltaParaLaHora + 300)
        }

        private enum class Diseño { MINI, FRANJA, COMPLETO }

        /** El diseño que el launcher está mostrando, según el tamaño actual del widget. */
        private fun diseñoActual(context: Context, manager: AppWidgetManager, id: Int): Diseño {
            val o = manager.getAppWidgetOptions(id)
            @Suppress("DEPRECATION")
            val tamaños = o.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
            val vertical = context.resources.configuration.orientation != android.content.res.Configuration.ORIENTATION_LANDSCAPE
            val (ancho, alto) = tamaños?.takeIf { it.isNotEmpty() }
                ?.let { if (vertical) it.first() else it.last() }?.let { it.width to it.height }
                ?: (o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).toFloat() to
                    o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT).toFloat())
            return when {
                ancho >= COMPLETO.width && alto >= COMPLETO.height -> Diseño.COMPLETO
                ancho >= FRANJA.width && alto >= FRANJA.height -> Diseño.FRANJA
                else -> Diseño.MINI
            }
        }

        /**
         * Latido del corazón al recibir un toque: crece y se enciende dos veces. Un widget no se
         * anima solo, así que son unas pocas actualizaciones en menos de un segundo. Cada una usa
         * el mismo diseño, que el launcher aplica sin volver a crear el widget; las parciales
         * las ignora en los widgets con varios diseños. El completo no late: su pie ya muestra
         * el toque. [ritmo] alarga las pausas (solo para pruebas). Hace pausas: no llamar desde
         * el hilo principal.
         */
        fun latir(context: Context, ritmo: Long = 1) {
            val manager = AppWidgetManager.getInstance(context)
            val vivos = manager.getAppWidgetIds(ComponentName(context, ContadorWidget::class.java))
                .filter { diseñoActual(context, manager, it) != Diseño.COMPLETO }
            if (vivos.isEmpty()) return
            listOf(true to 220L, false to 120L, true to 220L, false to 0L).forEach { (grande, espera) ->
                vivos.forEach { manager.updateAppWidget(it, vistas(context, manager, it, Juntos.ahora(), grande)) }
                if (espera > 0) Thread.sleep(espera * ritmo)
            }
        }

        /** Tamaño (en dp) desde el que caben las tres cajitas con título. */
        private val COMPLETO = SizeF(250f, 100f)
        /** Ancho pero bajito (3×1, 4×1): las tres cajitas en una fila. */
        private val FRANJA = SizeF(230f, 56f)
        private val MINI = SizeF(100f, 40f)

        private fun vistas(context: Context, manager: AppWidgetManager, id: Int, t: Juntos.Tiempo, latido: Boolean = false): RemoteViews {
            val tema = Temas.de(context, id)
            // Android 12+ elige solo el diseño según el tamaño, incluso mientras se redimensiona.
            if (Build.VERSION.SDK_INT >= 31) {
                return RemoteViews(mapOf(
                    MINI to mini(context, t, tema, latido),
                    FRANJA to franja(context, t, tema, latido),
                    COMPLETO to completo(context, t, tema),
                ))
            }
            val opciones = manager.getAppWidgetOptions(id)
            val ancho = opciones.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
            val alto = opciones.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
            return when {
                ancho in 1 until FRANJA.width.toInt() || alto in 1 until FRANJA.height.toInt() -> mini(context, t, tema, latido)
                ancho in 1 until COMPLETO.width.toInt() || alto in 1 until COMPLETO.height.toInt() -> franja(context, t, tema, latido)
                else -> completo(context, t, tema)
            }
        }

        /**
         * Para mostrar un diseño dentro de la app. Va sin acciones: tocar la vista previa
         * no debe mandar un toque de verdad ni abrir la página.
         */
        fun vistaPrevia(context: Context, tema: Tema, vista: Vista): RemoteViews {
            val t = Juntos.ahora()
            return when (vista) {
                Vista.COMPLETO -> completo(context, t, tema, acciones = false)
                Vista.FRANJA -> franja(context, t, tema, acciones = false)
                Vista.MINI -> mini(context, t, tema, acciones = false)
            }
        }

        /**
         * ¿Puede este widget estar mostrando el diseño completo? El launcher da los
         * tamaños posibles (vertical y horizontal); si no los da, se actualiza por si acaso.
         */
        private fun muestraPie(manager: AppWidgetManager, id: Int): Boolean {
            val o = manager.getAppWidgetOptions(id)
            @Suppress("DEPRECATION")
            val tamaños = o.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
            if (tamaños.isNullOrEmpty()) return true
            return tamaños.any { it.width >= COMPLETO.width && it.height >= COMPLETO.height }
        }

        /** Días y horas, iguales en el completo y la franja. Los ids que un diseño no tiene se ignoran. */
        private fun RemoteViews.numeros(t: Juntos.Tiempo, tema: Tema) {
            setTextViewText(R.id.dias, t.dias.toString())
            setTextViewText(R.id.dias_etiqueta, tema.dias(t.dias))
            setTextViewText(R.id.horas, tema.numeroHoras(t.horas))
            setTextViewText(R.id.horas_etiqueta, tema.horas(t.horas))
        }

        private fun completo(context: Context, t: Juntos.Tiempo, tema: Tema, acciones: Boolean = true) =
            RemoteViews(context.packageName, tema.completo).apply {
                numeros(t, tema)
                // El launcher reaplica sobre la vista que ya tiene: el pie se declara siempre,
                // o el último toque se quedaría para siempre. Medianoche y Perla no tienen pie fijo.
                val toque = textoToque(context, TOQUE_VISIBLE)
                if (toque != null || tema.pie != 0) {
                    setTextViewText(R.id.pie, toque ?: context.getString(tema.pie))
                    setViewVisibility(R.id.pie, View.VISIBLE)
                } else {
                    setViewVisibility(R.id.pie, View.GONE)
                }
                // Donde el pie va junto a la meta (Puntos), el toque reciente ocupa su lugar.
                setViewVisibility(R.id.meta, if (toque != null && tema.pie == 0) View.GONE else View.VISIBLE)
                reloj(t, tema.formatoReloj(t.horas))
                delMes(context, tema, Vista.COMPLETO)
                if (acciones) {
                    setOnClickPendingIntent(R.id.raiz, Juntos.abrirPagina(context))
                    setOnClickPendingIntent(R.id.enviar_toque, botonToque(context))
                }
                setContentDescription(R.id.enviar_toque, Toques.preferido(context).frase)
            }

        private fun franja(context: Context, t: Juntos.Tiempo, tema: Tema, latido: Boolean = false, acciones: Boolean = true) =
            RemoteViews(context.packageName, tema.franja).apply {
                numeros(t, tema)
                reloj(t)
                delMes(context, tema, Vista.FRANJA, latido)
                if (acciones) {
                    setOnClickPendingIntent(R.id.raiz, Juntos.abrirPagina(context))
                    setOnClickPendingIntent(R.id.enviar_toque, botonToque(context))
                }
                setContentDescription(R.id.enviar_toque, Toques.preferido(context).frase)
                // Se declara siempre: una actualización no revierte lo que ya no menciona.
                latir(context, R.id.enviar_toque, tema, latido, tema.corazon, tema.latidoEnBoton, 16f, 26f, tema.escalaBoton)
            }

        private fun mini(context: Context, t: Juntos.Tiempo, tema: Tema, latido: Boolean = false, acciones: Boolean = true) =
            RemoteViews(context.packageName, tema.mini).apply {
                val linea = "%d %s · %02d h".format(t.dias, if (t.dias == 1L) "día" else "días", t.horas)
                setTextViewText(R.id.linea, linea)
                numeros(t, tema)
                reloj(t)
                delMes(context, tema, Vista.MINI, latido)
                if (acciones) {
                    setOnClickPendingIntent(R.id.raiz, botonToque(context))
                    setOnClickPendingIntent(R.id.abrir_pagina, Juntos.abrirPagina(context))
                }
                setContentDescription(R.id.raiz, "Enviar: ${Toques.preferido(context).frase}")
                latir(context, R.id.abrir_pagina, tema, latido, tema.corazonSuelto, tema.latido, 13f, 21f, tema.escalaSuelto)
            }

        /**
         * Un cuadro del latido: el corazón crece, se llena (♡ → ♥) y toma el color del tema.
         * En el Neón además el tubo del borde destella en cian.
         */
        private fun RemoteViews.latir(
            context: Context, corazon: Int, tema: Tema, latido: Boolean,
            color: Int, colorLatido: Int, normal: Float, grande: Float, escala: Float = 1f,
        ) {
            setTextViewTextSize(corazon, TypedValue.COMPLEX_UNIT_SP, (if (latido) grande else normal) * escala)
            setTextColor(corazon, context.getColor(if (latido) colorLatido else color))
            setTextViewText(corazon, if (latido) CORAZON else tema.simbolo)
            if (tema.destello != 0) {
                setInt(R.id.raiz, "setBackgroundResource", if (latido) tema.destello else tema.marco)
            }
        }

        /**
         * Lo que depende del mes juntos: el texto de la meta, la barra y el anillo o los puntos.
         * Los ids que un diseño no tiene se ignoran. En el latido, el punto de hoy crece.
         */
        private fun RemoteViews.delMes(context: Context, tema: Tema, vista: Vista, latido: Boolean = false) {
            val mes = Juntos.mes()
            setTextViewText(R.id.meta, mes.texto)
            setProgressBar(R.id.barra, 1000, (mes.avance * 1000).toInt(), false)
            when (tema.grafico) {
                Grafico.ANILLO -> setImageViewBitmap(R.id.grafico, Graficos.anillo(context, mes.avance))
                Grafico.PUNTOS -> setImageViewBitmap(
                    R.id.grafico,
                    Graficos.puntos(context, mes, columnas = if (vista == Vista.MINI) 8 else 16, latido = latido),
                )
                Grafico.NINGUNO -> {}
            }
        }

        private fun RemoteViews.reloj(t: Juntos.Tiempo, formato: String? = null) {
            setChronometer(R.id.minseg, SystemClock.elapsedRealtime() - t.msEnLaHora, formato, true)
        }

        /** Si todavía no se eligió quién usa el celular, abre la app antes de enviar. */
        private fun botonToque(context: Context): PendingIntent =
            if (Toques.yo(context) == null) {
                PendingIntent.getActivity(
                    context, 4, Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            } else {
                ToqueReceiver.enviar(context)
            }

        private fun textoToque(context: Context, visible: Long): String? {
            val e = Toques.ultimo(context) ?: return null
            if (System.currentTimeMillis() - e.cuando > visible) return null
            val hora = Toques.hora(e.cuando)
            return when {
                e.fallo -> "No se pudo mandar el toque${e.motivo?.let { " ($it)" } ?: ""}"
                e.recibido -> "${e.tipo.recibido(e.nombre)} · $hora"
                else -> "${e.tipo.etiqueta} → ${e.nombre} · $hora"
            }
        }

        /** ¿Puede la app poner alarmas exactas? (Android 12+ lo deja al usuario). */
        fun alarmasExactas(context: Context): Boolean =
            Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

        private fun programar(context: Context, cuando: Long) {
            val alarmas = context.getSystemService(AlarmManager::class.java)
            // Con la pantalla apagada una alarma sin "wakeup" espera hasta que alguien
            // despierte el celular, y mientras tanto el Chronometer del launcher sigue
            // contando más allá de 59:59 (1:00:01…). Despertar una vez por hora es barato
            // y deja el widget ya corregido cuando se mira.
            if (alarmasExactas(context)) {
                alarmas.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cuando, alarma(context))
            } else {
                alarmas.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cuando, alarma(context))
            }
        }

        private fun alarma(context: Context) = PendingIntent.getBroadcast(
            context, 0,
            Intent(context, ContadorWidget::class.java).setAction(CAMBIO_DE_HORA),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
