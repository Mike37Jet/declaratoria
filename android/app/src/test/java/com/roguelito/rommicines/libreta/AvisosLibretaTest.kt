package com.roguelito.rommicines.libreta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AvisosLibretaTest {
    private val aviso = AvisoLibreta("pareja1", "celularA", "Mimina", Evento.PROPUESTA, "plan1", "Ir al cine")

    @Test
    fun `el aviso llega al otro celular de la misma libreta`() {
        val recibido = AvisoLibreta.paraEsteCelular(aviso.datos(), "pareja1", "celularB")
        assertEquals(aviso, recibido)
    }

    @Test
    fun `no le llega a quien lo mando`() {
        assertNull(AvisoLibreta.paraEsteCelular(aviso.datos(), "pareja1", "celularA"))
    }

    @Test
    fun `no le llega a otra libreta (ni a la de prueba del emulador)`() {
        assertNull(AvisoLibreta.paraEsteCelular(aviso.datos(), "otraPareja", "celularB"))
        assertNull(AvisoLibreta.paraEsteCelular(aviso.datos(), null, "celularB"))
    }

    @Test
    fun `un toque no es un aviso de la libreta`() {
        val toque = mapOf("nombre" to "Mimina", "de" to "celularA", "mensaje" to "hola")
        assertFalse(AvisoLibreta.esDeLibreta(toque))
        assertNull(AvisoLibreta.paraEsteCelular(toque, "pareja1", "celularB"))
        // Y el aviso no lleva "nombre", para que un APK viejo no lo tome por toque.
        assertFalse("nombre" in aviso.datos())
    }

    @Test
    fun `un evento desconocido se ignora`() {
        val datos = aviso.datos() + ("evento" to "baile")
        assertNull(AvisoLibreta.paraEsteCelular(datos, "pareja1", "celularB"))
    }

    @Test
    fun `sin nombre se dice tu persona`() {
        val datos = aviso.datos() + ("quien" to "")
        assertEquals("Tu persona", AvisoLibreta.paraEsteCelular(datos, "pareja1", "celularB")!!.quien)
    }

    @Test
    fun `cada evento tiene su titulo`() {
        Evento.entries.forEach { e -> assertTrue(aviso.copy(evento = e).titulo().isNotBlank()) }
        assertTrue(aviso.cuerpo().contains("firmarla"))
    }

    @Test
    fun `lapicero desconocido es boligrafo`() {
        Lapicero.entries.forEach { assertEquals(it, Lapicero.de(it.codigo)) }
        assertEquals(Lapicero.BOLIGRAFO, Lapicero.de(null))
        assertEquals(Lapicero.BOLIGRAFO, Lapicero.de("crayon"))
    }

    @Test
    fun `una propuesta queda por firmar hasta que la firman`() {
        val p = Plan(id = "1", texto = "x", autor = "a", tinta = Tinta.AZUL, orden = 1, hecho = null, hechoPor = null, propuesta = true)
        assertTrue(p.porFirmar)
        assertFalse(p.copy(firmadaPor = "b").porFirmar)
        assertFalse(p.copy(propuesta = false).porFirmar)
    }

    @Test
    fun `las manchas de cafe salen con el uso`() {
        assertEquals(0, Libreta.manchas(2))
        assertEquals(1, Libreta.manchas(3))
        assertEquals(2, Libreta.manchas(10))
        assertEquals(3, Libreta.manchas(50))
    }
}
