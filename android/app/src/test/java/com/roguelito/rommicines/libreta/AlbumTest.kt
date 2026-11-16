package com.roguelito.rommicines.libreta

import org.junit.Assert.assertEquals
import org.junit.Test

class AlbumTest {
    private fun plan(id: String, hecho: Long?) = Plan(id, "plan $id", "yo", Tinta.AZUL, 0, hecho, null)

    @Test fun elBoletoSigueElOrdenEnQueSeTacho() {
        val planes = listOf(plan("a", 300), plan("b", null), plan("c", 100), plan("d", 200))
        assertEquals(1, Album.numeroDeBoleto(planes, "c"))
        assertEquals(2, Album.numeroDeBoleto(planes, "d"))
        assertEquals(3, Album.numeroDeBoleto(planes, "a"))
    }

    @Test fun unPlanPendienteNoTieneBoleto() {
        assertEquals(0, Album.numeroDeBoleto(listOf(plan("b", null)), "b"))
    }

    @Test fun empatesSeOrdenanPorId() {
        val planes = listOf(plan("z", 5), plan("m", 5))
        assertEquals(1, Album.numeroDeBoleto(planes, "m"))
        assertEquals(2, Album.numeroDeBoleto(planes, "z"))
    }
}
