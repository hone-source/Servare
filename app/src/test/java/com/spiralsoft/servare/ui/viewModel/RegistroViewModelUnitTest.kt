package com.spiralsoft.servare.ui.viewModel

import com.spiralsoft.servare.domain.model.AvisoSinConexion
import com.spiralsoft.servare.domain.model.EstadoConectividad
import com.spiralsoft.servare.domain.model.EstadoSesion
import com.spiralsoft.servare.domain.repository.LONGITUD_MINIMA_CONTRASENA
import com.spiralsoft.servare.domain.repository.ResultadoRegistro
import com.spiralsoft.servare.domain.repository.SesionRepository
import com.spiralsoft.servare.notification.ServicioNotificacionEstado
import com.spiralsoft.servare.testutil.ReglaDispatcherPrincipal
import com.spiralsoft.servare.ui.viewModel.state.MensajesSesion
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RegistroViewModelUnitTest {
    @get:Rule val principal = ReglaDispatcherPrincipal()

    private val repo = mockk<SesionRepository>().also { every { it.estado } returns MutableStateFlow<EstadoSesion>(EstadoSesion.SinSesion) }
    private val servicio = mockk<ServicioNotificacionEstado>().also {
        every { it.registroNoDisponible } returns MutableStateFlow<AvisoSinConexion?>(null)
    }
    private val vm by lazy { RegistroViewModel(repo, servicio) }
    private val claves = mutableListOf<String>()

    private fun resultado(r: ResultadoRegistro) = coEvery { repo.registrar(any(), any(), any()) } coAnswers {
        claves += thirdArg<String>()
        r
    }

    private fun escribir() {
        vm.updateCorreo("a@b.com")
        vm.updateContrasena("secreta123")
    }

    @Test
    fun el_reintento_del_mismo_registro_lleva_la_misma_clave() = runTest(principal.dispatcher) {
        backgroundScope.launch { vm.state.collect {} }
        resultado(ResultadoRegistro.SinRespuesta)
        escribir()

        vm.enviar()
        vm.enviar()
        vm.enviar()

        assertEquals(3, claves.size)
        assertEquals("solo hay una clave: el servidor reconoce el reintento", 1, claves.toSet().size)
    }

    @Test
    fun editar_los_datos_es_otro_registro_y_cambia_la_clave() = runTest(principal.dispatcher) {
        backgroundScope.launch { vm.state.collect {} }
        resultado(ResultadoRegistro.SinRespuesta)
        escribir()
        vm.enviar()

        vm.updateCorreo("otro@b.com")
        vm.enviar()
        vm.updateContrasena("otra-clave-123")
        vm.enviar()

        assertEquals(3, claves.toSet().size)
    }

    @Test
    fun alternar_la_visibilidad_no_cambia_la_clave() = runTest(principal.dispatcher) {
        backgroundScope.launch { vm.state.collect {} }
        resultado(ResultadoRegistro.SinRespuesta)
        escribir()
        vm.enviar()

        vm.cambiarVisibilidad()
        vm.enviar()

        assertEquals(1, claves.toSet().size)
    }

    @Test
    fun cada_clave_es_un_uuid_distinto_entre_viewmodels() {
        val a = RegistroViewModel(repo, servicio)
        val b = RegistroViewModel(repo, servicio)
        coEvery { repo.registrar(any(), any(), any()) } coAnswers { claves += thirdArg<String>(); ResultadoRegistro.SinRespuesta }
        a.updateCorreo("a@b.com"); a.updateContrasena("secreta123")
        b.updateCorreo("a@b.com"); b.updateContrasena("secreta123")
        a.enviar(); b.enviar()
        assertNotEquals(claves[0], claves[1])
        assertEquals(36, claves[0].length)
    }

    @Test
    fun cada_resultado_se_explica_en_su_campo_o_en_un_mensaje() = runTest(principal.dispatcher) {
        backgroundScope.launch { vm.state.collect {} }
        escribir()

        resultado(ResultadoRegistro.CorreoInvalido)
        vm.enviar()
        assertEquals("Escribe un correo válido", vm.state.value.errorCorreo)

        resultado(ResultadoRegistro.ContrasenaCorta)
        vm.enviar()
        assertEquals("Mínimo $LONGITUD_MINIMA_CONTRASENA caracteres", vm.state.value.errorContrasena)

        resultado(ResultadoRegistro.CorreoYaRegistrado)
        vm.enviar()
        assertEquals("Este correo ya está registrado", vm.state.value.errorCorreo)

        val mensajes = mapOf(
            ResultadoRegistro.SinConexion(EstadoConectividad.RED_SIN_SERVIDOR) to MensajesSesion.sinConexion,
            ResultadoRegistro.SinRespuesta to MensajesSesion.sinRespuesta,
            ResultadoRegistro.Rechazado to MensajesSesion.rechazado,
            ResultadoRegistro.ErrorServidor to MensajesSesion.errorServidor
        )
        for ((r, esperado) in mensajes) {
            resultado(r)
            vm.enviar()
            assertEquals("$r", esperado, vm.state.value.mensaje)
            assertFalse("$r", vm.state.value.enviando)
            vm.mensajeMostrado()
        }
    }

    @Test
    fun el_error_de_almacenamiento_dice_que_la_cuenta_ya_se_creo_y_ofrece_reintentar() = runTest(principal.dispatcher) {
        backgroundScope.launch { vm.state.collect {} }
        resultado(ResultadoRegistro.ErrorAlmacenamiento)
        escribir()

        vm.enviar()

        val mensaje = vm.state.value.mensaje!!
        assertTrue(mensaje.reintentable)
        assertTrue(mensaje.texto.startsWith("La cuenta se creó"))
    }

    @Test
    fun un_registro_exitoso_deja_enviando_para_que_la_sesion_navegue() = runTest(principal.dispatcher) {
        backgroundScope.launch { vm.state.collect {} }
        resultado(ResultadoRegistro.Registrado)
        escribir()

        vm.enviar()

        assertTrue(vm.state.value.enviando)
        coVerify(exactly = 1) { repo.registrar("a@b.com", "secreta123", any()) }
    }
}
