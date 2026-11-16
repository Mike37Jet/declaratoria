package com.roguelito.rommicines.libreta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CodigosTest {
    @Test
    fun `un codigo nuevo tiene 6 letras sin las que se confunden`() {
        repeat(200) {
            val codigo = Codigos.nuevo()
            assertEquals(6, codigo.length)
            assertTrue(codigo, codigo.all { it in Codigos.LETRAS })
        }
        assertTrue("0" !in Codigos.LETRAS && "O" !in Codigos.LETRAS && "1" !in Codigos.LETRAS && "I" !in Codigos.LETRAS && "L" !in Codigos.LETRAS)
    }

    @Test
    fun `normalizar acepta como lo escriba la persona`() {
        assertEquals("K7PQX2", Codigos.normalizar("k7p-qx2"))
        assertEquals("K7PQX2", Codigos.normalizar(" K7P QX2 "))
    }

    @Test
    fun `normalizar rechaza lo que no puede ser un codigo`() {
        assertNull(Codigos.normalizar("K7PQX"))
        assertNull(Codigos.normalizar("K7PQX22"))
        assertNull(Codigos.normalizar("K0PQX2")) // el cero no se usa
        assertNull(Codigos.normalizar(""))
    }

    @Test
    fun `mostrar parte el codigo en dos`() {
        assertEquals("K7P-QX2", Codigos.mostrar("K7PQX2"))
    }

    @Test
    fun `las marcas van de cinco en cinco`() {
        assertEquals(0, Marcas.grupos(0))
        assertEquals(1, Marcas.grupos(5))
        assertEquals(2, Marcas.grupos(6))
        assertEquals(1, Marcas.renglones(0, 6))
        assertEquals(1, Marcas.renglones(30, 6))
        assertEquals(2, Marcas.renglones(31, 6))
        assertEquals(17, Marcas.renglones(100, 1) - 3)
    }
}
