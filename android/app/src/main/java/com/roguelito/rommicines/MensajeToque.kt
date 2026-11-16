package com.roguelito.rommicines

enum class TipoToque(val codigo: String, private val dibujo: String, val frase: String) {
    TOQUE("toque", "💗", "Un toque"),
    PENSANDO("pensando", "💞", "Estoy pensando en ti"),
    EXTRANO("extrano", "🥺", "Te extraño"),
    ABRAZO("abrazo", "🫂", "Quiero un abrazo"),
    BESO("beso", "💋", "Un beso de vuelta"),
    TE_AMO("te_amo", "❤️", "Te amo"),
    FELIZ("feliz", "🥰", "Me haces feliz"),
    BUENOS_DIAS("buenos_dias", "☀️", "Buenos días, amor"),
    BUENAS_NOCHES("buenas_noches", "🌙", "Que descanses"),
    LLEGUE("llegue", "🏡", "Ya llegué"),
    ANIMO("animo", "💪", "Ánimo, tú puedes");

    /** 🫂 llegó con Android 11; antes se veía un cuadrito, así que ahí va 🤗. */
    val emoji: String get() = if (this == ABRAZO && android.os.Build.VERSION.SDK_INT < 30) "🤗" else dibujo

    val etiqueta: String get() = "$emoji $frase"

    fun recibido(nombre: String): String = when (this) {
        TOQUE -> "$emoji $nombre te mandó un toque"
        PENSANDO -> "$emoji $nombre está pensando en ti"
        EXTRANO -> "$emoji $nombre te extraña"
        ABRAZO -> "$emoji $nombre quiere un abrazo"
        BESO -> "$emoji $nombre te mandó un beso"
        TE_AMO -> "$emoji $nombre te dice: te amo"
        FELIZ -> "$emoji $nombre dice: me haces feliz"
        BUENOS_DIAS -> "$emoji $nombre te da los buenos días"
        BUENAS_NOCHES -> "$emoji $nombre te desea que descanses"
        LLEGUE -> "$emoji $nombre ya llegó"
        ANIMO -> "$emoji $nombre te manda ánimo: tú puedes"
    }

    companion object {
        /** Las que se eligen en la app: primero el cariño, después las del día. */
        val elegibles = listOf(PENSANDO, EXTRANO, ABRAZO, TE_AMO, FELIZ, BUENOS_DIAS, BUENAS_NOCHES, LLEGUE, ANIMO)

        // Los APK anteriores no mandaban el campo "tipo"; y una frase que este APK no conoce sale como toque.
        fun desde(codigo: String?): TipoToque = entries.firstOrNull { it.codigo == codigo } ?: TOQUE
    }
}

data class MensajeToque(val de: String, val nombre: String, val tipo: TipoToque, val para: String? = null) {
    fun datos(): Map<String, String> = buildMap {
        put("de", de)
        put("nombre", nombre)
        put("tipo", tipo.codigo)
        para?.let { put("para", it) }
    }

    companion object {
        fun paraEsteCelular(datos: Map<String, String>, id: String): MensajeToque? {
            val de = datos["de"]?.takeIf { it.isNotBlank() } ?: return null
            val nombre = datos["nombre"]?.takeIf { it.isNotBlank() } ?: return null
            val para = datos["para"]?.takeIf { it.isNotBlank() }
            if (de == id || (para != null && para != id)) return null
            return MensajeToque(de, nombre, TipoToque.desde(datos["tipo"]), para)
        }
    }
}
