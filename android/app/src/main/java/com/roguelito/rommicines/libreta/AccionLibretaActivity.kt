package com.roguelito.rommicines.libreta

import android.animation.AnimatorInflater
import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.style.DynamicDrawableSpan
import android.text.style.ImageSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.roguelito.rommicines.R

/**
 * Lo que pasa al tocar algo del widget de la libreta. No tiene pantalla propia:
 * - firmar una propuesta del otro: una notita de papel encima de la pantalla de inicio («¿Lo firmas?»),
 *   sin abrir la libreta;
 * - tachar, abrir un plan o escribir uno nuevo: abre la libreta y ella lo hace (el tachón lleva su sello
 *   y luego pide la foto para el álbum, así que tiene que ser allí).
 */
class AccionLibretaActivity : Activity() {

    companion object {
        const val ABRIR = "abrir"
        const val TACHAR = "tachar"
        const val FIRMAR = "firmar"
        const val NUEVO = "nuevo"

        const val ACCION = "accion"
        const val PLAN = "plan"
        private const val TEXTO = "texto"
        private const val TINTA = "tinta"

        /** La plantilla de la lista del widget: cada renglón la completa con [relleno]. */
        fun plantilla(context: Context): PendingIntent {
            val mutable = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
            return PendingIntent.getActivity(context, 10, Intent(context, AccionLibretaActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or mutable)
        }

        fun relleno(accion: String, p: LibretaWidget.Pendiente): Intent = Intent()
            .putExtra(ACCION, accion).putExtra(PLAN, p.id).putExtra(TEXTO, p.texto)
            .putExtra(TINTA, if (p.tinta == Tinta.ROSA) "rosa" else "azul")

        /** Abrir la libreta directamente (el «+» y la cabecera del widget). */
        fun abrirLibreta(context: Context, accion: String?, plan: String?): PendingIntent =
            PendingIntent.getActivity(context, 20 + (accion?.hashCode() ?: 0) % 1000,
                aLaLibreta(context, accion, plan), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        fun aLaLibreta(context: Context, accion: String?, plan: String?): Intent =
            Intent(context, LibretaActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .apply { accion?.let { putExtra(ACCION, it) }; plan?.let { putExtra(PLAN, it) } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val accion = intent.getStringExtra(ACCION)
        val plan = intent.getStringExtra(PLAN)
        if (accion == FIRMAR && plan != null) {
            preguntarFirma(plan, intent.getStringExtra(TEXTO) ?: "", Tinta.de(intent.getStringExtra(TINTA)))
            return
        }
        startActivity(aLaLibreta(this, accion, plan))
        finish()
        @Suppress("DEPRECATION") overridePendingTransition(0, 0)
    }

    /** La misma notita de la libreta, pero flotando sobre la pantalla de inicio. */
    private fun preguntarFirma(plan: String, texto: String, tinta: Tinta) {
        val d = resources.displayMetrics.density
        fun dp(n: Int) = (n * d).toInt()
        val tarjeta = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = HojaDrawable(this@AccionLibretaActivity)
            elevation = dp(10).toFloat()
            setPadding(Papel.margen(this@AccionLibretaActivity).toInt() + dp(4), Papel.linea(this@AccionLibretaActivity).toInt(), dp(20), dp(13))
            rotation = 1.2f
            isClickable = true
        }
        tarjeta.addView(letra("¿Lo firmas?", 26f, R.color.tinta_lapiz, negrita = true))
        tarjeta.addView(letra("«$texto»", 21f, tinta.color).apply {
            (layoutParams as ViewGroup.MarginLayoutParams).topMargin = dp(8)
        })
        tarjeta.addView(letra("Con tu firma queda como plan de los dos.", 16f, R.color.tinta_suave).apply {
            (layoutParams as ViewGroup.MarginLayoutParams).topMargin = dp(5)
        })
        tarjeta.addView(boton("Firmar", R.drawable.ic_libreta_firma) {
            Libreta.firmar(this, Plan(plan, texto, autor = "", tinta = tinta, orden = 0, hecho = null, hechoPor = null, propuesta = true))
            LibretaWidget.firmadaAqui(this, plan)
            cerrar()
        })
        tarjeta.addView(letra("Todavía no", 16f, R.color.tinta_suave, negrita = true).apply {
            paintFlags = paintFlags or Paint.UNDERLINE_TEXT_FLAG
            gravity = Gravity.CENTER
            minHeight = dp(48)
            isClickable = true
            setOnClickListener { cerrar() }
        })

        val fondo = FrameLayout(this).apply {
            setBackgroundColor(0x73000000)
            isClickable = true
            setOnClickListener { cerrar() }
            addView(tarjeta, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER).apply {
                setMargins(dp(21), dp(21), dp(21), dp(21))
            })
        }
        setContentView(fondo)
        if (Papel.conMovimiento()) {
            fondo.alpha = 0f
            fondo.animate().alpha(1f).setDuration(200).setInterpolator(Papel.salida).start()
            tarjeta.translationY = dp(34).toFloat()
            tarjeta.animate().translationY(0f).setDuration(260).setInterpolator(Papel.salida).start()
        }
    }

    private fun cerrar() {
        finish()
        @Suppress("DEPRECATION") overridePendingTransition(0, android.R.anim.fade_out)
    }

    private fun letra(texto: String, sp: Float, color: Int, negrita: Boolean = false) = TextView(this).apply {
        text = texto
        typeface = if (negrita) Papel.letraNegrita(this@AccionLibretaActivity) else Papel.letra(this@AccionLibretaActivity)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(getColor(color))
        includeFontPadding = false
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    /** Un botón como etiqueta de tela, igual que los de la libreta. */
    private fun boton(texto: String, icono: Int, accion: () -> Unit) = TextView(this).apply {
        val d = resources.displayMetrics.density
        val lado = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 21f, resources.displayMetrics)
        val trazo = getDrawable(icono)!!.mutate().apply { setTint(getColor(R.color.tapa_etiqueta)); setBounds(0, 0, lado.toInt(), lado.toInt()) }
        text = SpannableStringBuilder().append("·", ImageSpan(trazo, DynamicDrawableSpan.ALIGN_BASELINE), 0).append("  ").append(texto)
        contentDescription = texto
        typeface = Papel.letraNegrita(this@AccionLibretaActivity)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 21f)
        setTextColor(getColor(R.color.tapa_etiqueta))
        gravity = Gravity.CENTER
        minHeight = (55 * d).toInt()
        background = GradientDrawable().apply { setColor(getColor(R.color.tapa_tela)); cornerRadius = 8 * d }
        stateListAnimator = AnimatorInflater.loadStateListAnimator(this@AccionLibretaActivity, R.animator.presionar)
        isClickable = true
        setOnClickListener { accion() }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = (13 * d).toInt() }
    }
}
