package com.roguelito.rommicines

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import java.time.OffsetDateTime

/**
 * 📅 El instante del "sí": el mismo OFFICIAL_YES_DATE de src/config.ts.
 * Si algún día cambia allí, hay que cambiarlo aquí también.
 */
object Juntos {
    /** La página (SITE_URL de src/config.ts). Los widgets la abren como acceso directo. */
    const val PAGINA = "https://mike37jet.github.io/declaratoria/"

    val DESDE: Long = OffsetDateTime.parse("2026-10-09T17:25:15-05:00").toInstant().toEpochMilli()

    const val HORA = 60 * 60 * 1000L
    const val DIA = 24 * HORA

    class Tiempo(val dias: Long, val horas: Long, val msEnLaHora: Long) {
        /** Milisegundos que faltan para que cambie el número de horas. */
        val faltaParaLaHora get() = HORA - msEnLaHora
    }

    fun ahora(ahora: Long = System.currentTimeMillis()): Tiempo {
        val pasado = (ahora - DESDE).coerceAtLeast(0)
        return Tiempo(pasado / DIA, (pasado % DIA) / HORA, pasado % HORA)
    }

    /** Lo que se dispara al tocar un acceso directo a la página. */
    fun abrirPagina(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 2,
        Intent(Intent.ACTION_VIEW, Uri.parse(PAGINA)),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun texto(t: Tiempo): String {
        val seg = t.msEnLaHora / 1000
        val dias = if (t.dias == 1L) "día" else "días"
        return "%d %s · %02d:%02d:%02d".format(t.dias, dias, t.horas, seg / 60, seg % 60)
    }
}
