package com.spiralsoft.servare.data.repository

import com.spiralsoft.servare.data.sync.SyncEngine
import com.spiralsoft.servare.data.sync.SyncScheduler
import com.spiralsoft.servare.domain.model.Prueba
import com.spiralsoft.servare.domain.model.TipoOperacion
import com.spiralsoft.servare.domain.repository.ResultadoEliminacionPrueba
import com.spiralsoft.servare.domain.repository.ResultadoGuardadoPrueba
import com.spiralsoft.servare.domain.repository.ResultadoObtencionPrueba
import com.spiralsoft.servare.testutil.BaseDeDatosRegla
import com.spiralsoft.servare.testutil.RelojFalso
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// El molde de las entidades sincronizables: lo que hace este repositorio lo repiten los demás
@RunWith(RobolectricTestRunner::class)
class DefaultPruebaRepositoryUnitTest {
    @get:Rule val bd = BaseDeDatosRegla()

    private val reloj = RelojFalso()
    private val scheduler = mockk<SyncScheduler>(relaxed = true)
    private val repo by lazy {
        DefaultPruebaRepository(
            bd.pruebas, EscrituraOutbox(bd.db, EncoladorOperaciones(bd.operaciones, reloj), bd.eliminaciones, scheduler, reloj),
            bd.syncInicial, mockk<SyncEngine>(), mockk<ResolutorOperaciones>()
        )
    }

    private suspend fun unica() = repo.observarTodas().first().single()

    @Test
    fun guardar_recorta_el_titulo_conserva_el_texto_y_encola_la_creacion() = runBlocking {
        val resultado = repo.guardar("  Hola  ", "  texto  ", 7)

        assertTrue(resultado is ResultadoGuardadoPrueba.Guardada)
        assertEquals(Prueba((resultado as ResultadoGuardadoPrueba.Guardada).entityId, "Hola", "  texto  ", 7, reloj.ahora), unica())
        assertEquals(TipoOperacion.CREAR, bd.operaciones.siguienteElegible()!!.type)
        verify(exactly = 1) { scheduler.requestSync() }
    }

    @Test
    fun sin_titulo_no_se_guarda_ni_se_actualiza() = runBlocking {
        for (titulo in listOf("", "  ", "\n")) {
            assertEquals(ResultadoGuardadoPrueba.FaltaTitulo, repo.guardar(titulo, "t", 1))
            assertEquals(ResultadoGuardadoPrueba.FaltaTitulo, repo.actualizar("x", titulo, "t", 1))
        }
        assertEquals(0, bd.operaciones.contarSinTerminar())
        verify(exactly = 0) { scheduler.requestSync() }
    }

    @Test
    fun actualizar_cambia_el_registro_y_uno_que_no_existe_se_informa() = runBlocking {
        repo.guardar("Antes", "a", 1)
        val id = unica().entityId

        assertTrue(repo.actualizar(id, " Después ", "b", 2) is ResultadoGuardadoPrueba.Guardada)
        assertEquals(Prueba(id, "Después", "b", 2, reloj.ahora), unica())
        assertEquals(ResultadoGuardadoPrueba.NoEncontrada, repo.actualizar("nada", "T", "t", 1))
    }

    @Test
    fun obtener_distingue_encontrada_y_no_encontrada_y_oculta_las_eliminadas() = runBlocking {
        repo.guardar("Uno", "t", 1)
        val id = unica().entityId

        assertEquals(ResultadoObtencionPrueba.Encontrada(unica()), repo.obtener(id))
        assertEquals(ResultadoObtencionPrueba.NoEncontrada, repo.obtener("nada"))

        repo.eliminar(id)
        assertEquals(ResultadoObtencionPrueba.NoEncontrada, repo.obtener(id))
    }

    @Test
    fun eliminar_nombra_el_registro_y_lo_marca_sin_borrarlo() = runBlocking {
        repo.guardar("Para borrar", "t", 1)
        val id = unica().entityId

        assertEquals(ResultadoEliminacionPrueba.Eliminada("Para borrar"), repo.eliminar(id))

        assertTrue(repo.observarTodas().first().isEmpty())
        assertNotNull(bd.pruebas.findById(id))
        assertEquals(ResultadoEliminacionPrueba.NoEncontrada, repo.eliminar(id))
    }

    @Test
    fun la_lista_va_de_la_mas_nueva_a_la_mas_vieja() = runBlocking {
        repo.guardar("Primera", "t", 1)
        reloj.avanzar(1000)
        repo.guardar("Segunda", "t", 2)

        assertEquals(listOf("Segunda", "Primera"), repo.observarTodas().first().map { it.titulo })
    }
}
