package com.spiralsoft.servare.ui.viewModel

import com.spiralsoft.servare.domain.model.AccionOperacion
import com.spiralsoft.servare.domain.model.AvisoDescargaInicial
import com.spiralsoft.servare.domain.model.AvisoOperacion
import com.spiralsoft.servare.domain.model.AvisoResultado
import com.spiralsoft.servare.domain.model.AvisoSesionExpirada
import com.spiralsoft.servare.domain.model.EstadoOperacion
import com.spiralsoft.servare.domain.model.Prueba
import com.spiralsoft.servare.domain.repository.PruebaRepository
import com.spiralsoft.servare.domain.repository.ResultadoEliminacionPrueba
import com.spiralsoft.servare.domain.repository.ResultadoRefresco
import com.spiralsoft.servare.domain.repository.ResultadoResolucion
import com.spiralsoft.servare.notification.ServicioNotificacionEstado
import com.spiralsoft.servare.testutil.ReglaDispatcherPrincipal
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

// La lista de cualquier tipo de registro, probada con el molde (PruebaListViewModel)
class ListaSincronizableViewModelUnitTest {
    @get:Rule val principal = ReglaDispatcherPrincipal()

    private val registros = MutableStateFlow<List<Prueba>>(emptyList())
    private val descargaCompleta = MutableStateFlow(value = false)
    private val repo = mockk<PruebaRepository>().also {
        every { it.observarTodas() } returns registros
        every { it.observarDescargaInicial() } returns descargaCompleta
    }
    private val resultados = MutableSharedFlow<AvisoResultado>(extraBufferCapacity = 1)
    private val descargaInicial = MutableStateFlow(AvisoDescargaInicial("Aún no se descargaron", "sin red"))
    private val sesionExpirada = MutableStateFlow<AvisoSesionExpirada?>(null)
    private val problemas = MutableStateFlow<List<AvisoOperacion>>(emptyList())
    private val servicio = mockk<ServicioNotificacionEstado>().also {
        every { it.resultados } returns resultados
        every { it.descargaInicial } returns descargaInicial
        every { it.sesionExpirada } returns sesionExpirada
        every { it.problemasOperacion } returns problemas
        every { it.textoEliminadosEnServidor(any()) } answers { "quitados: ${firstArg<List<String>>()}" }
    }
    private val vm by lazy { PruebaListViewModel(repo, servicio) }

    private val unaPrueba = Prueba("e1", "Título", "texto", 1, 1)

    @Test
    fun la_lista_muestra_los_registros_locales_y_ya_no_esta_cargando() = runTest(principal.dispatcher) {
        registros.value = listOf(unaPrueba)
        backgroundScope.launch { vm.state.collect {} }

        assertFalse(vm.state.value.cargando)
        assertEquals(listOf(unaPrueba), vm.state.value.registros)
    }

    @Test
    fun el_aviso_de_descarga_solo_explica_una_lista_vacia_sin_descarga_inicial() = runTest(principal.dispatcher) {
        backgroundScope.launch { vm.state.collect {} }

        assertEquals(descargaInicial.value, vm.state.value.sinDescargar)

        registros.value = listOf(unaPrueba)
        assertNull("con datos locales se muestran siempre", vm.state.value.sinDescargar)

        registros.value = emptyList()
        descargaCompleta.value = true
        assertNull("una lista vacía tras descargar es un resultado, no un aviso", vm.state.value.sinDescargar)
    }

    @Test
    fun la_sesion_expirada_y_los_problemas_llegan_a_la_pantalla() = runTest(principal.dispatcher) {
        backgroundScope.launch { vm.state.collect {} }
        val aviso = AvisoSesionExpirada("Tu sesión expiró", "inicia sesión")
        val problema = AvisoOperacion("op", EstadoOperacion.FALLIDA, titulo = "Falló", detalle = "x")

        sesionExpirada.value = aviso
        problemas.value = listOf(problema)

        assertEquals(aviso, vm.state.value.sesionExpirada)
        assertEquals(listOf(problema), vm.state.value.problemas)
    }

    @Test
    fun el_resultado_de_un_ciclo_se_muestra_una_vez() = runTest(principal.dispatcher) {
        backgroundScope.launch { vm.state.collect {} }

        resultados.emit(AvisoResultado("Se sincronizó 1 cambio con el servidor"))
        assertEquals("Se sincronizó 1 cambio con el servidor", vm.state.value.mensaje)

        vm.mensajeMostrado()
        assertNull(vm.state.value.mensaje)
    }

    @Test
    fun refrescar_marca_refrescando_y_muestra_el_resultado() = runTest(principal.dispatcher) {
        backgroundScope.launch { vm.state.collect {} }
        val respuesta = CompletableDeferred<ResultadoRefresco>()
        coEvery { repo.refrescar() } coAnswers { respuesta.await() }

        vm.refrescar()
        assertTrue(vm.state.value.refrescando)

        respuesta.complete(ResultadoRefresco.Actualizada(emptyList()))
        assertFalse(vm.state.value.refrescando)
        assertEquals("Lista actualizada", vm.state.value.mensaje)
    }

    @Test
    fun pedir_refrescar_mientras_se_refresca_no_lanza_otro() = runTest(principal.dispatcher) {
        backgroundScope.launch { vm.state.collect {} }
        val respuesta = CompletableDeferred<ResultadoRefresco>()
        coEvery { repo.refrescar() } coAnswers { respuesta.await() }

        vm.refrescar()
        vm.refrescar()
        vm.refrescar()
        respuesta.complete(ResultadoRefresco.SinServidor)

        coVerify(exactly = 1) { repo.refrescar() }
        vm.refrescar()
        coVerify(exactly = 2) { repo.refrescar() }
    }

    @Test
    fun cada_resultado_del_refresco_tiene_su_mensaje() = runTest(principal.dispatcher) {
        backgroundScope.launch { vm.state.collect {} }
        val casos = mapOf(
            ResultadoRefresco.Actualizada(listOf("Uno")) to "quitados: [Uno]",
            ResultadoRefresco.SinSesion to "Inicia sesión para actualizar",
            ResultadoRefresco.SinServidor to "No se pudo conectar con el servidor. Se actualizará al recuperar la conexión",
            ResultadoRefresco.ErrorDescarga to "No se pudieron descargar los registros. Intenta de nuevo.",
        )
        for ((resultado, mensaje) in casos) {
            coEvery { repo.refrescar() } returns resultado
            vm.refrescar()
            assertEquals(mensaje, vm.state.value.mensaje)
            vm.mensajeMostrado()
        }
    }

    @Test
    fun resolver_delega_en_el_repositorio_y_explica_el_resultado() = runTest(principal.dispatcher) {
        backgroundScope.launch { vm.state.collect {} }
        coEvery { repo.resolver("op", AccionOperacion.REINTENTAR) } returns ResultadoResolucion.Resuelta(AccionOperacion.REINTENTAR)
        coEvery { repo.resolver("op2", AccionOperacion.DESCARTAR) } returns ResultadoResolucion.NoDisponible

        vm.resolver("op", AccionOperacion.REINTENTAR)
        assertEquals("Se volverá a intentar el envío", vm.state.value.mensaje)

        vm.resolver("op2", AccionOperacion.DESCARTAR)
        assertEquals("Este cambio ya se resolvió o cambió de estado", vm.state.value.mensaje)
    }

    @Test
    fun eliminar_nombra_el_registro_o_explica_por_que_no_se_pudo() = runTest(principal.dispatcher) {
        backgroundScope.launch { vm.state.collect {} }
        coEvery { repo.eliminar("e1") } returns ResultadoEliminacionPrueba.Eliminada("Título")
        coEvery { repo.eliminar("e2") } returns ResultadoEliminacionPrueba.NoEncontrada

        vm.eliminar("e1")
        assertTrue(vm.state.value.mensaje!!.contains("«Título»"))

        vm.eliminar("e2")
        assertEquals("Este registro ya no estaba en el dispositivo", vm.state.value.mensaje)
    }
}
