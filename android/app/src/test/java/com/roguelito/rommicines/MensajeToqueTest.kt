package com.roguelito.rommicines

import org.junit.Assert.*
import org.junit.Test

class MensajeToqueTest {
    @Test fun `mensajes de la version anterior siguen llegando como toques`() {
        val mensaje = MensajeToque.paraEsteCelular(mapOf("de" to "otro", "nombre" to "Mimina"), "yo")
        assertEquals(TipoToque.TOQUE, mensaje?.tipo)
    }

    @Test fun `un tipo futuro desconocido no impide recibir el toque`() {
        val datos = mapOf("de" to "otro", "nombre" to "Mimina", "tipo" to "futuro")
        assertEquals(TipoToque.TOQUE, MensajeToque.paraEsteCelular(datos, "yo")?.tipo)
    }

    @Test fun `todos los mensajes conservan su significado al enviarse y recibirse`() {
        TipoToque.entries.forEach { tipo ->
            val mensaje = MensajeToque("otro", "Mimina", tipo)
            assertEquals(mensaje, MensajeToque.paraEsteCelular(mensaje.datos(), "yo"))
        }
    }

    @Test fun `el beso solo llega al celular que mando el mensaje original`() {
        val beso = MensajeToque("otro", "Mimina", TipoToque.BESO, "yo")
        assertEquals(beso, MensajeToque.paraEsteCelular(beso.datos(), "yo"))
        assertNull(MensajeToque.paraEsteCelular(beso.datos(), "tercero"))
        assertNull(MensajeToque.paraEsteCelular(beso.datos(), "otro"))
    }

    @Test fun `el tema compartido nunca devuelve mis propios toques`() {
        val mensaje = MensajeToque("yo", "Roguelito", TipoToque.PENSANDO)
        assertNull(MensajeToque.paraEsteCelular(mensaje.datos(), "yo"))
    }

    @Test fun `mensajes incompletos no producen notificaciones ni respuestas`() {
        listOf(emptyMap(), mapOf("de" to "otro"), mapOf("nombre" to "Mimina"),
            mapOf("de" to "", "nombre" to "Mimina"), mapOf("de" to "otro", "nombre" to " "))
            .forEach { assertNull(MensajeToque.paraEsteCelular(it, "yo")) }
    }
}
