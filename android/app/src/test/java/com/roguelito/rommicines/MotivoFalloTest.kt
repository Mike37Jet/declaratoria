package com.roguelito.rommicines

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import org.junit.Assert.assertEquals
import org.junit.Test

class MotivoFalloTest {
    @Test fun `sin internet se explica como sin conexion`() {
        assertEquals("sin conexión a internet", Toques.motivo(UnknownHostException("fcm.googleapis.com")))
        assertEquals("sin conexión a internet", Toques.motivo(ConnectException("Network is unreachable")))
    }

    @Test fun `un tiempo agotado se explica como conexion lenta`() {
        assertEquals("la conexión es muy lenta", Toques.motivo(SocketTimeoutException("Read timed out")))
    }

    @Test fun `un fallo seguro apunta a la hora del celular`() {
        assertEquals(
            "no se pudo abrir una conexión segura; revisa la hora del celular",
            Toques.motivo(SSLException("Chain validation failed")),
        )
    }

    @Test fun `si no se pudo pedir la sesion se dice asi`() {
        assertEquals(
            "no se pudo confirmar la sesión de la libreta",
            Toques.motivo(java.util.concurrent.TimeoutException("Timed out")),
        )
    }

    @Test fun `lo demas es un error inesperado`() {
        assertEquals("error inesperado", Toques.motivo(RuntimeException("x")))
    }
}
