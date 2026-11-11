package com.spiralsoft.servare.data.repository

import com.spiralsoft.servare.data.local.dao.aFicha
import com.spiralsoft.servare.data.sync.SyncEngine
import com.spiralsoft.servare.data.sync.SyncScheduler
import com.spiralsoft.servare.domain.model.Ficha
import com.spiralsoft.servare.domain.model.TipoOperacion
import com.spiralsoft.servare.domain.repository.ResultadoEliminacionFicha
import com.spiralsoft.servare.domain.repository.ResultadoGuardadoFicha
import com.spiralsoft.servare.domain.repository.ResultadoObtencionFicha
import com.spiralsoft.servare.testutil.BaseDeDatosRegla
import com.spiralsoft.servare.testutil.RelojFalso
import com.spiralsoft.servare.testutil.fichaLlena
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

@RunWith(RobolectricTestRunner::class)
class DefaultFichaRepositoryUnitTest {
    @get:Rule val bd = BaseDeDatosRegla()

    private val reloj = RelojFalso()
    private val scheduler = mockk<SyncScheduler>(relaxed = true)
    private val repo by lazy {
        DefaultFichaRepository(
            bd.db.fichaDao(), EscrituraOutbox(bd.db, EncoladorOperaciones(bd.operaciones, reloj), bd.eliminaciones, scheduler, reloj),
            bd.syncInicial, mockk<SyncEngine>(), mockk<ResolutorOperaciones>(),
        )
    }

    private suspend fun guardada(ficha: Ficha = fichaLlena()): String {
        val resultado = repo.guardar(ficha) as ResultadoGuardadoFicha.Guardada
        return resultado.entityId
    }

    @Test
    fun guardar_recorta_el_nombre_guarda_las_tablas_y_encola_la_creacion() = runBlocking {
        val id = guardada(fichaLlena("  Cerro  "))

        val ficha = bd.db.fichaDao().findCompletaById(id)!!
        assertEquals(fichaLlena("Cerro"), ficha.aFicha())
        assertEquals(null, ficha.ficha.version)
        assertEquals(reloj.ahora, ficha.ficha.createdAt)
        assertEquals(TipoOperacion.CREAR, bd.operaciones.siguienteElegible()!!.type)
        verify(exactly = 1) { scheduler.requestSync() }
    }

    @Test
    fun sin_nombre_no_se_guarda_ni_se_actualiza() = runBlocking {
        for (nombre in listOf("", "   ", "\n")) {
            assertEquals(ResultadoGuardadoFicha.FaltaNombre, repo.guardar(Ficha(nombre = nombre)))
            assertEquals(ResultadoGuardadoFicha.FaltaNombre, repo.actualizar("x", Ficha(nombre = nombre)))
        }
        assertEquals(0, bd.operaciones.contarSinTerminar())
        verify(exactly = 0) { scheduler.requestSync() }
    }

    @Test
    fun actualizar_conserva_la_marca_de_creacion_y_la_version_y_reemplaza_el_contenido() = runBlocking {
        val id = guardada()
        bd.db.fichaDao().actualizarVersion(id, 4)
        bd.operaciones.marcarCompletada(bd.operaciones.siguienteElegible()!!.operationId, 1)
        reloj.avanzar(10_000)
        val nueva = fichaLlena(" Renombrada ").copy(participantes = emptyList())

        assertEquals(ResultadoGuardadoFicha.Guardada(id), repo.actualizar(id, nueva))

        val guardada = bd.db.fichaDao().findCompletaById(id)!!
        assertEquals(nueva.copy(nombre = "Renombrada"), guardada.aFicha())
        assertEquals(4, guardada.ficha.version)
        assertEquals("no es otra ficha: conserva cuándo se creó", 1_700_000_000_000L, guardada.ficha.createdAt)
        assertEquals(4, bd.operaciones.siguienteElegible()!!.baseVersion)
    }

    @Test
    fun actualizar_una_ficha_que_no_existe_lo_dice() = runBlocking {
        assertEquals(ResultadoGuardadoFicha.NoEncontrada, repo.actualizar("nada", fichaLlena()))
    }

    @Test
    fun obtener_devuelve_la_ficha_completa_y_distingue_la_inexistente_y_la_eliminada() = runBlocking {
        val id = guardada()

        assertEquals(ResultadoObtencionFicha.Encontrada(fichaLlena()), repo.obtener(id))
        assertEquals(ResultadoObtencionFicha.NoEncontrada, repo.obtener("nada"))

        repo.eliminar(id)
        assertEquals(ResultadoObtencionFicha.NoEncontrada, repo.obtener(id))
    }

    @Test
    fun eliminar_nombra_la_ficha_la_oculta_y_no_la_borra_hasta_que_el_servidor_confirme() = runBlocking {
        val id = guardada()

        assertEquals(ResultadoEliminacionFicha.Eliminada("Cerro"), repo.eliminar(id))

        assertTrue(repo.observarResumenes().first().isEmpty())
        assertNotNull(bd.db.fichaDao().findById(id))
        assertEquals(ResultadoEliminacionFicha.NoEncontrada, repo.eliminar(id))
    }

    @Test
    fun la_lista_marca_pendiente_la_ficha_con_cambios_sin_confirmar() = runBlocking {
        val id = guardada()

        val resumen = repo.observarResumenes().first().single()

        assertEquals(id, resumen.entityId)
        assertEquals("Cerro", resumen.nombre)
        assertTrue(resumen.pendiente)
    }
}
