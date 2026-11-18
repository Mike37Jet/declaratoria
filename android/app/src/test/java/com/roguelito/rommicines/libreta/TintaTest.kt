package com.roguelito.rommicines.libreta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TintaTest {
    @Test fun losCodigosVanYVuelven() {
        Tinta.entries.forEach { assertEquals(it, Tinta.de(it.codigo)) }
        assertEquals("rosa", Tinta.ROSA.codigo) // lo que ya está guardado en Firestore
    }

    @Test fun unCodigoDesconocidoEsAzul() {
        assertEquals(Tinta.AZUL, Tinta.de("dorada"))
        assertEquals(Tinta.AZUL, Tinta.de(null))
        assertNull(Tinta.deCodigo("dorada"))
    }

    @Test fun quienCedeNoSeQuedaConLaDelOtro() {
        Tinta.entries.forEach { ocupada ->
            Tinta.entries.forEach { preferida -> assertNotEquals(ocupada, Libreta.libre(preferida, ocupada)) }
        }
        assertEquals(Tinta.ROSA, Libreta.libre(Tinta.ROSA, Tinta.VERDE))
    }

    @Test fun cedeSoloUnoDeLosDos() {
        assertTrue(Libreta.cede("b", "a") != Libreta.cede("a", "b"))
    }
}
