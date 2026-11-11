package com.spiralsoft.servare.ui.viewModel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.spiralsoft.servare.domain.model.AccionOperacion
import com.spiralsoft.servare.domain.repository.ResultadoEliminacionFicha
import com.spiralsoft.servare.domain.repository.ResultadoEliminacionPrueba
import com.spiralsoft.servare.domain.repository.ResultadoResolucion

class MensajesResultadoUnitTest {

    @Test
    fun cada_accion_resuelta_tiene_su_propio_mensaje() {
        val mensajes = AccionOperacion.entries.map { ResultadoResolucion.Resuelta(it).mensaje() }

        assertEquals(AccionOperacion.entries.size, mensajes.toSet().size)
        assertTrue(mensajes.none { it.isBlank() })
    }

    @Test
    fun la_resolucion_que_no_prospera_explica_por_que() {
        assertEquals("Este cambio ya se resolvió o cambió de estado", ResultadoResolucion.NoDisponible.mensaje())
        assertEquals(
            "No se pudo resolver el cambio. Intenta de nuevo.",
            ResultadoResolucion.ErrorAlmacenamiento.mensaje(),
        )
    }

    @Test
    fun eliminar_una_prueba_nombra_el_registro_y_avisa_que_falta_el_servidor() {
        assertEquals(
            "«Mi prueba» se eliminó del dispositivo. Se eliminará del servidor al sincronizar",
            ResultadoEliminacionPrueba.Eliminada("Mi prueba").mensaje()
        )
        assertEquals("Este registro ya no estaba en el dispositivo", ResultadoEliminacionPrueba.NoEncontrada.mensaje())
        assertEquals(
            "No se pudo eliminar el registro. Intenta de nuevo.",
            ResultadoEliminacionPrueba.ErrorAlmacenamiento.mensaje()
        )
    }

    @Test
    fun eliminar_una_ficha_sin_nombre_usa_un_nombre_de_reemplazo() {
        assertEquals(
            "«Cerro» se eliminó del dispositivo. Se eliminará del servidor al sincronizar",
            ResultadoEliminacionFicha.Eliminada("Cerro").mensaje()
        )
        assertTrue(ResultadoEliminacionFicha.Eliminada("  ").mensaje().startsWith("«Sin nombre»"))
        assertEquals("Esta ficha ya no estaba en el dispositivo", ResultadoEliminacionFicha.NoEncontrada.mensaje())
        assertEquals(
            "No se pudo eliminar la ficha. Intenta de nuevo.",
            ResultadoEliminacionFicha.ErrorAlmacenamiento.mensaje()
        )
    }
}
