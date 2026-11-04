package com.spiralsoft.servare.ui.viewModel

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import com.spiralsoft.servare.domain.model.Prueba
import com.spiralsoft.servare.domain.repository.ResultadoGuardadoPrueba
import com.spiralsoft.servare.domain.repository.ResultadoObtencionPrueba
import com.spiralsoft.servare.testutil.PruebaRepositoryFalso
import com.spiralsoft.servare.testutil.ReglaDispatcherPrincipal

class PruebaFormViewModelTest {

    @get:Rule
    val principal = ReglaDispatcherPrincipal()

    private val repositorio = PruebaRepositoryFalso()
    private val existente = Prueba(entityId = "p1", titulo = "Título", texto = "Texto", numero = 7, createdAt = 1L)

    private fun crear() = PruebaFormViewModel(repositorio, SavedStateHandle())

    private fun editar(): PruebaFormViewModel {
        repositorio.alObtener = { ResultadoObtencionPrueba.Encontrada(existente) }
        return PruebaFormViewModel(repositorio, SavedStateHandle(mapOf("entityId" to "p1")))
    }

    // ---- apertura ----

    @Test
    fun al_crear_el_formulario_abre_vacio_y_sin_cargar() {
        val form = crear().form.value

        assertFalse(form.editando)
        assertFalse(form.cargando)
        assertFalse(form.hayCambios)
    }

    @Test
    fun al_editar_se_cargan_los_valores_y_se_toman_como_originales() = runTest(principal.dispatcher) {
        val form = editar().form.value

        assertTrue(form.editando)
        assertFalse(form.cargando)
        assertEquals("Título", form.titulo)
        assertEquals("Texto", form.texto)
        assertEquals("7", form.numero)
        assertEquals("Título", form.tituloOriginal)
        assertFalse(form.hayCambios)
    }

    @Test
    fun editar_un_valor_marca_que_hay_cambios_y_volver_al_original_los_quita() = runTest(principal.dispatcher) {
        val vm = editar()

        vm.updateTitulo("Otro")
        assertTrue(vm.form.value.hayCambios)

        vm.updateTitulo("Título")
        assertFalse(vm.form.value.hayCambios)
    }

    @Test
    fun mientras_carga_el_formulario_esta_marcado_como_cargando() = runTest(principal.dispatcher) {
        val llega = CompletableDeferred<ResultadoObtencionPrueba>()
        repositorio.alObtener = { llega.await() }

        val vm = PruebaFormViewModel(repositorio, SavedStateHandle(mapOf("entityId" to "p1")))
        assertTrue(vm.form.value.cargando)

        // Guardar antes de que llegue el registro no hace nada
        vm.guardar()
        assertTrue(repositorio.guardados.isEmpty())

        llega.complete(ResultadoObtencionPrueba.Encontrada(existente))
        assertFalse(vm.form.value.cargando)
    }

    @Test
    fun si_el_registro_ya_no_existe_se_cierra_con_un_mensaje() = runTest(principal.dispatcher) {
        repositorio.alObtener = { ResultadoObtencionPrueba.NoEncontrada }

        val vm = PruebaFormViewModel(repositorio, SavedStateHandle(mapOf("entityId" to "p1")))

        assertEquals("Este registro ya no está en el dispositivo", vm.form.value.mensajeSalida)
    }

    @Test
    fun si_no_se_pudo_leer_el_registro_se_cierra_con_un_mensaje() = runTest(principal.dispatcher) {
        repositorio.alObtener = { ResultadoObtencionPrueba.ErrorAlmacenamiento }

        val vm = PruebaFormViewModel(repositorio, SavedStateHandle(mapOf("entityId" to "p1")))

        assertEquals("No se pudo abrir el registro. Intenta de nuevo.", vm.form.value.mensajeSalida)
    }

    // ---- validación de campos ----

    @Test
    fun un_numero_vacio_o_no_entero_no_llega_al_repositorio() = runTest(principal.dispatcher) {
        val vm = crear()
        vm.updateTitulo("Algo")

        vm.guardar()
        assertEquals("Escribe un número", vm.form.value.errorNumero)

        for (invalido in listOf("abc", "1.5", "99999999999")) {
            vm.updateNumero(invalido)
            vm.guardar()
            assertEquals(invalido, "Escribe un número entero", vm.form.value.errorNumero)
        }

        assertEquals("Revisa el número", vm.form.value.mensaje)
        assertTrue(repositorio.guardados.isEmpty())
    }

    @Test
    fun el_error_del_numero_se_quita_al_escribir_un_entero() = runTest(principal.dispatcher) {
        val vm = crear()
        vm.guardar()
        assertEquals("Escribe un número", vm.form.value.errorNumero)

        vm.updateNumero("abc")
        assertEquals("Escribe un número", vm.form.value.errorNumero)

        vm.updateNumero(" 12 ")
        assertNull(vm.form.value.errorNumero)
    }

    @Test
    fun el_error_del_titulo_se_quita_al_escribir_algo() = runTest(principal.dispatcher) {
        val vm = crear()
        vm.updateNumero("1")
        repositorio.alGuardar = { ResultadoGuardadoPrueba.FaltaTitulo }
        vm.guardar()
        assertEquals("Escribe el título", vm.form.value.errorTitulo)

        vm.updateTitulo("  ")
        assertEquals("Escribe el título", vm.form.value.errorTitulo)

        vm.updateTitulo("Hola")
        assertNull(vm.form.value.errorTitulo)
    }

    // ---- guardado ----

    @Test
    fun guardar_nueva_envia_los_valores_y_cierra_con_mensaje() = runTest(principal.dispatcher) {
        val vm = crear()
        vm.updateTitulo("Mi prueba")
        vm.updateTexto("Contenido")
        vm.updateNumero(" 42 ")

        vm.guardar()

        val guardado = repositorio.guardados.single()
        assertNull(guardado.entityId)
        assertEquals("Mi prueba", guardado.titulo)
        assertEquals("Contenido", guardado.texto)
        assertEquals(42, guardado.numero)
        assertEquals("Registro guardado en el dispositivo", vm.form.value.mensajeSalida)
        assertFalse(vm.form.value.guardando)
    }

    @Test
    fun guardar_una_edicion_actualiza_el_mismo_registro() = runTest(principal.dispatcher) {
        val vm = editar()
        vm.updateTitulo("Nuevo título")

        vm.guardar()

        val guardado = repositorio.guardados.single()
        assertEquals("p1", guardado.entityId)
        assertEquals("Nuevo título", guardado.titulo)
        assertEquals(7, guardado.numero)
        assertEquals("Cambios guardados en el dispositivo", vm.form.value.mensajeSalida)
    }

    @Test
    fun editar_un_registro_que_se_elimino_no_guarda_y_lo_dice() = runTest(principal.dispatcher) {
        val vm = editar()
        repositorio.alActualizar = { ResultadoGuardadoPrueba.NoEncontrada }

        vm.guardar()

        assertEquals(
            "Este registro se eliminó del dispositivo; los cambios no se guardaron",
            vm.form.value.mensajeSalida
        )
        assertFalse(vm.form.value.guardando)
    }

    @Test
    fun sin_titulo_el_formulario_sigue_abierto_con_el_error_en_el_campo() = runTest(principal.dispatcher) {
        val vm = crear()
        vm.updateNumero("1")
        repositorio.alGuardar = { ResultadoGuardadoPrueba.FaltaTitulo }

        vm.guardar()

        assertEquals("Escribe el título", vm.form.value.errorTitulo)
        assertEquals("Falta el título", vm.form.value.mensaje)
        assertNull(vm.form.value.mensajeSalida)
        assertFalse(vm.form.value.guardando)
    }

    @Test
    fun los_fallos_de_almacenamiento_se_explican_y_dejan_reintentar() = runTest(principal.dispatcher) {
        val vm = crear()
        vm.updateTitulo("Algo")
        vm.updateNumero("1")

        repositorio.alGuardar = { ResultadoGuardadoPrueba.SinEspacio }
        vm.guardar()
        assertEquals("No hay espacio en el dispositivo. Libera espacio e intenta de nuevo.", vm.form.value.mensaje)
        assertFalse(vm.form.value.guardando)

        repositorio.alGuardar = { ResultadoGuardadoPrueba.ErrorAlmacenamiento }
        vm.guardar()
        assertEquals(
            "No se pudo guardar el registro en el dispositivo. Intenta de nuevo.",
            vm.form.value.mensaje
        )
        assertFalse(vm.form.value.guardando)
        assertNull(vm.form.value.mensajeSalida)
        assertEquals(2, repositorio.guardados.size)
    }

    @Test
    fun un_segundo_toque_mientras_se_guarda_no_duplica_el_registro() = runTest(principal.dispatcher) {
        val termina = CompletableDeferred<ResultadoGuardadoPrueba>()
        repositorio.alGuardar = { termina.await() }
        val vm = crear()
        vm.updateTitulo("Algo")
        vm.updateNumero("1")

        vm.guardar()
        assertTrue(vm.form.value.guardando)
        vm.guardar()

        assertEquals(1, repositorio.guardados.size)

        termina.complete(ResultadoGuardadoPrueba.Guardada("nueva"))
        assertFalse(vm.form.value.guardando)
    }

    @Test
    fun el_mensaje_se_muestra_una_sola_vez() = runTest(principal.dispatcher) {
        val vm = crear()
        vm.guardar()
        assertEquals("Revisa el número", vm.form.value.mensaje)

        vm.mensajeMostrado()

        assertNull(vm.form.value.mensaje)
    }
}
