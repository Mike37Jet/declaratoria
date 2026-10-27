package com.roguelito.rommicines

import android.content.Context
import android.util.AttributeSet
import android.widget.GridView

/** Una sola superficie de desplazamiento; el inicio limita la vista previa a seis fotos. */
class GaleriaInicio(context: Context, attrs: AttributeSet) : GridView(context, attrs) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val ancho = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
        val lado = ((ancho - 2 * horizontalSpacing) / 3).coerceAtLeast(0)
        val filas = ((adapter?.count ?: 0) + 2) / 3
        val alto = filas * lado + (filas - 1).coerceAtLeast(0) * verticalSpacing + paddingTop + paddingBottom
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(alto, MeasureSpec.EXACTLY))
    }
}
