package com.roguelito.rommicines

enum class TipoToque(val codigo: String, val emoji: String, val frase: String) {
    TOQUE("toque", "💗", "Un toque"),
    PENSANDO("pensando", "💭", "Estoy pensando en ti"),
    EXTRANO("extrano", "🥺", "Te extraño"),
    ABRAZO("abrazo", "🤗", "Quiero un abrazo"),
    BESO("beso", "💋", "Un beso de vuelta");

    val etiqueta: String get() = "$emoji $frase"

    fun recibido(nombre: String): String = when (this) {
        TOQUE -> "$emoji $nombre te mandó un toque"
        PENSANDO -> "$emoji $nombre está pensando en ti"
        EXTRANO -> "$emoji $nombre te extraña"
        ABRAZO -> "$emoji $nombre quiere un abrazo"
        BESO -> "$emoji $nombre te mandó un beso"
    }

    companion object {
        val elegibles = listOf(PENSANDO, EXTRANO, ABRAZO)

        // Los APK anteriores no mandaban el campo "tipo".
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
