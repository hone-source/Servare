package com.spiralsoft.servare.data.repository

import android.database.sqlite.SQLiteException
import com.spiralsoft.servare.data.connectivity.ConnectivityMonitor
import com.spiralsoft.servare.data.remote.ResultadoLoginRemoto
import com.spiralsoft.servare.data.remote.ResultadoRegistroRemoto
import com.spiralsoft.servare.data.remote.SesionRemoteDataSource
import com.spiralsoft.servare.data.sesion.GestorSesion
import com.spiralsoft.servare.data.sync.SyncEngine
import com.spiralsoft.servare.data.sync.SyncScheduler
import com.spiralsoft.servare.domain.model.EstadoConectividad
import com.spiralsoft.servare.domain.model.EstadoConectividad.CONECTADO
import com.spiralsoft.servare.domain.model.EstadoConectividad.RED_SIN_SERVIDOR
import com.spiralsoft.servare.domain.model.EstadoConectividad.SIN_RED
import com.spiralsoft.servare.domain.model.EstadoSesion
import com.spiralsoft.servare.domain.repository.LONGITUD_MINIMA_CONTRASENA
import com.spiralsoft.servare.domain.repository.ResultadoCierreSesion
import com.spiralsoft.servare.domain.repository.ResultadoInicioSesion
import com.spiralsoft.servare.domain.repository.ResultadoRegistro
import com.spiralsoft.servare.testutil.BaseDeDatosRegla
import com.spiralsoft.servare.testutil.operacion
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Validación, exigencia de CONECTADO y traducción de resultados de registro, inicio y cierre de sesión
@RunWith(RobolectricTestRunner::class)
class DefaultSesionRepositoryUnitTest {
    @get:Rule val bd = BaseDeDatosRegla()

    private val conectividad = MutableStateFlow(CONECTADO)
    private val gestor = mockk<GestorSesion>(relaxed = true).also {
        every { it.estado } returns MutableStateFlow<EstadoSesion>(EstadoSesion.SinSesion)
    }
    private val remoto = mockk<SesionRemoteDataSource>()
    private val monitor = mockk<ConnectivityMonitor>(relaxed = true).also { every { it.estado } returns conectividad }
    private val scheduler = mockk<SyncScheduler>(relaxed = true)
    private val motor = mockk<SyncEngine>().also {
        coEvery { it.pausado<Any?>(any()) } coAnswers { firstArg<suspend () -> Any?>().invoke() }
    }
    private val repo by lazy {
        DefaultSesionRepository(gestor, remoto, monitor, bd.db, bd.operaciones, scheduler, motor)
    }

    private val contrasenaValida = "x".repeat(LONGITUD_MINIMA_CONTRASENA)

    // --- registro ---

    @Test
    fun un_correo_invalido_no_llega_al_servidor() = runBlocking {
        for (correo in listOf("", "   ", "sin-arroba", "a@", "@b.com", "a b@c.com")) {
            assertEquals("«$correo»", ResultadoRegistro.CorreoInvalido, repo.registrar(correo, contrasenaValida, "clave"))
        }
        coVerify(exactly = 0) { remoto.registrar(any(), any(), any()) }
    }

    @Test
    fun una_contrasena_mas_corta_que_el_minimo_se_rechaza_y_la_justa_pasa() = runBlocking {
        assertEquals(
            ResultadoRegistro.ContrasenaCorta,
            repo.registrar("a@b.com", "x".repeat(LONGITUD_MINIMA_CONTRASENA - 1), "clave")
        )
        coEvery { remoto.registrar(any(), any(), any()) } returns ResultadoRegistroRemoto.Rechazado
        assertEquals(ResultadoRegistro.Rechazado, repo.registrar("a@b.com", contrasenaValida, "clave"))
    }

    @Test
    fun el_correo_se_recorta_y_la_clave_de_registro_viaja_tal_cual() = runBlocking {
        coEvery { remoto.registrar(any(), any(), any()) } returns ResultadoRegistroRemoto.Rechazado

        repo.registrar("  a@b.com  ", contrasenaValida, "clave-uuid")

        coVerify { remoto.registrar("a@b.com", contrasenaValida, "clave-uuid") }
    }

    @Test
    fun sin_conectado_el_registro_ni_se_intenta() = runBlocking {
        for (estado in listOf(SIN_RED, RED_SIN_SERVIDOR)) {
            conectividad.value = estado
            assertEquals(ResultadoRegistro.SinConexion(estado), repo.registrar("a@b.com", contrasenaValida, "c"))
        }
        coVerify(exactly = 0) { remoto.registrar(any(), any(), any()) }
    }

    @Test
    fun un_registro_exitoso_guarda_la_sesion() = runBlocking {
        coEvery { remoto.registrar(any(), any(), any()) } returns ResultadoRegistroRemoto.Creada("tok", 999L, "a@b.com")
        coEvery { gestor.guardar(any(), any(), any()) } returns true

        assertEquals(ResultadoRegistro.Registrado, repo.registrar("a@b.com", contrasenaValida, "c"))

        coVerify { gestor.guardar("tok", "a@b.com", 999L) }
    }

    @Test
    fun si_no_se_puede_guardar_la_sesion_el_registro_lo_dice() = runBlocking {
        coEvery { remoto.registrar(any(), any(), any()) } returns ResultadoRegistroRemoto.Creada("tok", 999L, "a@b.com")
        coEvery { gestor.guardar(any(), any(), any()) } returns false

        assertEquals(ResultadoRegistro.ErrorAlmacenamiento, repo.registrar("a@b.com", contrasenaValida, "c"))
    }

    @Test
    fun cada_resultado_remoto_del_registro_tiene_su_resultado() = runBlocking {
        val casos = listOf(
            ResultadoRegistroRemoto.CorreoYaRegistrado to ResultadoRegistro.CorreoYaRegistrado,
            ResultadoRegistroRemoto.Rechazado to ResultadoRegistro.Rechazado,
            ResultadoRegistroRemoto.ErrorServidor to ResultadoRegistro.ErrorServidor
        )
        for ((remota, esperada) in casos) {
            coEvery { remoto.registrar(any(), any(), any()) } returns remota
            assertEquals(esperada, repo.registrar("a@b.com", contrasenaValida, "c"))
        }
        verify(exactly = 0) { monitor.reportarServidorNoAlcanzable() }
    }

    @Test
    fun sin_respuesta_o_sin_conexion_durante_el_registro_se_avisa_al_monitor() = runBlocking {
        coEvery { remoto.registrar(any(), any(), any()) } returns ResultadoRegistroRemoto.SinRespuesta
        assertEquals(ResultadoRegistro.SinRespuesta, repo.registrar("a@b.com", contrasenaValida, "c"))
        verify(exactly = 1) { monitor.reportarServidorNoAlcanzable() }

        coEvery { remoto.registrar(any(), any(), any()) } returns ResultadoRegistroRemoto.SinConexion
        every { monitor.reportarServidorNoAlcanzable() } answers { conectividad.value = RED_SIN_SERVIDOR }
        assertEquals(
            "informa el estado que dejó el monitor",
            ResultadoRegistro.SinConexion(RED_SIN_SERVIDOR), repo.registrar("a@b.com", contrasenaValida, "c")
        )
    }

    // --- inicio de sesión ---

    @Test
    fun iniciar_sesion_valida_correo_y_pide_contrasena_pero_no_exige_longitud() = runBlocking {
        assertEquals(ResultadoInicioSesion.CorreoInvalido, repo.iniciarSesion("mal", "x"))
        assertEquals(ResultadoInicioSesion.FaltaContrasena, repo.iniciarSesion("a@b.com", ""))
        coEvery { remoto.iniciarSesion(any(), any()) } returns ResultadoLoginRemoto.CredencialesInvalidas

        assertEquals(ResultadoInicioSesion.CredencialesInvalidas, repo.iniciarSesion("a@b.com", "corta"))
    }

    @Test
    fun sin_conectado_el_inicio_de_sesion_ni_se_intenta() = runBlocking {
        conectividad.value = SIN_RED
        assertEquals(ResultadoInicioSesion.SinConexion(SIN_RED), repo.iniciarSesion("a@b.com", "clave"))
        coVerify(exactly = 0) { remoto.iniciarSesion(any(), any()) }
    }

    @Test
    fun un_inicio_exitoso_guarda_la_sesion_y_si_no_se_puede_lo_dice() = runBlocking {
        coEvery { remoto.iniciarSesion(any(), any()) } returns ResultadoLoginRemoto.Autenticada("tok", 5L, "a@b.com")
        coEvery { gestor.guardar(any(), any(), any()) } returns true
        assertEquals(ResultadoInicioSesion.Autenticado, repo.iniciarSesion("a@b.com", "clave"))
        coVerify { gestor.guardar("tok", "a@b.com", 5L) }

        coEvery { gestor.guardar(any(), any(), any()) } returns false
        assertEquals(ResultadoInicioSesion.ErrorAlmacenamiento, repo.iniciarSesion("a@b.com", "clave"))
    }

    @Test
    fun cada_resultado_remoto_del_inicio_tiene_su_resultado() = runBlocking {
        val casos = listOf(
            ResultadoLoginRemoto.CredencialesInvalidas to ResultadoInicioSesion.CredencialesInvalidas,
            ResultadoLoginRemoto.Rechazado to ResultadoInicioSesion.Rechazado,
            ResultadoLoginRemoto.ErrorServidor to ResultadoInicioSesion.ErrorServidor
        )
        for ((remota, esperada) in casos) {
            coEvery { remoto.iniciarSesion(any(), any()) } returns remota
            assertEquals(esperada, repo.iniciarSesion("a@b.com", "clave"))
        }
        coEvery { remoto.iniciarSesion(any(), any()) } returns ResultadoLoginRemoto.SinRespuesta
        assertEquals(ResultadoInicioSesion.SinRespuesta, repo.iniciarSesion("a@b.com", "clave"))
        verify(exactly = 1) { monitor.reportarServidorNoAlcanzable() }
    }

    // --- cambios sin enviar y cierre ---

    @Test
    fun se_cuentan_los_cambios_que_el_servidor_aun_no_confirma() = runBlocking {
        bd.operaciones.insert(operacion("a", 1, entidad = "e1"))
        bd.operaciones.insert(operacion("b", 1, entidad = "e2", estado = com.spiralsoft.servare.domain.model.EstadoOperacion.FALLIDA))
        bd.operaciones.insert(operacion("c", 1, entidad = "e3", estado = com.spiralsoft.servare.domain.model.EstadoOperacion.COMPLETADA))

        assertEquals(2, repo.contarCambiosSinEnviar())
    }

    @Test
    fun cerrar_la_sesion_cancela_el_ciclo_borra_los_datos_locales_y_cierra() = runBlocking {
        bd.operaciones.insert(operacion("a", 1))
        coEvery { gestor.cerrar() } returns true

        assertEquals(ResultadoCierreSesion.Cerrada, repo.cerrarSesion())

        assertEquals(0, bd.operaciones.contarSinTerminar())
        coVerify { scheduler.cancelar() }
        coVerify(exactly = 1) { gestor.cerrar() }
        verify(exactly = 0) { scheduler.requestSync() }
    }

    @Test
    fun el_cierre_ocurre_con_el_motor_pausado() = runBlocking {
        var cerroDentro = false
        coEvery { motor.pausado<Any?>(any()) } coAnswers {
            val resultado = firstArg<suspend () -> Any?>().invoke()
            cerroDentro = true
            resultado
        }
        coEvery { gestor.cerrar() } answers { cerroDentro.not() }

        assertEquals(ResultadoCierreSesion.Cerrada, repo.cerrarSesion())
    }

    @Test
    fun si_no_se_puede_cerrar_la_sesion_se_reanuda_la_cola() = runBlocking {
        coEvery { gestor.cerrar() } returns false

        assertEquals(ResultadoCierreSesion.ErrorAlmacenamiento, repo.cerrarSesion())

        verify(exactly = 1) { scheduler.requestSync() }
    }

    @Test
    fun si_falla_el_borrado_local_el_cierre_lo_dice_y_se_reanuda_la_cola() = runBlocking {
        coEvery { motor.pausado<Any?>(any()) } throws SQLiteException("disco")

        assertEquals(ResultadoCierreSesion.ErrorAlmacenamiento, repo.cerrarSesion())

        coVerify(exactly = 0) { gestor.cerrar() }
        verify(exactly = 1) { scheduler.requestSync() }
    }

    @Test
    fun el_estado_de_la_sesion_es_el_del_gestor() {
        assertEquals(EstadoSesion.SinSesion, repo.estado.value)
    }
}
