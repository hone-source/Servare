package com.spiralsoft.servare.ui.viewModel

import com.spiralsoft.servare.data.mapa.GestorMapaOffline
import com.spiralsoft.servare.domain.model.AccionOperacion
import com.spiralsoft.servare.domain.model.AvisoDescargaInicial
import com.spiralsoft.servare.domain.model.AvisoResultado
import com.spiralsoft.servare.domain.model.AvisoSesionExpirada
import com.spiralsoft.servare.domain.model.EstadoRegionMapa
import com.spiralsoft.servare.domain.model.Punto
import com.spiralsoft.servare.domain.repository.PuntoRepository
import com.spiralsoft.servare.domain.repository.ResultadoEliminacionPunto
import com.spiralsoft.servare.domain.repository.ResultadoGuardadoPunto
import com.spiralsoft.servare.domain.repository.ResultadoRefresco
import com.spiralsoft.servare.domain.repository.ResultadoResolucion
import com.spiralsoft.servare.notification.ServicioNotificacionEstado
import com.spiralsoft.servare.testutil.ReglaDispatcherPrincipal
import com.spiralsoft.servare.ui.viewModel.state.DialogoPunto
import com.spiralsoft.servare.ui.viewModel.state.PuntoNuevo
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MapaViewModelUnitTest {
    @get:Rule val principal = ReglaDispatcherPrincipal()

    private val gestor = mockk<GestorMapaOffline>()
    private val puntos = mockk<PuntoRepository>()
    private val lista = MutableStateFlow<List<Punto>>(emptyList())
    private val descargaCompleta = MutableStateFlow(value = true)
    private val resultados = MutableSharedFlow<AvisoResultado>(extraBufferCapacity = 1)
    private val sesionExpirada = MutableStateFlow<AvisoSesionExpirada?>(null)
    private val aviso = AvisoDescargaInicial("Aún no se descargaron", "sin red")
    private val servicio = mockk<ServicioNotificacionEstado>().also {
        every { it.resultados } returns resultados
        every { it.sesionExpirada } returns sesionExpirada
        every { it.problemasOperacion } returns MutableStateFlow(emptyList())
        every { it.descargaInicial } returns MutableStateFlow(aviso)
        every { it.textoEliminadosEnServidor(any()) } returns "quitados"
    }

    // Valores por defecto: cada prueba los cambia antes de crear el ViewModel, que arranca al primer uso
    init {
        coEvery { gestor.estado(any()) } returns EstadoRegionMapa.NoDescargada
        every { puntos.observarTodos() } returns lista
        every { puntos.observarDescargaInicial() } returns descargaCompleta
    }

    private val vm by lazy { MapaViewModel(gestor, puntos, servicio) }

    private val punto = Punto("p1", "Mirador", 22.77, -102.57, 1)

    // --- Arranque ---

    @Test
    fun al_abrir_comprueba_el_estado_del_mapa_y_muestra_los_puntos() = runTest(principal.dispatcher) {
        coEvery { gestor.estado(any()) } returns EstadoRegionMapa.Completa(1_000)
        lista.value = listOf(punto)

        assertEquals(listOf(punto), vm.state.value.puntos)
        assertEquals(EstadoRegionMapa.Completa(1_000), vm.state.value.region)
    }

    @Test
    fun si_no_se_puede_leer_el_estado_del_mapa_se_toma_como_no_descargado() = runTest(principal.dispatcher) {
        every { puntos.observarTodos() } returns lista
        every { puntos.observarDescargaInicial() } returns descargaCompleta
        coEvery { gestor.estado(any()) } throws IllegalStateException("sin SDK")

        assertEquals(EstadoRegionMapa.NoDescargada, MapaViewModel(gestor, puntos, servicio).state.value.region)
    }

    @Test
    fun si_falla_la_lectura_de_puntos_se_avisa_y_no_se_cae() = runTest(principal.dispatcher) {
        coEvery { gestor.estado(any()) } returns EstadoRegionMapa.NoDescargada
        every { puntos.observarDescargaInicial() } returns descargaCompleta
        every { puntos.observarTodos() } returns flow { throw android.database.sqlite.SQLiteException("x") }

        assertEquals("No se pudieron leer los puntos guardados.", MapaViewModel(gestor, puntos, servicio).state.value.mensaje)
    }

    @Test
    fun el_aviso_de_descarga_pendiente_solo_aparece_antes_de_la_descarga_inicial() = runTest(principal.dispatcher) {
        descargaCompleta.value = false
        assertEquals(aviso, vm.state.value.descargaPendiente)

        descargaCompleta.value = true
        assertNull(vm.state.value.descargaPendiente)
    }

    @Test
    fun los_resultados_y_la_sesion_expirada_llegan_al_estado() = runTest(principal.dispatcher) {
        vm // el ViewModel debe estar escuchando antes de que llegue el aviso
        resultados.tryEmit(AvisoResultado("Se sincronizó 1 cambio con el servidor"))
        val expirada = AvisoSesionExpirada("Tu sesión expiró", "inicia sesión")
        sesionExpirada.value = expirada

        vm.state.value.let {
            assertEquals("Se sincronizó 1 cambio con el servidor", it.mensaje)
            assertEquals(expirada, it.sesionExpirada)
        }
    }

    // --- Refresco y resolución ---

    @Test
    fun refrescar_marca_refrescando_no_se_repite_y_muestra_el_resultado() = runTest(principal.dispatcher) {
        val respuesta = CompletableDeferred<ResultadoRefresco>()
        coEvery { puntos.refrescar() } coAnswers { respuesta.await() }

        vm.refrescar()
        vm.refrescar()
        assertTrue(vm.state.value.refrescando)

        respuesta.complete(ResultadoRefresco.Actualizada(emptyList()))
        assertFalse(vm.state.value.refrescando)
        assertEquals("Lista actualizada", vm.state.value.mensaje)
        coVerify(exactly = 1) { puntos.refrescar() }
    }

    @Test
    fun resolver_explica_el_resultado() = runTest(principal.dispatcher) {
        coEvery { puntos.resolver("op", AccionOperacion.DESCARTAR) } returns ResultadoResolucion.Resuelta(AccionOperacion.DESCARTAR)

        vm.resolver("op", AccionOperacion.DESCARTAR)

        assertEquals("El cambio se descartó", vm.state.value.mensaje)
    }

    // --- Mapa sin conexión ---

    @Test
    fun descargar_muestra_el_avance_y_deja_el_estado_final() = runTest(principal.dispatcher) {
        coEvery { gestor.descargar(any()) } returns flowOf(
            EstadoRegionMapa.Descargando(10, 100), EstadoRegionMapa.Descargando(100, 100), EstadoRegionMapa.Completa(5_000),
        )

        vm.descargar()

        assertEquals(EstadoRegionMapa.Completa(5_000), vm.state.value.region)
    }

    @Test
    fun si_la_descarga_falla_conserva_lo_descargado_y_explica() = runTest(principal.dispatcher) {
        coEvery { gestor.descargar(any()) } returns flow {
            emit(EstadoRegionMapa.Descargando(30, 100))
            throw RuntimeException("red")
        }
        coEvery { gestor.estado(any()) } returns EstadoRegionMapa.Parcial(30, 100)

        vm.descargar()

        assertEquals(EstadoRegionMapa.Parcial(30, 100), vm.state.value.region)
        assertTrue(vm.state.value.mensaje!!.startsWith("No se pudo descargar"))
    }

    @Test
    fun una_segunda_descarga_mientras_corre_la_primera_se_ignora() = runTest(principal.dispatcher) {
        val sinFin = CompletableDeferred<Unit>()
        coEvery { gestor.descargar(any()) } returns flow {
            emit(EstadoRegionMapa.Descargando(1, 100))
            sinFin.await()
        }

        vm.descargar()
        vm.descargar()

        io.mockk.verify(exactly = 1) { gestor.descargar(any()) }
        sinFin.complete(Unit)
    }

    @Test
    fun eliminar_el_mapa_cancela_la_descarga_y_avisa() = runTest(principal.dispatcher) {
        coEvery { gestor.eliminar(any()) } returns Unit
        coEvery { gestor.estado(any()) } returns EstadoRegionMapa.NoDescargada

        vm.eliminar()

        assertEquals(EstadoRegionMapa.NoDescargada, vm.state.value.region)
        assertTrue(vm.state.value.mensaje!!.startsWith("Se quitó el mapa"))
    }

    @Test
    fun si_no_se_pudo_quitar_el_mapa_lo_dice() = runTest(principal.dispatcher) {
        coEvery { gestor.eliminar(any()) } throws IllegalStateException("x")

        vm.eliminar()

        assertTrue(vm.state.value.mensaje!!.startsWith("No se pudo quitar el mapa"))
    }

    // --- Puntos ---

    @Test
    fun proponer_y_cancelar_un_punto_nuevo() = runTest(principal.dispatcher) {
        vm.proponerPunto(22.0, -102.0)
        assertEquals(PuntoNuevo(22.0, -102.0), vm.state.value.puntoNuevo)

        vm.cancelarPunto()
        assertNull(vm.state.value.puntoNuevo)
    }

    @Test
    fun guardar_sin_punto_propuesto_no_hace_nada() = runTest(principal.dispatcher) {
        vm.guardarPunto("Nombre")
        coVerify(exactly = 0) { puntos.guardar(any(), any(), any()) }
    }

    @Test
    fun guardar_un_punto_cierra_el_dialogo_y_confirma_con_el_nombre() = runTest(principal.dispatcher) {
        coEvery { puntos.guardar("Mirador", 22.0, -102.0) } returns ResultadoGuardadoPunto.Guardado("Mirador")
        vm.proponerPunto(22.0, -102.0)

        vm.guardarPunto("Mirador")

        assertNull(vm.state.value.puntoNuevo)
        assertEquals("«Mirador» se guardó en este dispositivo", vm.state.value.mensaje)
    }

    @Test
    fun sin_nombre_el_dialogo_sigue_abierto_para_completar_el_dato() = runTest(principal.dispatcher) {
        coEvery { puntos.guardar(any(), any(), any()) } returns ResultadoGuardadoPunto.FaltaNombre
        vm.proponerPunto(22.0, -102.0)

        vm.guardarPunto("")

        assertEquals(PuntoNuevo(22.0, -102.0), vm.state.value.puntoNuevo)
        assertEquals("Escribe un nombre para el punto", vm.state.value.mensaje)
    }

    @Test
    fun los_fallos_de_almacenamiento_cierran_el_dialogo_con_un_mensaje_que_se_ve() = runTest(principal.dispatcher) {
        val esperados = mapOf(
            ResultadoGuardadoPunto.SinEspacio to "No hay espacio en el dispositivo",
            ResultadoGuardadoPunto.ErrorAlmacenamiento to "No se pudo guardar el punto",
            ResultadoGuardadoPunto.CoordenadasInvalidas to "No se pudo guardar el punto",
            ResultadoGuardadoPunto.NoEncontrado to "No se pudo guardar el punto"
        )
        for ((resultado, texto) in esperados) {
            coEvery { puntos.guardar(any(), any(), any()) } returns resultado
            vm.proponerPunto(1.0, 1.0)
            vm.guardarPunto("P")
            assertNull(resultado.toString(), vm.state.value.puntoNuevo)
            assertTrue(resultado.toString(), vm.state.value.mensaje!!.startsWith(texto))
            vm.mensajeMostrado()
        }
    }

    @Test
    fun los_dialogos_de_un_punto_se_abren_y_se_cierran() = runTest(principal.dispatcher) {
        vm.verPunto("p1")
        assertEquals(DialogoPunto.Detalle("p1"), vm.state.value.dialogo)
        vm.editarPunto("p1")
        assertEquals(DialogoPunto.Editar("p1"), vm.state.value.dialogo)
        vm.pedirEliminarPunto("p1")
        assertEquals(DialogoPunto.ConfirmarEliminar("p1"), vm.state.value.dialogo)
        vm.cerrarDialogo()
        assertNull(vm.state.value.dialogo)
    }

    @Test
    fun actualizar_un_punto_cierra_el_dialogo_salvo_si_el_dato_se_puede_corregir() = runTest(principal.dispatcher) {
        vm.editarPunto("p1")
        coEvery { puntos.actualizar("p1", "Nuevo", 1.0, 2.0) } returns ResultadoGuardadoPunto.Guardado("Nuevo")
        vm.actualizarPunto("p1", "Nuevo", 1.0, 2.0)
        assertNull(vm.state.value.dialogo)
        assertEquals("«Nuevo» se actualizó en este dispositivo", vm.state.value.mensaje)

        for ((resultado, texto) in mapOf(
            ResultadoGuardadoPunto.FaltaNombre to "Escribe un nombre para el punto",
            ResultadoGuardadoPunto.CoordenadasInvalidas to "La latitud va de −90 a 90 y la longitud de −180 a 180"
        )) {
            vm.editarPunto("p1")
            coEvery { puntos.actualizar(any(), any(), any(), any()) } returns resultado
            vm.actualizarPunto("p1", "", 999.0, 0.0)
            assertEquals("$resultado: sigue abierto para corregir", DialogoPunto.Editar("p1"), vm.state.value.dialogo)
            assertEquals(texto, vm.state.value.mensaje)
        }
    }

    @Test
    fun actualizar_un_punto_que_ya_no_existe_o_sin_espacio_cierra_y_explica() = runTest(principal.dispatcher) {
        val esperados = mapOf(
            ResultadoGuardadoPunto.NoEncontrado to "El punto ya no existe en este dispositivo",
            ResultadoGuardadoPunto.SinEspacio to "No hay espacio en el dispositivo",
            ResultadoGuardadoPunto.ErrorAlmacenamiento to "No se pudieron guardar los cambios del punto"
        )
        for ((resultado, texto) in esperados) {
            vm.editarPunto("p1")
            coEvery { puntos.actualizar(any(), any(), any(), any()) } returns resultado
            vm.actualizarPunto("p1", "N", 1.0, 1.0)
            assertNull("$resultado", vm.state.value.dialogo)
            assertTrue("$resultado", vm.state.value.mensaje!!.startsWith(texto))
        }
    }

    @Test
    fun eliminar_un_punto_cierra_el_dialogo_y_explica_cada_resultado() = runTest(principal.dispatcher) {
        val esperados = mapOf(
            ResultadoEliminacionPunto.Eliminado("Mirador") to "«Mirador» se eliminó de este dispositivo",
            ResultadoEliminacionPunto.NoEncontrado to "El punto ya no existe en este dispositivo",
            ResultadoEliminacionPunto.ErrorAlmacenamiento to "No se pudo eliminar el punto. Inténtalo de nuevo."
        )
        for ((resultado, texto) in esperados) {
            vm.pedirEliminarPunto("p1")
            coEvery { puntos.eliminar("p1") } returns resultado
            vm.eliminarPunto("p1")
            assertNull("$resultado", vm.state.value.dialogo)
            assertEquals(texto, vm.state.value.mensaje)
        }
    }

    @Test
    fun el_mensaje_se_muestra_una_sola_vez() = runTest(principal.dispatcher) {
        coEvery { puntos.eliminar(any()) } returns ResultadoEliminacionPunto.NoEncontrado
        vm.eliminarPunto("p1")
        assertTrue(vm.state.value.mensaje != null)

        vm.mensajeMostrado()

        assertNull(vm.state.value.mensaje)
    }
}
