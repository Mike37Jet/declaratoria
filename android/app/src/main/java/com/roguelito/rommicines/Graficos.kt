package com.roguelito.rommicines

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF

/**
 * Lo que un widget no sabe dibujar solo (un arco con puntas redondas, una cuadrícula de puntos)
 * se pinta aquí como imagen. Se redibuja con cada actualización del contador (una vez por hora)
 * y en cada cuadro del latido.
 */
object Graficos {

    /** Anillo tipo Actividad: pista apagada y el avance del mes juntos, con puntas redondas. */
    fun anillo(context: Context, avance: Float, latido: Boolean = false): Bitmap {
        val lado = 200
        val grosor = if (latido) 30f else 24f
        val bmp = Bitmap.createBitmap(lado, lado, Bitmap.Config.ARGB_8888)
        val lienzo = Canvas(bmp)
        val pincel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = grosor
            strokeCap = Paint.Cap.ROUND
        }
        val m = 16f
        val caja = RectF(m, m, lado - m, lado - m)
        pincel.color = context.getColor(R.color.anillo_pista)
        lienzo.drawArc(caja, 0f, 360f, false, pincel)
        pincel.color = context.getColor(R.color.anillo_rosa)
        // Aunque recién empiece el mes, se ve una puntita: un anillo vacío parece apagado.
        lienzo.drawArc(caja, -90f, (360f * avance).coerceIn(4f, 360f), false, pincel)
        return bmp
    }

    /**
     * Un punto por cada día del mes juntos: rosa los que ya pasaron, blanco el de hoy, gris los
     * que faltan. Con [latido] el de hoy crece, como el ♥.
     */
    fun puntos(context: Context, mes: Juntos.Mes, columnas: Int, latido: Boolean = false): Bitmap {
        val celda = 24
        val filas = (mes.diasDelMes + columnas - 1) / columnas
        val bmp = Bitmap.createBitmap(columnas * celda, filas * celda, Bitmap.Config.ARGB_8888)
        val lienzo = Canvas(bmp)
        val pincel = Paint(Paint.ANTI_ALIAS_FLAG)
        val pasado = context.getColor(R.color.puntos_rosa)
        val hoy = context.getColor(R.color.blanco)
        val falta = context.getColor(R.color.puntos_apagado)
        for (i in 0 until mes.diasDelMes) {
            val x = (i % columnas) * celda + celda / 2f
            val y = (i / columnas) * celda + celda / 2f
            pincel.color = when {
                i < mes.dia -> pasado
                i == mes.dia -> hoy
                else -> falta
            }
            val radio = if (i == mes.dia && latido) 11.5f else 8f
            lienzo.drawCircle(x, y, radio, pincel)
        }
        return bmp
    }
}
