package com.roguelito.rommicines

import android.animation.AnimatorInflater
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * Elegir el diseño del contador. La abre el launcher (al poner el widget o desde
 * "Personalizar"/ajustes del widget) con el id del widget, o la app sin id: entonces
 * el diseño se aplica a todos los contadores y queda para los que se pongan después.
 *
 * Las vistas previas son los mismos layouts del widget, sin acciones.
 */
class ElegirTemaActivity : Activity() {

    private var widget = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widget = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val propios = contadores()
        if (widget != AppWidgetManager.INVALID_APPWIDGET_ID && widget !in propios) {
            finish()
            return
        }
        if (widget != AppWidgetManager.INVALID_APPWIDGET_ID) {
            // Salir sin elegir deja el widget puesto con el diseño de siempre.
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widget))
            ContadorWidget.actualizar(this, widget)
        }
        val actual = if (widget != AppWidgetManager.INVALID_APPWIDGET_ID) Temas.de(this, widget) else Temas.porDefecto(this)
        setContentView(pantalla(actual, varios = widget == AppWidgetManager.INVALID_APPWIDGET_ID && propios.size > 1))
    }

    private fun contadores() = AppWidgetManager.getInstance(this)
        .getAppWidgetIds(ComponentName(this, ContadorWidget::class.java))

    private fun elegir(tema: Tema) {
        if (widget != AppWidgetManager.INVALID_APPWIDGET_ID) {
            Temas.guardar(this, widget, tema)
            ContadorWidget.actualizar(this, widget)
        } else {
            Temas.guardarTodos(this, contadores(), tema)
            ContadorWidget.actualizarTodos(this)
        }
        Toast.makeText(this, "Diseño ${tema.nombre} listo ♥", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun pantalla(actual: Tema, varios: Boolean): View {
        val lista = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(24))
        }
        lista.addView(texto(getString(R.string.elegir_diseno), 23f, R.color.gold).apply {
            typeface = Typeface.create("serif", Typeface.BOLD)
        })
        val ayuda = getString(R.string.elegir_diseno_ayuda) + if (varios) " Se aplica a todos tus contadores." else ""
        lista.addView(texto(ayuda, 14f, R.color.cream_soft).apply { setPadding(0, dp(4), 0, dp(12)) })
        Tema.entries.forEach { lista.addView(tarjeta(it, it == actual)) }

        return ScrollView(this).apply {
            setBackgroundColor(getColor(R.color.cinema))
            addView(lista)
            setOnApplyWindowInsetsListener { vista, insets ->
                @Suppress("DEPRECATION")
                vista.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
                insets
            }
        }
    }

    private fun tarjeta(tema: Tema, enUso: Boolean): View {
        val cabecera = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(texto(tema.nombre, 16f, R.color.cream).apply { setTypeface(typeface, Typeface.BOLD) })
            addView(texto("  ·  ${tema.estilo}", 12f, R.color.cream_soft), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply {
                marginEnd = dp(8)
            })
            if (enUso) addView(texto("✓ en uso", 12f, R.color.gold_light))
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundResource(if (enUso) R.drawable.tarjeta_elegida else R.drawable.accion_inicio)
            stateListAnimator = AnimatorInflater.loadStateListAnimator(context, R.animator.presionar)
            isClickable = true
            contentDescription = "Usar el diseño ${tema.nombre}" + if (enUso) ", en uso" else ""
            setOnClickListener { elegir(tema) }
            addView(cabecera)
            addView(previa(tema, ContadorWidget.Vista.COMPLETO), LinearLayout.LayoutParams(MATCH_PARENT, dp(150)).apply {
                topMargin = dp(10)
                bottomMargin = dp(8)
            })
            // Franja a todo lo ancho (como un 4×1) y el mini al tamaño de un 2×1.
            addView(previa(tema, ContadorWidget.Vista.FRANJA), LinearLayout.LayoutParams(MATCH_PARENT, dp(64)).apply { bottomMargin = dp(8) })
            addView(previa(tema, ContadorWidget.Vista.MINI), LinearLayout.LayoutParams(dp(180), dp(64)))
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(12) }
        }
    }

    private fun previa(tema: Tema, vista: ContadorWidget.Vista) = FrameLayout(this).apply {
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        addView(ContadorWidget.vistaPrevia(context, tema, vista).apply(context, this))
    }

    private fun texto(contenido: String, tamaño: Float, color: Int) = TextView(this).apply {
        text = contenido
        textSize = tamaño
        setTextColor(getColor(color))
    }

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
}
