package com.spiralsoft.servare.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EstadoSesionTest {

    @Test
    fun el_correo_actual_existe_con_sesion_abierta_o_expirada() {
        assertEquals("a@b.mx", EstadoSesion.ConSesion("a@b.mx").correoActual)
        assertEquals("a@b.mx", EstadoSesion.Expirada("a@b.mx").correoActual)
    }

    @Test
    fun sin_sesion_o_cargando_no_hay_correo() {
        assertNull(EstadoSesion.SinSesion.correoActual)
        assertNull(EstadoSesion.Cargando.correoActual)
    }
}
