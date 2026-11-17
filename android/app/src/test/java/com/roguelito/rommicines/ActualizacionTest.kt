package com.roguelito.rommicines

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Cuándo una versión publicada cuenta como más nueva que la instalada. */
class ActualizacionTest {
    @Test fun `una versión mayor es nueva`() {
        assertTrue(Actualizacion.esMasNueva("1.3", "1.2"))
        assertTrue(Actualizacion.esMasNueva("2.0", "1.9"))
        assertTrue(Actualizacion.esMasNueva("1.10", "1.9"))
        assertTrue(Actualizacion.esMasNueva("1.2.1", "1.2"))
    }

    @Test fun `la misma o una más vieja no lo es`() {
        assertFalse(Actualizacion.esMasNueva("1.2", "1.2"))
        assertFalse(Actualizacion.esMasNueva("1.2.0", "1.2"))
        assertFalse(Actualizacion.esMasNueva("1.1", "1.2"))
    }
}
