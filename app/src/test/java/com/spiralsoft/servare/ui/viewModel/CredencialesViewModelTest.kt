package com.spiralsoft.servare.ui.viewModel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import com.spiralsoft.servare.domain.model.AvisoSinConexion
import com.spiralsoft.servare.testutil.ReglaDispatcherPrincipal
import com.spiralsoft.servare.ui.viewModel.state.MensajeSesion

class CredencialesViewModelTest {

    @get:Rule
    val principal = ReglaDispatcherPrincipal()

    private class ViewModelDePrueba(aviso: MutableStateFlow<AvisoSinConexion?>) : CredencialesViewModel(aviso) {
        var ediciones = 0
        override fun alEditar() {
            ediciones++
        }

        override fun enviar() = Unit

        fun fallar(campo: String) = formulario.update {
            it.copy(errorCorreo = campo, errorContrasena = campo, mensaje = MensajeSesion("fallo"))
        }

        fun enviando() = formulario.update { it.copy(enviando = true) }
    }

    private val aviso = MutableStateFlow<AvisoSinConexion?>(null)
    // Diferido: el ViewModel usa viewModelScope al construirse, y la regla instala Main después de crear la clase
    private val vm by lazy { ViewModelDePrueba(aviso) }

    @Test
    fun al_editar_el_correo_se_limpia_su_error_y_se_avisa_a_la_subclase() = runTest(principal.dispatcher) {
        backgroundScope.launch { vm.state.collect {} }
        vm.fallar("mal")

        vm.updateCorreo("a@b.mx")

        assertEquals("a@b.mx", vm.state.value.correo)
        assertNull(vm.state.value.errorCorreo)
        assertEquals("mal", vm.state.value.errorContrasena) // el otro campo no se toca
        assertEquals(1, vm.ediciones)
    }

    @Test
    fun al_editar_la_contrasena_se_limpia_su_error() = runTest(principal.dispatcher) {
        backgroundScope.launch { vm.state.collect {} }
        vm.fallar("mal")

        vm.updateContrasena("secreta123")

        assertEquals("secreta123", vm.state.value.contrasena)
        assertNull(vm.state.value.errorContrasena)
        assertEquals("mal", vm.state.value.errorCorreo)
        assertEquals(1, vm.ediciones)
    }

    @Test
    fun la_visibilidad_de_la_contrasena_se_alterna() = runTest(principal.dispatcher) {
        backgroundScope.launch { vm.state.collect {} }
        assertFalse(vm.state.value.contrasenaVisible)

        vm.cambiarVisibilidad()
        assertTrue(vm.state.value.contrasenaVisible)

        vm.cambiarVisibilidad()
        assertFalse(vm.state.value.contrasenaVisible)
    }

    @Test
    fun el_mensaje_se_muestra_una_sola_vez() = runTest(principal.dispatcher) {
        backgroundScope.launch { vm.state.collect {} }
        vm.fallar("mal")
        assertEquals(MensajeSesion("fallo"), vm.state.value.mensaje)

        vm.mensajeMostrado()

        assertNull(vm.state.value.mensaje)
    }

    @Test
    fun el_aviso_de_conexion_impide_enviar_hasta_que_desaparece() = runTest(principal.dispatcher) {
        backgroundScope.launch { vm.state.collect {} }
        assertTrue(vm.state.value.puedeEnviar)

        val sinRed = AvisoSinConexion("Sin conexión", "Conéctate para continuar")
        aviso.value = sinRed
        assertEquals(sinRed, vm.state.value.sinConexion)
        assertFalse(vm.state.value.puedeEnviar)

        aviso.value = null
        assertTrue(vm.state.value.puedeEnviar)
    }

    @Test
    fun mientras_se_envia_no_se_puede_enviar_otra_vez() = runTest(principal.dispatcher) {
        backgroundScope.launch { vm.state.collect {} }

        vm.enviando()

        assertFalse(vm.state.value.puedeEnviar)
    }
}
