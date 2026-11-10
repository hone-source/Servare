package com.spiralsoft.servare.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import com.spiralsoft.servare.domain.model.TipoEntidad
import com.spiralsoft.servare.testutil.AdaptadorFalso

class AdaptadoresTest {

    private fun adaptadoresDe(tipos: List<TipoEntidad>) = tipos.associateWith { AdaptadorFalso(it) }

    @Test
    fun cada_tipo_se_resuelve_a_su_adaptador() {
        val porTipo = adaptadoresDe(TipoEntidad.entries)
        val adaptadores = Adaptadores(porTipo)

        for (tipo in TipoEntidad.entries) {
            assertSame(porTipo.getValue(tipo), adaptadores[tipo])
        }
    }

    @Test
    fun se_descargan_en_el_orden_de_TipoEntidad_sin_importar_el_del_mapa() {
        val alReves = adaptadoresDe(TipoEntidad.entries.reversed())

        val adaptadores = Adaptadores(alReves)

        assertEquals(TipoEntidad.entries, adaptadores.todos.map { it.tipo })
    }

    @Test
    fun un_tipo_sin_adaptador_falla_al_construirse_y_dice_cual() {
        val incompleto = adaptadoresDe(TipoEntidad.entries - TipoEntidad.FICHA)

        val error = assertThrows(IllegalStateException::class.java) { Adaptadores(incompleto) }

        assertTrue(error.message!!.contains("FICHA"))
    }
}
