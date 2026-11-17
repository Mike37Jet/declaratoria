package com.roguelito.rommicines

import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** El mes juntos que usan el anillo, la barra y los puntos. El sí fue el 9 de octubre a las 17:25. */
class MesJuntosTest {

    private fun en(fecha: String) = Juntos.mes(OffsetDateTime.parse(fecha).toInstant().toEpochMilli())

    @Test fun `a mitad del segundo mes`() {
        val m = en("2026-11-16T10:00:00-05:00")
        assertEquals(1, m.cumplidos)
        assertEquals(7, m.dia)
        assertEquals(30, m.diasDelMes) // del 9 de noviembre al 9 de diciembre
        assertEquals(23, m.faltan)
        assertEquals("faltan 23 días para 2 meses", m.texto)
        assertTrue(m.avance in 0.24f..0.26f)
    }

    @Test fun `el día que se cumple`() {
        val m = en("2026-12-09T18:00:00-05:00")
        assertEquals(2, m.cumplidos)
        assertEquals(0, m.dia)
        assertEquals("hoy cumplen 2 meses ♥︎", m.texto)
    }

    @Test fun `la víspera`() {
        assertEquals("mañana cumplen 2 meses", en("2026-12-08T20:00:00-05:00").texto)
    }

    @Test fun `el primer mes dice mes`() {
        assertEquals("faltan 21 días para 1 mes", en("2026-10-19T17:00:00-05:00").texto)
    }
}
