package com.spiralsoft.servare.ui.viewModel

import androidx.lifecycle.SavedStateHandle
import com.spiralsoft.servare.domain.model.Ficha
import com.spiralsoft.servare.domain.repository.FichaRepository
import com.spiralsoft.servare.domain.repository.ResultadoGuardadoFicha
import com.spiralsoft.servare.domain.repository.ResultadoObtencionFicha
import com.spiralsoft.servare.testutil.ReglaDispatcherPrincipal
import com.spiralsoft.servare.testutil.fichaLlena
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class FichaFormViewModelUnitTest {
    @get:Rule val principal = ReglaDispatcherPrincipal()

    private val repo = mockk<FichaRepository>()
    private val existente = fichaLlena("Cerro")

    private fun crear() = FichaFormViewModel(repo, SavedStateHandle())

    private fun editar(): FichaFormViewModel {
        coEvery { repo.obtener("f1") } returns ResultadoObtencionFicha.Encontrada(existente)
        return FichaFormViewModel(repo, SavedStateHandle(mapOf("entityId" to "f1")))
    }

    // --- Apertura ---

    @Test
    fun al_crear_abre_vacio_sin_cargar_y_sin_cambios() {
        val form = crear().form.value

        assertNull(form.entityId)
        assertFalse(form.cargando)
        assertFalse(form.hayCambios)
        assertEquals(Ficha(), form.ficha)
    }

    @Test
    fun al_editar_carga_la_ficha_y_la_toma_como_original() = runTest(principal.dispatcher) {
        val form = editar().form.value

        assertEquals(existente, form.ficha)
        assertEquals(existente, form.fichaOriginal)
        assertFalse(form.cargando)
        assertFalse(form.hayCambios)
    }

    @Test
    fun mientras_carga_el_formulario_esta_marcado_y_no_guarda() = runTest(principal.dispatcher) {
        val lectura = CompletableDeferred<ResultadoObtencionFicha>()
        coEvery { repo.obtener("f1") } coAnswers { lectura.await() }
        val vm = FichaFormViewModel(repo, SavedStateHandle(mapOf("entityId" to "f1")))
        assertTrue(vm.form.value.cargando)

        vm.guardar()

        coVerify(exactly = 0) { repo.actualizar(any(), any()) }
        lectura.complete(ResultadoObtencionFicha.Encontrada(existente))
        assertFalse(vm.form.value.cargando)
    }

    @Test
    fun si_la_ficha_ya_no_existe_o_no_se_pudo_leer_se_explica_y_no_se_guarda() = runTest(principal.dispatcher) {
        val casos = mapOf(
            ResultadoObtencionFicha.NoEncontrada to "Esta ficha ya no está en el dispositivo",
            ResultadoObtencionFicha.ErrorAlmacenamiento to "No se pudo abrir la ficha. Intenta de nuevo."
        )
        for ((resultado, texto) in casos) {
            coEvery { repo.obtener("f1") } returns resultado
            val vm = FichaFormViewModel(repo, SavedStateHandle(mapOf("entityId" to "f1")))

            assertEquals(texto, vm.form.value.errorCarga)
            assertFalse(vm.form.value.cargando)
            vm.guardar()
        }
        coVerify(exactly = 0) { repo.actualizar(any(), any()) }
    }

    // --- Edición ---

    @Test
    fun editar_un_campo_marca_cambios_y_volver_al_original_los_quita() = runTest(principal.dispatcher) {
        val vm = editar()

        vm.updateFicha { it.copy(comunidad = "Otra") }
        assertTrue(vm.form.value.hayCambios)

        vm.updateFicha { it.copy(comunidad = existente.comunidad) }
        assertFalse(vm.form.value.hayCambios)
    }

    @Test
    fun el_error_del_nombre_se_quita_al_escribirlo_pero_no_mientras_siga_vacio() = runTest(principal.dispatcher) {
        coEvery { repo.guardar(any()) } returns ResultadoGuardadoFicha.FaltaNombre
        val vm = crear()
        vm.guardar()
        assertEquals("Escribe el nombre del lugar", vm.form.value.errorNombre)

        vm.updateFicha { it.copy(comunidad = "X") }
        assertEquals("sigue sin nombre", "Escribe el nombre del lugar", vm.form.value.errorNombre)

        vm.updateFicha { it.copy(nombre = "Cerro") }
        assertNull(vm.form.value.errorNombre)
    }

    // --- Guardado ---

    @Test
    fun guardar_una_ficha_nueva_la_envia_al_repositorio_y_cierra_con_mensaje() = runTest(principal.dispatcher) {
        coEvery { repo.guardar(any()) } returns ResultadoGuardadoFicha.Guardada("nuevo")
        val vm = crear()
        vm.updateFicha { it.copy(nombre = "Cerro") }

        vm.guardar()

        coVerify { repo.guardar(Ficha(nombre = "Cerro")) }
        assertEquals("Ficha guardada en el dispositivo", vm.form.value.mensajeSalida)
        assertFalse(vm.form.value.guardando)
    }

    @Test
    fun guardar_una_edicion_actualiza_la_misma_ficha() = runTest(principal.dispatcher) {
        coEvery { repo.actualizar("f1", any()) } returns ResultadoGuardadoFicha.Guardada("f1")
        val vm = editar()
        vm.updateFicha { it.copy(comunidad = "Otra") }

        vm.guardar()

        coVerify { repo.actualizar("f1", existente.copy(comunidad = "Otra")) }
        coVerify(exactly = 0) { repo.guardar(any()) }
        assertEquals("Cambios guardados en el dispositivo", vm.form.value.mensajeSalida)
    }

    @Test
    fun editar_una_ficha_que_se_elimino_no_guarda_y_lo_dice() = runTest(principal.dispatcher) {
        coEvery { repo.actualizar("f1", any()) } returns ResultadoGuardadoFicha.NoEncontrada
        val vm = editar()

        vm.guardar()

        assertEquals("Esta ficha se eliminó del dispositivo, los cambios no se guardaron", vm.form.value.mensajeSalida)
    }

    @Test
    fun sin_nombre_el_formulario_sigue_abierto_con_el_error_y_un_mensaje() = runTest(principal.dispatcher) {
        coEvery { repo.guardar(any()) } returns ResultadoGuardadoFicha.FaltaNombre
        val vm = crear()

        vm.guardar()

        assertEquals("Falta el nombre del lugar", vm.form.value.mensaje)
        assertNull(vm.form.value.mensajeSalida)
        assertFalse(vm.form.value.guardando)
    }

    @Test
    fun los_fallos_de_almacenamiento_se_explican_y_dejan_reintentar() = runTest(principal.dispatcher) {
        val vm = crear()
        val casos = mapOf(
            ResultadoGuardadoFicha.SinEspacio to "No hay espacio en el dispositivo. Libera espacio e intenta de nuevo.",
            ResultadoGuardadoFicha.ErrorAlmacenamiento to "No se pudo guardar la ficha en el dispositivo. Intenta de nuevo."
        )
        for ((resultado, texto) in casos) {
            coEvery { repo.guardar(any()) } returns resultado
            vm.guardar()
            assertEquals(texto, vm.form.value.mensaje)
            assertNull(vm.form.value.mensajeSalida)
            assertFalse("se puede reintentar", vm.form.value.guardando)
            vm.mensajeMostrado()
        }
    }

    @Test
    fun un_segundo_toque_mientras_se_guarda_no_duplica_la_ficha() = runTest(principal.dispatcher) {
        val respuesta = CompletableDeferred<ResultadoGuardadoFicha>()
        coEvery { repo.guardar(any()) } coAnswers { respuesta.await() }
        val vm = crear()

        vm.guardar()
        vm.guardar()
        assertTrue(vm.form.value.guardando)

        respuesta.complete(ResultadoGuardadoFicha.Guardada("x"))
        coVerify(exactly = 1) { repo.guardar(any()) }
    }

    @Test
    fun el_mensaje_se_muestra_una_sola_vez() = runTest(principal.dispatcher) {
        coEvery { repo.guardar(any()) } returns ResultadoGuardadoFicha.SinEspacio
        val vm = crear()
        vm.guardar()
        assertTrue(vm.form.value.mensaje != null)

        vm.mensajeMostrado()

        assertNull(vm.form.value.mensaje)
    }
}
