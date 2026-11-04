package com.spiralsoft.servare.ui.viewModel

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import com.spiralsoft.servare.domain.model.EstadoSesion
import com.spiralsoft.servare.domain.repository.ResultadoCierreSesion
import com.spiralsoft.servare.testutil.ReglaDispatcherPrincipal
import com.spiralsoft.servare.testutil.SesionRepositoryFalso
import com.spiralsoft.servare.ui.viewModel.state.CierreSesionState
import com.spiralsoft.servare.ui.viewModel.state.ConfirmacionCierre

class CierreSesionViewModelTest {

    @get:Rule
    val principal = ReglaDispatcherPrincipal()

    private val repositorio = SesionRepositoryFalso(EstadoSesion.ConSesion("a@b.mx"))
    // Diferido: el ViewModel usa viewModelScope al construirse, y la regla instala Main después de crear la clase
    private val vm by lazy { CierreSesionViewModel(repositorio) }

    @Test
    fun el_correo_sigue_a_la_sesion_incluso_expirada() = runTest(principal.dispatcher) {
        backgroundScope.launch { vm.correo.collect {} }
        assertEquals("a@b.mx", vm.correo.value)

        repositorio.estado.value = EstadoSesion.Expirada("a@b.mx")
        assertEquals("a@b.mx", vm.correo.value)

        repositorio.estado.value = EstadoSesion.SinSesion
        assertNull(vm.correo.value)
    }

    @Test
    fun solicitar_cuenta_los_cambios_sin_enviar_antes_de_pedir_confirmacion() = runTest(principal.dispatcher) {
        repositorio.cambiosSinEnviar = 3

        vm.solicitar()

        assertEquals(ConfirmacionCierre(3), vm.state.value.confirmacion)
        assertEquals(0, repositorio.cierres) // pedir confirmación no cierra nada
    }

    @Test
    fun si_no_se_pudo_contar_se_confirma_sin_cifra() = runTest(principal.dispatcher) {
        repositorio.cambiosSinEnviar = null

        vm.solicitar()

        assertEquals(ConfirmacionCierre(null), vm.state.value.confirmacion)
    }

    @Test
    fun solicitar_con_el_dialogo_abierto_no_vuelve_a_contar() = runTest(principal.dispatcher) {
        vm.solicitar()
        vm.solicitar()

        assertEquals(1, repositorio.cuentasDeCambios)
    }

    @Test
    fun cancelar_cierra_el_dialogo() = runTest(principal.dispatcher) {
        vm.solicitar()

        vm.cancelar()

        assertEquals(CierreSesionState(), vm.state.value)
    }

    @Test
    fun confirmar_cierra_la_sesion_y_se_queda_cerrando_para_no_repetirse() = runTest(principal.dispatcher) {
        vm.solicitar()

        vm.confirmar()

        assertEquals(1, repositorio.cierres)
        assertTrue(vm.state.value.cerrando)
        assertNull(vm.state.value.error)
    }

    @Test
    fun si_el_cierre_falla_se_puede_reintentar_con_un_error_visible() = runTest(principal.dispatcher) {
        repositorio.alCerrar = { ResultadoCierreSesion.ErrorAlmacenamiento }
        vm.solicitar()

        vm.confirmar()

        assertFalse(vm.state.value.cerrando)
        assertEquals("No se pudo cerrar la sesión. Intenta de nuevo.", vm.state.value.error)
        assertTrue(vm.state.value.confirmacion != null) // el diálogo sigue abierto

        repositorio.alCerrar = { ResultadoCierreSesion.Cerrada }
        vm.confirmar()

        assertEquals(2, repositorio.cierres)
        assertNull(vm.state.value.error)
        assertTrue(vm.state.value.cerrando)
    }

    @Test
    fun mientras_se_cierra_ni_se_repite_ni_se_cancela() = runTest(principal.dispatcher) {
        val terminar = CompletableDeferred<ResultadoCierreSesion>()
        repositorio.alCerrar = { terminar.await() }
        vm.solicitar()

        vm.confirmar()
        vm.confirmar()
        vm.cancelar()

        assertEquals(1, repositorio.cierres)
        assertTrue(vm.state.value.cerrando)
        assertTrue(vm.state.value.confirmacion != null)

        terminar.complete(ResultadoCierreSesion.ErrorAlmacenamiento)
        assertFalse(vm.state.value.cerrando)
    }
}
