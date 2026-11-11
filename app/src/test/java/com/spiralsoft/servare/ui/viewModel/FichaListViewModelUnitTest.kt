package com.spiralsoft.servare.ui.viewModel

import com.spiralsoft.servare.domain.model.AvisoDescargaInicial
import com.spiralsoft.servare.domain.model.AvisoResultado
import com.spiralsoft.servare.domain.model.FichaResumen
import com.spiralsoft.servare.domain.model.TipoEspacio
import com.spiralsoft.servare.domain.repository.FichaRepository
import com.spiralsoft.servare.domain.repository.ResultadoEliminacionFicha
import com.spiralsoft.servare.notification.ServicioNotificacionEstado
import com.spiralsoft.servare.testutil.ReglaDispatcherPrincipal
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

// Lo común a las listas está en ListaSincronizableViewModelUnitTest, lo propio de las fichas
class FichaListViewModelUnitTest {
    @get:Rule val principal = ReglaDispatcherPrincipal()

    private val resumenes = MutableStateFlow<List<FichaResumen>>(emptyList())
    private val repo = mockk<FichaRepository>().also {
        every { it.observarResumenes() } returns resumenes
        every { it.observarDescargaInicial() } returns MutableStateFlow(true)
    }
    private val servicio = mockk<ServicioNotificacionEstado>().also {
        every { it.resultados } returns MutableSharedFlow<AvisoResultado>(extraBufferCapacity = 1)
        every { it.descargaInicial } returns MutableStateFlow(AvisoDescargaInicial("t", "d"))
        every { it.sesionExpirada } returns MutableStateFlow(null)
        every { it.problemasOperacion } returns MutableStateFlow(emptyList())
    }
    private val vm by lazy { FichaListViewModel(repo, servicio) }

    @Test
    fun la_lista_muestra_los_resumenes_con_su_marca_de_pendiente() = runTest(principal.dispatcher) {
        val resumen = FichaResumen("f1", "Cerro", TipoEspacio.CAPILLA, "Tuapurie", "2026-10-01", pendiente = true)
        resumenes.value = listOf(resumen)
        backgroundScope.launch { vm.state.collect {} }

        assertEquals(listOf(resumen), vm.state.value.registros)
        assertFalse(vm.state.value.cargando)
    }

    @Test
    fun eliminar_nombra_la_ficha_y_si_no_tiene_nombre_usa_uno_de_reemplazo() = runTest(principal.dispatcher) {
        backgroundScope.launch { vm.state.collect {} }
        coEvery { repo.eliminar("f1") } returns ResultadoEliminacionFicha.Eliminada("Cerro")
        coEvery { repo.eliminar("f2") } returns ResultadoEliminacionFicha.Eliminada("")

        vm.eliminar("f1")
        assertEquals("«Cerro» se eliminó del dispositivo. Se eliminará del servidor al sincronizar", vm.state.value.mensaje)

        vm.eliminar("f2")
        assertEquals("«Sin nombre» se eliminó del dispositivo. Se eliminará del servidor al sincronizar", vm.state.value.mensaje)
    }

    @Test
    fun eliminar_explica_los_fallos() = runTest(principal.dispatcher) {
        backgroundScope.launch { vm.state.collect {} }
        coEvery { repo.eliminar("f1") } returns ResultadoEliminacionFicha.NoEncontrada
        coEvery { repo.eliminar("f2") } returns ResultadoEliminacionFicha.ErrorAlmacenamiento

        vm.eliminar("f1")
        assertEquals("Esta ficha ya no estaba en el dispositivo", vm.state.value.mensaje)

        vm.eliminar("f2")
        assertEquals("No se pudo eliminar la ficha. Intenta de nuevo.", vm.state.value.mensaje)
    }
}
