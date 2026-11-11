package com.spiralsoft.servare.data.sesion

import com.spiralsoft.servare.data.sync.SyncScheduler
import com.spiralsoft.servare.domain.model.EstadoSesion
import com.spiralsoft.servare.testutil.RelojFalso
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

// El gestor de sesión con el almacén doblado: la lectura inicial corre en Dispatchers.IO, así que se espera el estado
class GestorSesionUnitTest {
    private val almacen = mockk<AlmacenSesion>()
    private val scheduler = mockk<SyncScheduler>(relaxed = true)
    private val reloj = RelojFalso(ahora = 1_000_000L)

    private suspend fun gestor(guardada: SesionGuardada?): GestorSesion {
        coEvery { almacen.leer() } returns guardada
        val gestor = GestorSesion(almacen, scheduler, reloj)
        withTimeout(5_000.milliseconds) { gestor.estado.first { it != EstadoSesion.Cargando } }
        return gestor
    }

    @Test
    fun sin_nada_guardado_no_hay_sesion_ni_ciclo() = runBlocking {
        val gestor = gestor(null)

        assertEquals(EstadoSesion.SinSesion, gestor.estado.value)
        assertNull(gestor.token())
        verify(exactly = 0) { scheduler.requestSync() }
    }

    @Test
    fun una_sesion_vigente_se_abre_con_su_token_y_pide_un_ciclo() = runBlocking {
        val gestor = gestor(SesionGuardada("tok", "a@b.com", expiraEn = reloj.ahora + 1))

        assertEquals(EstadoSesion.ConSesion("a@b.com"), gestor.estado.value)
        assertEquals("tok", gestor.token())
        verify(exactly = 1) { scheduler.requestSync() }
    }

    @Test
    fun una_sesion_vencida_al_arrancar_queda_expirada_sin_token_ni_ciclo() = runBlocking {
        for (expiraEn in listOf(reloj.ahora, reloj.ahora - 1)) {
            val gestor = gestor(SesionGuardada("tok", "a@b.com", expiraEn))

            assertEquals(EstadoSesion.Expirada("a@b.com"), gestor.estado.value)
            assertNull(gestor.token())
        }
        verify(exactly = 0) { scheduler.requestSync() }
    }

    @Test
    fun guardar_abre_la_sesion_y_pide_un_ciclo() = runBlocking {
        val gestor = gestor(null)
        coEvery { almacen.guardar(SesionGuardada("nuevo", "x@y.com", 9L)) } returns true

        assertEquals(true, gestor.guardar("nuevo", "x@y.com", 9L))

        assertEquals(EstadoSesion.ConSesion("x@y.com"), gestor.estado.value)
        assertEquals("nuevo", gestor.token())
        verify(exactly = 1) { scheduler.requestSync() }
    }

    @Test
    fun si_no_se_pudo_guardar_el_estado_no_cambia() = runBlocking {
        val gestor = gestor(null)
        coEvery { almacen.guardar(any()) } returns false

        assertEquals(false, gestor.guardar("tok", "x@y.com", 9L))

        assertEquals(EstadoSesion.SinSesion, gestor.estado.value)
        assertNull(gestor.token())
        verify(exactly = 0) { scheduler.requestSync() }
    }

    @Test
    fun expirar_con_sesion_abierta_olvida_el_token_y_conserva_la_cuenta() = runBlocking {
        val gestor = gestor(SesionGuardada("tok", "a@b.com", reloj.ahora + 1))

        gestor.expirar()

        assertEquals(EstadoSesion.Expirada("a@b.com"), gestor.estado.value)
        assertNull(gestor.token())
    }

    @Test
    fun expirar_sin_sesion_no_hace_nada() = runBlocking {
        val gestor = gestor(null)
        gestor.expirar()
        assertEquals(EstadoSesion.SinSesion, gestor.estado.value)
    }

    @Test
    fun expirar_dos_veces_es_inofensivo() = runBlocking {
        val gestor = gestor(SesionGuardada("tok", "a@b.com", reloj.ahora + 1))
        gestor.expirar()
        gestor.expirar()
        assertEquals(EstadoSesion.Expirada("a@b.com"), gestor.estado.value)
    }

    @Test
    fun volver_a_iniciar_sesion_tras_expirar_la_reabre() = runBlocking {
        val gestor = gestor(SesionGuardada("viejo", "a@b.com", reloj.ahora + 1))
        gestor.expirar()
        coEvery { almacen.guardar(any()) } returns true

        gestor.guardar("nuevo", "a@b.com", reloj.ahora + 100)

        assertEquals(EstadoSesion.ConSesion("a@b.com"), gestor.estado.value)
        assertEquals("nuevo", gestor.token())
    }

    @Test
    fun cerrar_borra_la_sesion_guardada_y_deja_sin_sesion() = runBlocking {
        val gestor = gestor(SesionGuardada("tok", "a@b.com", reloj.ahora + 1))
        coEvery { almacen.borrar() } returns true

        assertEquals(true, gestor.cerrar())

        assertEquals(EstadoSesion.SinSesion, gestor.estado.value)
        assertNull(gestor.token())
    }

    @Test
    fun si_no_se_pudo_borrar_la_sesion_sigue_abierta() = runBlocking {
        val gestor = gestor(SesionGuardada("tok", "a@b.com", reloj.ahora + 1))
        coEvery { almacen.borrar() } returns false

        assertEquals(false, gestor.cerrar())

        assertEquals(EstadoSesion.ConSesion("a@b.com"), gestor.estado.value)
        assertEquals("tok", gestor.token())
        coVerify(exactly = 1) { almacen.borrar() }
    }
}
