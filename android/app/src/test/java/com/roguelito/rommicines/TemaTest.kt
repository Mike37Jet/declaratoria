package com.roguelito.rommicines

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TemaTest {
    @Test
    fun etiquetasEnMayusculasSoloEnLosDisenosQueLasUsan() {
        assertEquals("DÍAS", Tema.CINE.dias(37))
        assertEquals("DÍA", Tema.NEON.dias(1))
        assertEquals("días", Tema.CIELO.dias(37))
        assertEquals("hora", Tema.PERLA.horas(1))
    }

    @Test
    fun horasConOSinCero() {
        assertEquals("05", Tema.CINE.numeroHoras(5))
        assertEquals("5", Tema.POLAROID.numeroHoras(5))
        assertEquals("12", Tema.PERLA.numeroHoras(12))
    }

    @Test
    fun soloMedianocheLlevaLasHorasEnElReloj() {
        assertEquals("05 h · %s", Tema.MEDIANOCHE.formatoReloj(5))
        assertNull(Tema.CINE.formatoReloj(5))
    }

    @Test
    fun corazonEsTextoNoEmoji() {
        assertEquals("♥︎", CORAZON)
    }
}
