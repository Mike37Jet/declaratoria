package com.roguelito.rommicines

import android.content.Context
import java.util.Locale

/**
 * Los diseños del widget contador. Cada uno tiene sus tres layouts (completo, franja y mini)
 * porque un widget no puede cambiar de fuente en tiempo de ejecución; los colores y fondos
 * viven en colors_temas.xml y en los drawables marco_*, caja_* y boton_*.
 *
 * [pie] es el texto de abajo del completo cuando no hay un toque reciente (0: sin pie).
 * [corazon] y [latido] son los colores del ♥ en reposo y cuando llega un toque;
 * [latidoEnBoton] es el del latido encima del botón de la franja, si hace falta otro.
 * [destello] es el fondo que se enciende con el latido (solo el Neón lo tiene).
 * [marco] hace falta junto a [destello] para volver al fondo normal tras el latido.
 * [suelto] es el color del ♥ del mini cuando no es el mismo que el del botón ([corazon]).
 * [grafico] es lo que se pinta como imagen en `@id/grafico` (anillo o puntos).
 * [escalaBoton] agranda el ♥ (y su latido) del botón de la franja, y [escalaSuelto] el del mini,
 * en los diseños donde el ♥ va dentro de un círculo o pastilla grande.
 */
// El ♥ crece por pasos de la escala áurea de letra (10 · 13 · 16 · 21 · 26 · 34): el del botón
// va de 16 a 21 sp, y el suelto del mini de 13 a 16 (o a 21 con dos pasos).
private const val UN_PASO_BOTON = 21f / 16f
private const val UN_PASO_SUELTO = 16f / 13f
private const val DOS_PASOS_SUELTO = 21f / 13f

enum class Tema(
    val nombre: String,
    val estilo: String,
    val completo: Int,
    val franja: Int,
    val mini: Int,
    val pie: Int,
    val corazon: Int,
    val latido: Int,
    val latidoEnBoton: Int = latido,
    val mayusculas: Boolean = false,
    val horaConCero: Boolean = true,
    val simbolo: String = CORAZON,
    val marco: Int = 0,
    val destello: Int = 0,
    val suelto: Int = 0,
    val grafico: Grafico = Grafico.NINGUNO,
    val escalaBoton: Float = 1f,
    val escalaSuelto: Float = 1f,
) {
    CINE("Cine", "elegante", R.layout.widget_contador, R.layout.widget_contador_franja, R.layout.widget_contador_mini,
        R.string.contador_pie,
        R.color.gold_light, R.color.latido, mayusculas = true),
    CIELO("Cielo", "creativo", R.layout.widget_cielo, R.layout.widget_cielo_franja, R.layout.widget_cielo_mini,
        R.string.cielo_pie,
        R.color.cielo_corazon, R.color.cielo_latido),
    ATARDECER("Atardecer", "creativo", R.layout.widget_atardecer, R.layout.widget_atardecer_franja, R.layout.widget_atardecer_mini,
        R.string.atardecer_pie,
        R.color.atardecer_texto, R.color.atardecer_latido),
    POLAROID("Polaroid", "creativo", R.layout.widget_polaroid, R.layout.widget_polaroid_franja, R.layout.widget_polaroid_mini,
        R.string.polaroid_pie,
        R.color.polaroid_corazon, R.color.polaroid_latido, horaConCero = false),
    NEON("Neón", "creativo", R.layout.widget_neon, R.layout.widget_neon_franja, R.layout.widget_neon_mini,
        R.string.neon_pie,
        R.color.neon_rosa, R.color.neon_cian, mayusculas = true,
        marco = R.drawable.marco_neon, destello = R.drawable.marco_neon_destello),
    NUBE("Nube", "minimalista", R.layout.widget_nube, R.layout.widget_nube_franja, R.layout.widget_nube_mini,
        R.string.nube_pie,
        R.color.nube_celeste, R.color.nube_latido, simbolo = "♡"),
    MEDIANOCHE("Medianoche", "minimalista", R.layout.widget_medianoche, R.layout.widget_medianoche_franja, R.layout.widget_medianoche_mini,
        0,
        R.color.medianoche_texto, R.color.medianoche_latido, simbolo = "♡"),
    MATERIAL("Material You", "minimalista · con los colores de tu fondo",
        R.layout.widget_material, R.layout.widget_material_franja, R.layout.widget_material_mini,
        R.string.material_pie,
        R.color.material_sobre_boton, R.color.material_latido, latidoEnBoton = R.color.material_latido_boton),
    PERLA("Perla", "elegante", R.layout.widget_perla, R.layout.widget_perla_franja, R.layout.widget_perla_mini,
        0,
        R.color.perla_acento, R.color.perla_latido, horaConCero = false),
    AZUL("Azul noche", "elegante", R.layout.widget_azul, R.layout.widget_azul_franja, R.layout.widget_azul_mini,
        R.string.azul_pie,
        R.color.azul_celeste, R.color.azul_latido),

    // Estilo Apple y One UI (2026). Los layouts los genera el mismo script: mismos ids en todos.
    VIDRIO("Vidrio líquido", "estilo Apple · deja ver tu fondo", R.layout.widget_vidrio, R.layout.widget_vidrio_franja, R.layout.widget_vidrio_mini,
        R.string.vidrio_pie,
        R.color.vidrio_rosa, R.color.vidrio_latido, escalaBoton = UN_PASO_BOTON),
    ANILLO("Anillo", "estilo Apple · avanza hacia el próximo mes", R.layout.widget_anillo, R.layout.widget_anillo_franja, R.layout.widget_anillo_mini,
        R.string.anillo_pie,
        R.color.anillo_rosa, R.color.anillo_latido, grafico = Grafico.ANILLO, escalaBoton = UN_PASO_BOTON, escalaSuelto = UN_PASO_SUELTO),
    PUNTOS("Puntos", "estilo Apple · un punto por cada día del mes", R.layout.widget_puntos, R.layout.widget_puntos_franja, R.layout.widget_puntos_mini,
        0,
        R.color.puntos_rosa, R.color.puntos_latido, grafico = Grafico.PUNTOS, escalaBoton = UN_PASO_BOTON),
    PAPEL("Papel", "estilo Apple · claro", R.layout.widget_papel, R.layout.widget_papel_franja, R.layout.widget_papel_mini,
        R.string.papel_pie,
        R.color.blanco, R.color.papel_latido, suelto = R.color.papel_rojo, escalaBoton = UN_PASO_BOTON),
    TINTA("Tinta", "minimalista · un solo color", R.layout.widget_tinta, R.layout.widget_tinta_franja, R.layout.widget_tinta_mini,
        R.string.tinta_pie,
        R.color.tinta_acento, R.color.tinta_latido, escalaBoton = UN_PASO_BOTON),
    TRANSPARENTE("Transparente", "sin fondo · solo las letras", R.layout.widget_transparente, R.layout.widget_transparente_franja, R.layout.widget_transparente_mini,
        R.string.transparente_pie,
        R.color.blanco, R.color.transparente_latido, escalaBoton = UN_PASO_BOTON),
    BARRA("Barra Now", "estilo One UI · barra hacia el próximo mes", R.layout.widget_barra, R.layout.widget_barra_franja, R.layout.widget_barra_mini,
        R.string.barra_pie,
        R.color.blanco, R.color.barra_latido, escalaBoton = UN_PASO_BOTON, escalaSuelto = DOS_PASOS_SUELTO),
    PASTEL("Pastel", "estilo One UI · lila claro", R.layout.widget_pastel, R.layout.widget_pastel_franja, R.layout.widget_pastel_mini,
        R.string.pastel_pie,
        R.color.blanco, R.color.pastel_latido, latidoEnBoton = R.color.bloques_latido, suelto = R.color.pastel_morado, escalaBoton = UN_PASO_BOTON),
    BLOQUES("Bloques", "estilo One UI · fichas de color", R.layout.widget_bloques, R.layout.widget_bloques_franja, R.layout.widget_bloques_mini,
        R.string.bloques_pie,
        R.color.blanco, R.color.bloques_boton, latidoEnBoton = R.color.bloques_latido, suelto = R.color.bloques_rosa_texto, escalaBoton = UN_PASO_BOTON),
    CORAL("Coral", "estilo One UI · color fuerte", R.layout.widget_coral, R.layout.widget_coral_franja, R.layout.widget_coral_mini,
        R.string.coral_pie,
        R.color.coral_fondo, R.color.coral_latido, suelto = R.color.blanco, escalaBoton = UN_PASO_BOTON);

    fun dias(n: Long) = etiqueta(if (n == 1L) "día" else "días")
    fun horas(n: Long) = etiqueta(if (n == 1L) "hora" else "horas")
    fun numeroHoras(n: Long): String = if (horaConCero) "%02d".format(n) else n.toString()

    /** El diseño completo de Medianoche lleva las horas dentro del reloj: "05 h · 42:18". */
    fun formatoReloj(horas: Long): String? =
        if (this == MEDIANOCHE) "%02d h · %%s".format(horas) else null

    /** El ♥ del mini va suelto sobre el fondo; en Material el color de [corazon] es para encima del botón. */
    val corazonSuelto get() = if (suelto != 0) suelto else when (this) {
        MATERIAL -> R.color.material_boton
        CINE -> R.color.gold
        else -> corazon
    }

    private fun etiqueta(texto: String) = if (mayusculas) texto.uppercase(Locale.forLanguageTag("es")) else texto
}

/** Lo que algunos diseños pintan como imagen en `@id/grafico` ([Graficos]). */
enum class Grafico { NINGUNO, ANILLO, PUNTOS }

/** ♥ como texto (no emoji): así toma el color de cada diseño y del latido. */
const val CORAZON = "♥\uFE0E"

/** Qué diseño tiene cada widget. Los nuevos usan el último que se eligió. */
object Temas {
    private fun prefs(context: Context) = context.getSharedPreferences("temas", Context.MODE_PRIVATE)

    fun de(context: Context, id: Int): Tema {
        val p = prefs(context)
        return buscar(p.getString("widget_$id", null)) ?: porDefecto(context)
    }

    fun porDefecto(context: Context): Tema = buscar(prefs(context).getString("por_defecto", null)) ?: Tema.CINE

    fun guardar(context: Context, id: Int, tema: Tema) {
        prefs(context).edit().putString("widget_$id", tema.name).putString("por_defecto", tema.name).apply()
    }

    /** Desde la app (sin un widget concreto): cambia todos y queda como diseño para los nuevos. */
    fun guardarTodos(context: Context, ids: IntArray, tema: Tema) {
        prefs(context).edit().apply {
            ids.forEach { putString("widget_$it", tema.name) }
            putString("por_defecto", tema.name)
        }.apply()
    }

    fun olvidar(context: Context, ids: IntArray) {
        prefs(context).edit().apply { ids.forEach { remove("widget_$it") } }.apply()
    }

    private fun buscar(nombre: String?) = Tema.entries.firstOrNull { it.name == nombre }
}
