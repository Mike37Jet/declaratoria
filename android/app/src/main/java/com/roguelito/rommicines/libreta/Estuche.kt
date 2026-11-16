package com.roguelito.rommicines.libreta

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import com.roguelito.rommicines.R

/** Dibuja un lapicero acostado en diagonal: la punta abajo a la izquierda, como si estuviera sobre la hoja. */
object DibujoLapicero {
    private val cuerpo = Paint(Paint.ANTI_ALIAS_FLAG)
    private val metal = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linea = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }

    fun dibujar(canvas: Canvas, context: Context, l: Lapicero, tinta: Int, cx: Float, cy: Float, largo: Float) {
        val d = context.resources.displayMetrics.density
        val grueso = when (l) { Lapicero.PLUMON -> 9f; Lapicero.PLUMA -> 6f; Lapicero.BOLIGRAFO -> 5f } * d
        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(-45f)
        val mitad = largo / 2
        val punta = largo * 0.22f
        // El cuerpo, del color de la tinta.
        cuerpo.color = tinta
        canvas.drawRoundRect(-mitad + punta, -grueso / 2, mitad, grueso / 2, grueso / 2, grueso / 2, cuerpo)
        metal.color = context.getColor(R.color.clip)
        val p = Path()
        when (l) {
            Lapicero.BOLIGRAFO -> { // cono y una bolita
                p.moveTo(-mitad + punta, -grueso / 2); p.lineTo(-mitad, 0f); p.lineTo(-mitad + punta, grueso / 2); p.close()
                canvas.drawPath(p, metal)
            }
            Lapicero.PLUMON -> { // punta gorda y roma
                canvas.drawRoundRect(-mitad, -grueso * 0.32f, -mitad + punta + d, grueso * 0.32f, 2 * d, 2 * d, cuerpo)
            }
            Lapicero.PLUMA -> { // plumín metálico con su ranura
                p.moveTo(-mitad + punta, -grueso * 0.75f); p.quadTo(-mitad + punta * 0.4f, -grueso * 0.3f, -mitad, 0f)
                p.quadTo(-mitad + punta * 0.4f, grueso * 0.3f, -mitad + punta, grueso * 0.75f); p.close()
                canvas.drawPath(p, metal)
                linea.color = tinta; linea.strokeWidth = 0.8f * d
                canvas.drawLine(-mitad + d, 0f, -mitad + punta * 0.7f, 0f, linea)
            }
        }
        // El clip del capuchón.
        linea.color = context.getColor(R.color.clip); linea.strokeWidth = 1.4f * d
        canvas.drawLine(mitad * 0.25f, -grueso / 2 - 1.5f * d, mitad - 2 * d, -grueso / 2 - 1.5f * d, linea)
        canvas.restore()
    }
}

/** El lapicero con que escribes, al final del renglón. Al tocarlo se abre el estuche. */
@SuppressLint("ViewConstructor")
class BotonLapicero(context: Context, private val tinta: Int) : View(context) {
    var lapicero = Libreta.lapicero(context)
        set(v) { field = v; contentDescription = "Lapicero: ${v.nombre}. Cambiar"; invalidate() }
    var alCambiar: ((Lapicero) -> Unit)? = null

    init {
        contentDescription = "Lapicero: ${lapicero.nombre}. Cambiar"
        isClickable = true
        setOnClickListener { abrirEstuche() }
    }

    override fun onDraw(canvas: Canvas) {
        DibujoLapicero.dibujar(canvas, context, lapicero, tinta, width / 2f, height / 2f, minOf(width, height) * 0.82f)
    }

    private fun abrirEstuche() {
        val d = resources.displayMetrics.density
        val caja = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(context.getColor(R.color.hoja)); cornerRadius = 6 * d
                setStroke((1 * d).toInt(), context.getColor(R.color.renglon))
            }
            setPadding((8 * d).toInt(), (6 * d).toInt(), (14 * d).toInt(), (6 * d).toInt())
        }
        val ventana = PopupWindow(caja, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true).apply {
            setBackgroundDrawable(ColorDrawable(0))
            elevation = 10 * d
            animationStyle = android.R.style.Animation_Dialog
        }
        Lapicero.entries.forEach { l ->
            caja.addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = (48 * d).toInt()
                isClickable = true
                contentDescription = l.nombre + if (l == lapicero) ", elegido" else ""
                if (l == lapicero) background = GradientDrawable().apply { setColor(context.getColor(R.color.renglon) and 0x55FFFFFF); cornerRadius = 4 * d }
                addView(object : View(context) {
                    override fun onDraw(canvas: Canvas) = DibujoLapicero.dibujar(canvas, context, l, tinta, width / 2f, height / 2f, height * 0.8f)
                }, LinearLayout.LayoutParams((44 * d).toInt(), (44 * d).toInt()))
                addView(TextView(context).apply {
                    text = "${l.nombre}  ·  así escribo"
                    typeface = if (l.negrita) Papel.letraNegrita(context) else Papel.letra(context)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 21f)
                    setTextColor(tinta)
                    setPadding((6 * d).toInt(), 0, 0, 0)
                })
                setOnClickListener {
                    Libreta.elegirLapicero(context, l)
                    lapicero = l
                    alCambiar?.invoke(l)
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    ventana.dismiss()
                }
            })
        }
        caja.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        // Se abre encima del lapicero, alineado a su derecha, como un estuche que se despliega.
        ventana.showAsDropDown(this, width - caja.measuredWidth, -height - caja.measuredHeight)
    }
}

/** El clip junto al renglón: puesto, lo que escribes sale como propuesta para que el otro la firme. */
class BotonClip(context: Context) : View(context) {
    var puesto = false
        set(v) { field = v; contentDescription = if (v) "Propuesta: sí. Quitar el clip" else "Escribir como propuesta"; invalidate() }
    var alCambiar: ((Boolean) -> Unit)? = null

    private val d = resources.displayMetrics.density
    private val alambre = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 1.8f * d; strokeCap = Paint.Cap.ROUND
        color = context.getColor(R.color.clip)
    }
    private val papel = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.hoja) }
    private val borde = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 1f * d; color = context.getColor(R.color.tinta_guia)
    }
    private val clip = Path().apply {
        moveTo(10 * d, 24 * d); lineTo(10 * d, 6 * d)
        arcTo(RectF(2 * d, 2 * d, 10 * d, 10 * d), 0f, -180f, false)
        lineTo(2 * d, 28 * d)
        arcTo(RectF(2 * d, 25 * d, 8 * d, 31 * d), 180f, -180f, false)
        lineTo(8 * d, 10 * d)
    }

    init {
        puesto = false
        isClickable = true
        setOnClickListener {
            puesto = !puesto
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            alCambiar?.invoke(puesto)
        }
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f; val cy = height / 2f
        canvas.save()
        canvas.rotate(-12f, cx, cy)
        if (puesto) {
            // Puesto: sujeta una notita de papel.
            canvas.drawRect(cx - 9 * d, cy - 8 * d, cx + 11 * d, cy + 14 * d, papel)
            canvas.drawRect(cx - 9 * d, cy - 8 * d, cx + 11 * d, cy + 14 * d, borde)
            alambre.alpha = 255
        } else alambre.alpha = 150
        canvas.translate(cx - 6 * d, cy - 16 * d - if (puesto) 4 * d else 0f)
        canvas.drawPath(clip, alambre)
        canvas.restore()
    }
}
