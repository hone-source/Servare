package com.spiralsoft.servare.ui.viewModel

import com.spiralsoft.servare.domain.model.AvisoSinConexion
import com.spiralsoft.servare.domain.model.EstadoConectividad
import com.spiralsoft.servare.domain.model.EstadoSesion
import com.spiralsoft.servare.domain.repository.ResultadoInicioSesion
import com.spiralsoft.servare.domain.repository.SesionRepository
import com.spiralsoft.servare.notification.ServicioNotificacionEstado
import com.spiralsoft.servare.testutil.ReglaDispatcherPrincipal
import com.spiralsoft.servare.ui.viewModel.state.MensajeSesion
import com.spiralsoft.servare.ui.viewModel.state.MensajesSesion
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LoginViewModelUnitTest {
    @get:Rule val principal = ReglaDispatcherPrincipal()

    private val estado = MutableStateFlow<EstadoSesion>(EstadoSesion.SinSesion)
    private val repo = mockk<SesionRepository>().also { every { it.estado } returns estado }
    private val sinConexion = MutableStateFlow<AvisoSinConexion?>(null)
    private val servicio = mockk<ServicioNotificacionEstado>().also { every { it.inicioSesionNoDisponible } returns sinConexion }

    // Diferido: el ViewModel usa viewModelScope al construirse y la regla instala Main después de crear la clase
    private val vm by lazy { LoginViewModel(repo, servicio) }

    private fun resultado(r: ResultadoInicioSesion) = coEvery { repo.iniciarSesion(any(), any()) } returns r

    private fun escribir() {
        vm.updateCorreo("a@b.com")
        vm.updateContrasena("secreta")
    }

    @Test
    fun con_la_sesion_expirada_el_correo_queda_fijo_y_escrito() = runTest(principal.dispatcher) {
        estado.value = EstadoSesion.Expirada("cuenta@b.com")
        backgroundScope.launch { vm.state.collect {} }

        assertEquals("cuenta@b.com", vm.correoFijo)
        assertEquals("cuenta@b.com", vm.state.value.correo)
    }

    @Test
    fun sin_sesion_expirada_el_correo_es_libre() = runTest(principal.dispatcher) {
        estado.value = EstadoSesion.ConSesion("x@y.com")
        backgroundScope.launch { vm.state.collect {} }

        assertNull(vm.correoFijo)
        assertEquals("", vm.state.value.correo)
    }

    @Test
    fun enviar_manda_lo_escrito_y_deja_enviando_si_se_autentico() = runTest(principal.dispatcher) {
        backgroundScope.launch { vm.state.collect {} }
        resultado(ResultadoInicioSesion.Autenticado)
        escribir()

        vm.enviar()

        coVerify { repo.iniciarSesion("a@b.com", "secreta") }
        assertTrue("la navegación la hace la sesión, no se permite repetir el envío", vm.state.value.enviando)
    }

    @Test
    fun cada_resultado_se_explica_en_su_campo_o_en_un_mensaje() = runTest(principal.dispatcher) {
        backgroundScope.launch { vm.state.collect {} }
        escribir()
        data class Caso(val resultado: ResultadoInicioSesion, val errorCorreo: String? = null, val errorContrasena: String? = null, val mensaje: MensajeSesion? = null)
        val casos = listOf(
            Caso(ResultadoInicioSesion.CorreoInvalido, errorCorreo = "Escribe un correo válido"),
            Caso(ResultadoInicioSesion.FaltaContrasena, errorContrasena = "Escribe tu contraseña"),
            Caso(ResultadoInicioSesion.CredencialesInvalidas, errorContrasena = "Correo o contraseña incorrectos"),
            Caso(ResultadoInicioSesion.SinConexion(EstadoConectividad.SIN_RED), mensaje = MensajesSesion.sinConexion),
            Caso(ResultadoInicioSesion.SinRespuesta, mensaje = MensajesSesion.sinRespuesta),
            Caso(ResultadoInicioSesion.Rechazado, mensaje = MensajesSesion.rechazado),
            Caso(ResultadoInicioSesion.ErrorServidor, mensaje = MensajesSesion.errorServidor)
        )
        for (caso in casos) {
            resultado(caso.resultado)
            vm.enviar()

            val s = vm.state.value
            assertEquals("${caso.resultado}", caso.errorCorreo, s.errorCorreo)
            assertEquals("${caso.resultado}", caso.errorContrasena, s.errorContrasena)
            assertEquals("${caso.resultado}", caso.mensaje, s.mensaje)
            assertFalse("${caso.resultado}: se puede volver a intentar", s.enviando)
            vm.mensajeMostrado()
            vm.updateCorreo("a@b.com")
        }
    }

    @Test
    fun si_no_se_pudo_guardar_la_sesion_se_ofrece_reintentar() = runTest(principal.dispatcher) {
        backgroundScope.launch { vm.state.collect {} }
        resultado(ResultadoInicioSesion.ErrorAlmacenamiento)
        escribir()

        vm.enviar()

        val mensaje = vm.state.value.mensaje!!
        assertTrue(mensaje.reintentable)
        assertTrue(mensaje.texto.contains("no se pudo guardar la sesión"))
        assertFalse(vm.state.value.enviando)
    }

    @Test
    fun editar_cualquier_campo_quita_el_error_de_credenciales_incorrectas() = runTest(principal.dispatcher) {
        backgroundScope.launch { vm.state.collect {} }
        resultado(ResultadoInicioSesion.CredencialesInvalidas)
        escribir()
        vm.enviar()
        assertEquals("Correo o contraseña incorrectos", vm.state.value.errorContrasena)

        vm.updateCorreo("otro@b.com")

        assertNull(vm.state.value.errorContrasena)
    }

    @Test
    fun sin_conexion_no_se_envia_y_mientras_se_envia_tampoco_se_repite() = runTest(principal.dispatcher) {
        backgroundScope.launch { vm.state.collect {} }
        escribir()
        sinConexion.value = AvisoSinConexion("Iniciar sesión requiere conexión", "sin red")
        vm.enviar()
        coVerify(exactly = 0) { repo.iniciarSesion(any(), any()) }

        sinConexion.value = null
        val respuesta = CompletableDeferred<ResultadoInicioSesion>()
        coEvery { repo.iniciarSesion(any(), any()) } coAnswers { respuesta.await() }
        vm.enviar()
        vm.enviar()
        coVerify(exactly = 1) { repo.iniciarSesion(any(), any()) }

        respuesta.complete(ResultadoInicioSesion.SinRespuesta)
        assertFalse(vm.state.value.enviando)
    }
}
