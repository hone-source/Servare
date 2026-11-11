package com.spiralsoft.servare.data.repository

import com.spiralsoft.servare.data.local.entity.SyncInicialEntity
import com.spiralsoft.servare.data.sync.SyncEngine
import com.spiralsoft.servare.data.sync.SyncScheduler
import com.spiralsoft.servare.domain.model.AccionOperacion
import com.spiralsoft.servare.domain.model.Punto
import com.spiralsoft.servare.domain.model.TipoEntidad
import com.spiralsoft.servare.domain.model.TipoOperacion
import com.spiralsoft.servare.domain.repository.ResultadoEliminacionPunto
import com.spiralsoft.servare.domain.repository.ResultadoGuardadoPunto
import com.spiralsoft.servare.domain.repository.ResultadoRefresco
import com.spiralsoft.servare.domain.repository.ResultadoResolucion
import com.spiralsoft.servare.testutil.BaseDeDatosRegla
import com.spiralsoft.servare.testutil.RelojFalso
import io.mockk.coEvery
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

// Validación y traducción de resultados del repositorio de puntos, con Room real
@RunWith(RobolectricTestRunner::class)
class DefaultPuntoRepositoryUnitTest {
    @get:Rule val bd = BaseDeDatosRegla()

    private val reloj = RelojFalso()
    private val scheduler = mockk<SyncScheduler>(relaxed = true)
    private val motor = mockk<SyncEngine>()
    private val resolutor = mockk<ResolutorOperaciones>()
    private val repo by lazy {
        DefaultPuntoRepository(
            bd.db.puntoDao(), EscrituraOutbox(bd.db, EncoladorOperaciones(bd.operaciones, reloj), bd.eliminaciones, scheduler, reloj),
            bd.syncInicial, motor, resolutor,
        )
    }

    private suspend fun guardado(nombre: String = "Cerro", lat: Double = 10.0, lon: Double = -20.0) =
        repo.guardar(nombre, lat, lon).let { repo.observarTodos().first().single() }

    @Test
    fun guardar_recorta_el_nombre_y_encola_la_creacion() = runBlocking {
        assertEquals(ResultadoGuardadoPunto.Guardado("Cerro"), repo.guardar("  Cerro  ", 10.0, -20.0))

        val punto = repo.observarTodos().first().single()
        assertEquals(Punto(punto.entityId, "Cerro", 10.0, -20.0, reloj.ahora), punto)
        val op = bd.operaciones.siguienteElegible()!!
        assertEquals(TipoOperacion.CREAR, op.type)
        assertEquals(TipoEntidad.PUNTO, op.entityType)
        verify(exactly = 1) { scheduler.requestSync() }
    }

    @Test
    fun sin_nombre_no_se_escribe_nada() = runBlocking {
        for (nombre in listOf("", "   ", "\n\t")) {
            assertEquals(ResultadoGuardadoPunto.FaltaNombre, repo.guardar(nombre, 0.0, 0.0))
        }
        assertTrue(repo.observarTodos().first().isEmpty())
        assertEquals(0, bd.operaciones.contarSinTerminar())
        verify(exactly = 0) { scheduler.requestSync() }
    }

    @Test
    fun las_coordenadas_fuera_de_rango_o_no_finitas_se_rechazan() = runBlocking {
        val invalidas = listOf(
            90.0001 to 0.0, -90.0001 to 0.0, 0.0 to 180.0001, 0.0 to -180.0001,
            Double.NaN to 0.0, 0.0 to Double.NaN,
            Double.POSITIVE_INFINITY to 0.0, 0.0 to Double.NEGATIVE_INFINITY
        )
        for ((lat, lon) in invalidas) {
            assertEquals("$lat,$lon", ResultadoGuardadoPunto.CoordenadasInvalidas, repo.guardar("P", lat, lon))
            assertEquals("$lat,$lon", ResultadoGuardadoPunto.CoordenadasInvalidas, repo.actualizar("x", "P", lat, lon))
        }
        assertEquals(0, bd.operaciones.contarSinTerminar())
    }

    @Test
    fun los_limites_exactos_son_validos() = runBlocking {
        for ((lat, lon) in listOf(90.0 to 180.0, -90.0 to -180.0, 0.0 to 0.0)) {
            assertEquals(ResultadoGuardadoPunto.Guardado("P"), repo.guardar("P", lat, lon))
        }
    }

    @Test
    fun el_nombre_se_valida_antes_que_las_coordenadas() = runBlocking {
        assertEquals(ResultadoGuardadoPunto.FaltaNombre, repo.guardar("", 999.0, 999.0))
    }

    @Test
    fun actualizar_cambia_los_campos_con_el_nombre_recortado_y_la_version_conocida() = runBlocking {
        val punto = guardado()
        bd.db.puntoDao().update(bd.db.puntoDao().findById(punto.entityId)!!.copy(version = 3))
        bd.operaciones.marcarCompletada(bd.operaciones.siguienteElegible()!!.operationId, 1)

        assertEquals(ResultadoGuardadoPunto.Guardado("Nuevo"), repo.actualizar(punto.entityId, " Nuevo ", 1.0, 2.0))

        val actualizado = bd.db.puntoDao().findById(punto.entityId)!!
        assertEquals(Triple("Nuevo", 1.0, 2.0), Triple(actualizado.nombre, actualizado.latitud, actualizado.longitud))
        assertEquals(3, bd.operaciones.siguienteElegible()!!.baseVersion)
    }

    @Test
    fun actualizar_un_punto_que_no_existe_lo_dice() = runBlocking {
        assertEquals(ResultadoGuardadoPunto.NoEncontrado, repo.actualizar("nada", "P", 0.0, 0.0))
    }

    @Test
    fun eliminar_nombra_el_punto_y_lo_saca_de_la_lista() = runBlocking {
        val punto = guardado(nombre = "Mirador")

        assertEquals(ResultadoEliminacionPunto.Eliminado("Mirador"), repo.eliminar(punto.entityId))

        assertTrue(repo.observarTodos().first().isEmpty())
        assertNotNull("el registro espera la confirmación del servidor", bd.db.puntoDao().findById(punto.entityId))
    }

    @Test
    fun eliminar_un_punto_que_no_existe_o_ya_eliminado_lo_dice() = runBlocking {
        assertEquals(ResultadoEliminacionPunto.NoEncontrado, repo.eliminar("nada"))
        val punto = guardado()
        repo.eliminar(punto.entityId)
        assertEquals(ResultadoEliminacionPunto.NoEncontrado, repo.eliminar(punto.entityId))
    }

    @Test
    fun la_descarga_inicial_se_observa_por_tipo() = runBlocking {
        assertEquals(false, repo.observarDescargaInicial().first())
        bd.syncInicial.marcarCompletada(SyncInicialEntity(TipoEntidad.FICHA, 1))
        assertEquals("otro tipo no cuenta", false, repo.observarDescargaInicial().first())
        bd.syncInicial.marcarCompletada(SyncInicialEntity(TipoEntidad.PUNTO, 1))
        assertEquals(true, repo.observarDescargaInicial().first())
    }

    @Test
    fun refrescar_y_resolver_delegan_en_el_motor_y_el_resolutor() = runBlocking {
        coEvery { motor.refrescar() } returns ResultadoRefresco.SinServidor
        coEvery { resolutor.resolver("op", AccionOperacion.DESCARTAR) } returns ResultadoResolucion.NoDisponible

        assertEquals(ResultadoRefresco.SinServidor, repo.refrescar())
        assertEquals(ResultadoResolucion.NoDisponible, repo.resolver("op", AccionOperacion.DESCARTAR))
    }
}
