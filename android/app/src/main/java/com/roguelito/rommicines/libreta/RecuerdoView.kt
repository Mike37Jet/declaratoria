package com.roguelito.rommicines.libreta

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.OvershootInterpolator
import com.roguelito.rommicines.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.random.Random

/**
 * Una página del álbum: papel kraft, la polaroid pegada con dos cintas, la línea escrita
 * con la tinta de quien la pegó, el boleto de la aventura y una calcomanía de corazón.
 */
class RecuerdoView(context: Context) : View(context) {

    private var recuerdo: Recuerdo? = null
    private var foto: Bitmap? = null

    /** 0..1: oscurece la página mientras gira, para que se note la profundidad. */
    var sombra = 0f
        set(v) { if (field != v) { field = v; invalidate() } }

    /** 0..1: la polaroid cayendo sobre la página (1 = ya pegada). */
    private var caida = 1f
    private var anim: ValueAnimator? = null

    private val d = context.resources.displayMetrics.density
    private val kraft = context.getColor(R.color.kraft)
    private val fibra = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.kraft_fibra); strokeWidth = 1f * d; alpha = 40 }
    private val lomo = Paint()
    private val blanco = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.polaroid) }
    private val sombraPapel = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x38000000 }
    private val vacia = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.foto_vacia) }
    private val pintaFoto = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val cinta = Paint(Paint.ANTI_ALIAS_FLAG)
    private val boleto = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.boleto) }
    private val tintaBoleto = context.getColor(R.color.boleto_tinta)
    private val trazoBoleto = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = tintaBoleto; style = Paint.Style.STROKE; strokeWidth = 1.2f * d
        pathEffect = DashPathEffect(floatArrayOf(4 * d, 3 * d), 0f)
    }
    private val letraLinea = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Papel.letra(context); textSize = Papel.sp(context, 23f) }
    private val letraPlan = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Papel.letraNegrita(context); textSize = Papel.sp(context, 19f); color = tintaBoleto }
    private val letraChica = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Papel.letraNegrita(context); textSize = Papel.sp(context, 11f); color = tintaBoleto; letterSpacing = 0.14f
    }
    private val letraNumero = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Papel.letraNegrita(context); textSize = Papel.sp(context, 30f); color = tintaBoleto }
    private val letraVacia = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Papel.letra(context); textSize = Papel.sp(context, 24f); color = context.getColor(R.color.tinta_lapiz)
    }
    private val corazon = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.tinta_rosa) }
    private val bordeCorazon = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.polaroid); style = Paint.Style.STROKE; strokeWidth = 3.5f * d; strokeJoin = Paint.Join.ROUND }
    private val oscuro = Paint()
    private val origen = Rect()
    private val destino = RectF()

    fun mostrar(r: Recuerdo?, f: Bitmap?) {
        recuerdo = r
        foto = f
        caida = 1f
        contentDescription = r?.let { "Recuerdo ${it.numero}: ${it.plan}. ${it.linea}" } ?: "Álbum vacío"
        invalidate()
    }

    /** La polaroid recién pegada: cae un poco girada, se asienta y las cintas la sujetan. */
    fun pegar() {
        if (!Papel.conMovimiento()) return
        anim?.cancel()
        caida = 0f
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 420
            startDelay = 160
            interpolator = OvershootInterpolator(1.1f)
            var sono = false
            addUpdateListener {
                caida = it.animatedValue as Float
                if (!sono && it.animatedFraction > 0.6f) { sono = true; performHapticFeedback(HapticFeedbackConstants.CONFIRM) }
                invalidate()
            }
            start()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        lomo.shader = LinearGradient(0f, 0f, 18 * d, 0f, 0x55000000, 0x00000000, Shader.TileMode.CLAMP)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawColor(kraft)
        // Fibras del papel: siempre las mismas en cada página.
        val azar = Random(recuerdo?.id?.hashCode() ?: 7)
        repeat(140) {
            val x = azar.nextFloat() * w
            val y = azar.nextFloat() * h
            val largo = (4 + azar.nextFloat() * 10) * d
            val angulo = azar.nextFloat() * 0.6f - 0.3f
            canvas.drawLine(x, y, x + largo, y + largo * angulo, fibra)
        }
        canvas.drawRect(0f, 0f, 18 * d, h, lomo)

        val r = recuerdo
        if (r == null) dibujarVacia(canvas, w, h) else dibujarRecuerdo(canvas, r, w, h)

        if (sombra > 0f) {
            oscuro.color = (((sombra.coerceIn(0f, 1f) * 0.35f) * 255).toInt() shl 24)
            canvas.drawRect(0f, 0f, w, h, oscuro)
        }
    }

    private fun dibujarVacia(canvas: Canvas, w: Float, h: Float) {
        val texto = "Todavía no hay recuerdos.\n\nTachen un plan en la libreta y peguen aquí su foto."
        val ancho = (w * 0.72f).toInt()
        val capa = StaticLayout.Builder.obtain(texto, 0, texto.length, letraVacia, ancho)
            .setAlignment(Layout.Alignment.ALIGN_CENTER).build()
        canvas.save()
        canvas.translate((w - ancho) / 2, (h - capa.height) / 2)
        capa.draw(canvas)
        canvas.restore()
    }

    private fun dibujarRecuerdo(canvas: Canvas, r: Recuerdo, w: Float, h: Float) {
        val semilla = abs(r.id.hashCode())
        val borde = 12 * d
        val anchoPolaroid = minOf(w * 0.76f, 340 * d)
        val ladoFoto = anchoPolaroid - borde * 2
        val banda = 70 * d
        val altoPolaroid = borde + ladoFoto + banda
        val cx = w / 2 + 6 * d
        // Polaroid y boleto juntos, un poco por encima del centro (el centro óptico).
        val conjunto = altoPolaroid + 34 * d + 78 * d
        val arriba = maxOf((h - conjunto) * 0.42f, 28 * d)
        val giro = (semilla % 7) - 3f

        // --- La polaroid (cae desde más grande y más girada al pegarse) ---
        val escala = 1.14f - 0.14f * caida
        canvas.save()
        canvas.translate(cx, arriba + altoPolaroid / 2)
        canvas.rotate(giro + 7f * (1f - caida))
        canvas.scale(escala, escala)
        val x0 = -anchoPolaroid / 2
        val y0 = -altoPolaroid / 2
        val levantada = (1f - caida.coerceIn(0f, 1f)) * 10 * d
        canvas.drawRect(x0 + 3 * d + levantada, y0 + 5 * d + levantada, x0 + anchoPolaroid + 3 * d + levantada, y0 + altoPolaroid + 5 * d + levantada, sombraPapel)
        canvas.drawRect(x0, y0, x0 + anchoPolaroid, y0 + altoPolaroid, blanco)
        destino.set(x0 + borde, y0 + borde, x0 + borde + ladoFoto, y0 + borde + ladoFoto)
        val f = foto
        if (f != null) {
            // Recorte cuadrado del centro de la foto.
            val lado = minOf(f.width, f.height)
            origen.set((f.width - lado) / 2, (f.height - lado) / 2, (f.width + lado) / 2, (f.height + lado) / 2)
            canvas.drawBitmap(f, origen, destino, pintaFoto)
        } else {
            canvas.drawRect(destino, vacia)
        }
        // La línea, con la tinta de quien la pegó.
        if (r.linea.isNotBlank()) {
            letraLinea.color = context.getColor(r.tinta.color)
            val capa = StaticLayout.Builder.obtain(r.linea, 0, r.linea.length, letraLinea, ladoFoto.toInt())
                .setAlignment(Layout.Alignment.ALIGN_CENTER).setMaxLines(2).setEllipsize(TextUtils.TruncateAt.END)
                .setIncludePad(false).build()
            canvas.save()
            canvas.translate(x0 + borde, y0 + borde + ladoFoto + (banda - capa.height) / 2)
            capa.draw(canvas)
            canvas.restore()
        }
        // Calcomanía de corazón en la esquina de abajo.
        dibujarCorazon(canvas, x0 + anchoPolaroid - 8 * d, y0 + altoPolaroid - 12 * d, 38 * d, -12f + (semilla % 5) * 4f)
        canvas.restore()

        // --- Las cintas: aparecen cuando la polaroid ya tocó la página ---
        val presion = ((caida - 0.55f) / 0.45f).coerceIn(0f, 1f)
        if (presion > 0f) {
            cinta.color = context.getColor(if (semilla % 2 == 0) R.color.cinta_miel else R.color.cinta_menta)
            cinta.alpha = (175 * presion).toInt()
            val izq = cx - anchoPolaroid / 2 + 8 * d
            val der = cx + anchoPolaroid / 2 - 8 * d
            dibujarCinta(canvas, izq, arriba + 4 * d, -38f + giro, semilla)
            dibujarCinta(canvas, der, arriba + 4 * d, 36f + giro, semilla + 1)
        }

        // --- El boleto de la aventura ---
        val anchoBoleto = minOf(w * 0.7f, 300 * d)
        val altoBoleto = 78 * d
        val by = minOf(arriba + altoPolaroid + 34 * d, h - altoBoleto - 24 * d)
        canvas.save()
        canvas.translate(w / 2 - 10 * d, by + altoBoleto / 2)
        canvas.rotate(-giro * 0.6f + 1.5f)
        dibujarBoleto(canvas, r, anchoBoleto, altoBoleto)
        canvas.restore()
    }

    private fun dibujarCinta(canvas: Canvas, x: Float, y: Float, angulo: Float, semilla: Int) {
        val largo = 78 * d
        val ancho = 24 * d
        val azar = Random(semilla)
        val camino = Path()
        // Bordes rasgados en zigzag, como cinta cortada con los dedos.
        camino.moveTo(-largo / 2, -ancho / 2)
        camino.lineTo(largo / 2, -ancho / 2)
        val dientes = 5
        for (i in 1..dientes) camino.lineTo(largo / 2 + (if (i % 2 == 0) 0f else 2.5f * d + azar.nextFloat() * d), -ancho / 2 + ancho * i / dientes)
        camino.lineTo(-largo / 2, ancho / 2)
        for (i in dientes - 1 downTo 0) camino.lineTo(-largo / 2 - (if (i % 2 == 0) 0f else 2.5f * d + azar.nextFloat() * d), -ancho / 2 + ancho * i / dientes)
        camino.close()
        canvas.save()
        canvas.translate(x, y)
        canvas.rotate(angulo)
        canvas.drawPath(camino, cinta)
        canvas.restore()
    }

    private fun dibujarBoleto(canvas: Canvas, r: Recuerdo, ancho: Float, alto: Float) {
        val muesca = 9 * d
        val talon = 66 * d
        val forma = Path().apply {
            addRoundRect(-ancho / 2, -alto / 2, ancho / 2, alto / 2, 4 * d, 4 * d, Path.Direction.CW)
            // Las muescas de un boleto de verdad, a la altura del corte.
            val corte = Path().apply {
                addCircle(-ancho / 2 + talon, -alto / 2, muesca, Path.Direction.CW)
                addCircle(-ancho / 2 + talon, alto / 2, muesca, Path.Direction.CW)
            }
            op(corte, Path.Op.DIFFERENCE)
        }
        canvas.save()
        canvas.translate(2 * d, 3 * d)
        canvas.drawPath(forma, sombraPapel)
        canvas.restore()
        canvas.drawPath(forma, boleto)
        val xCorte = -ancho / 2 + talon
        canvas.drawLine(xCorte, -alto / 2 + muesca + 2 * d, xCorte, alto / 2 - muesca - 2 * d, trazoBoleto)

        // Talón: el número de la aventura.
        val no = "Nº"
        canvas.drawText(no, -ancho / 2 + (talon - letraChica.measureText(no)) / 2, -alto / 2 + 22 * d, letraChica)
        val numero = r.numero.toString()
        canvas.drawText(numero, -ancho / 2 + (talon - letraNumero.measureText(numero)) / 2, alto / 2 - 18 * d, letraNumero)

        // Cuerpo: qué hicieron y cuándo.
        val izquierda = xCorte + 12 * d
        val util = (ancho / 2 - izquierda - 10 * d).toInt()
        canvas.drawText("BOLETO · UNA AVENTURA", izquierda, -alto / 2 + 20 * d, letraChica)
        val capa = StaticLayout.Builder.obtain(r.plan, 0, r.plan.length, letraPlan, util)
            .setMaxLines(1).setEllipsize(TextUtils.TruncateAt.END).setIncludePad(false).build()
        canvas.save()
        canvas.translate(izquierda, -alto / 2 + 26 * d)
        capa.draw(canvas)
        canvas.restore()
        canvas.drawText(FECHA.format(Date(r.fecha)).uppercase(), izquierda, alto / 2 - 14 * d, letraChica)
    }

    private fun dibujarCorazon(canvas: Canvas, cx: Float, cy: Float, lado: Float, angulo: Float) {
        val s = lado / 2
        val camino = Path().apply {
            moveTo(0f, s * 0.9f)
            cubicTo(-s * 1.3f, s * 0.05f, -s * 0.85f, -s * 1.05f, 0f, -s * 0.45f)
            cubicTo(s * 0.85f, -s * 1.05f, s * 1.3f, s * 0.05f, 0f, s * 0.9f)
            close()
        }
        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(angulo)
        canvas.drawPath(camino, bordeCorazon)
        canvas.drawPath(camino, corazon)
        canvas.restore()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        anim?.cancel()
    }

    companion object {
        private val FECHA = SimpleDateFormat("d 'de' MMMM yyyy", Locale.forLanguageTag("es"))
    }
}
