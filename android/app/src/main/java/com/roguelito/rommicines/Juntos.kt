package com.roguelito.rommicines

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

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

    /** Ecuador no cambia de hora: los «cumplemeses» caen siempre a la misma hora del sí. */
    private val ZONA = ZoneOffset.ofHours(-5)

    /**
     * El mes juntos que va corriendo: [cumplidos] meses ya, [dia] días dentro de este mes
     * (0 = hoy se cumple), de [diasDelMes] en total, y cuántos días [faltan] para el siguiente.
     * Lo usan el anillo, la barra y los puntos de los diseños nuevos.
     */
    class Mes(val cumplidos: Int, val dia: Int, val diasDelMes: Int, val faltan: Int, val avance: Float) {
        val texto: String get() = when {
            dia == 0 && cumplidos > 0 -> "hoy cumplen $cumplidos ${meses(cumplidos)} ♥\uFE0E"
            faltan <= 1 -> "mañana cumplen ${cumplidos + 1} ${meses(cumplidos + 1)}"
            else -> "faltan $faltan días para ${cumplidos + 1} ${meses(cumplidos + 1)}"
        }

        private fun meses(n: Int) = if (n == 1) "mes" else "meses"
    }

    /** Por fechas del calendario, como lo cuentan ellos: del 16 de noviembre al 9 de diciembre faltan 23 días. */
    fun mes(ahora: Long = System.currentTimeMillis()): Mes {
        val momento = Instant.ofEpochMilli(maxOf(ahora, DESDE)).atOffset(ZONA)
        val primero = Instant.ofEpochMilli(DESDE).atOffset(ZONA).toLocalDate()
        val hoy = momento.toLocalDate()
        val cumplidos = ChronoUnit.MONTHS.between(primero, hoy)
        val inicio = primero.plusMonths(cumplidos)
        val diasDelMes = ChronoUnit.DAYS.between(inicio, primero.plusMonths(cumplidos + 1)).toInt()
        val dia = ChronoUnit.DAYS.between(inicio, hoy).toInt()
        val delDia = momento.toLocalTime().toSecondOfDay() / 86400f
        return Mes(
            cumplidos = cumplidos.toInt(),
            dia = dia,
            diasDelMes = diasDelMes,
            faltan = diasDelMes - dia,
            avance = ((dia + delDia) / diasDelMes).coerceIn(0f, 1f),
        )
    }

    /** Lo que se dispara al tocar un acceso directo a la página. */
    /** Siempre baja el APK de la última versión publicada en GitHub (Releases → latest). */
    const val ACTUALIZACION = "https://github.com/Mike37Jet/declaratoria/releases/latest/download/ROMMICINES.apk"

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
