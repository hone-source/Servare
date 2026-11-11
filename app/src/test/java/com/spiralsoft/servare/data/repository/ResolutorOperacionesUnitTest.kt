package com.spiralsoft.servare.data.repository

import com.spiralsoft.servare.data.local.entity.EliminacionEntity
import com.spiralsoft.servare.data.local.entity.OperationEntity
import com.spiralsoft.servare.data.local.entity.PruebaEntity
import com.spiralsoft.servare.data.remote.PruebaRemoteDataSource
import com.spiralsoft.servare.data.sync.Adaptadores
import com.spiralsoft.servare.data.sync.AdaptadorPrueba
import com.spiralsoft.servare.data.sync.SyncScheduler
import com.spiralsoft.servare.domain.model.AccionOperacion
import com.spiralsoft.servare.domain.model.AccionOperacion.ACEPTAR_SERVIDOR
import com.spiralsoft.servare.domain.model.AccionOperacion.CONSERVAR_MIA
import com.spiralsoft.servare.domain.model.AccionOperacion.DESCARTAR
import com.spiralsoft.servare.domain.model.AccionOperacion.RECREAR
import com.spiralsoft.servare.domain.model.AccionOperacion.REINTENTAR
import com.spiralsoft.servare.domain.model.CausaConflicto
import com.spiralsoft.servare.domain.model.EstadoOperacion
import com.spiralsoft.servare.domain.model.EstadoOperacion.BLOQUEADA
import com.spiralsoft.servare.domain.model.EstadoOperacion.CONFLICTO
import com.spiralsoft.servare.domain.model.EstadoOperacion.DESCARTADA
import com.spiralsoft.servare.domain.model.EstadoOperacion.FALLIDA
import com.spiralsoft.servare.domain.model.EstadoOperacion.PENDIENTE
import com.spiralsoft.servare.domain.model.TipoEntidad
import com.spiralsoft.servare.domain.model.TipoOperacion
import com.spiralsoft.servare.domain.repository.ResultadoResolucion
import com.spiralsoft.servare.testutil.AdaptadorFalso
import com.spiralsoft.servare.testutil.BaseDeDatosRegla
import com.spiralsoft.servare.testutil.RelojFalso
import com.spiralsoft.servare.testutil.operacion
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Lo que el usuario decide sobre una operación FALLIDA o en CONFLICTO.
// Cada acción exige el estado esperado: si ya no lo tiene, NoDisponible y nada cambia
@RunWith(RobolectricTestRunner::class)
class ResolutorOperacionesUnitTest {
    @get:Rule val bd = BaseDeDatosRegla()

    private val reloj = RelojFalso()
    private val scheduler = mockk<SyncScheduler>(relaxed = true)
    private val resolutor by lazy {
        val adaptadorPrueba = AdaptadorPrueba(
            bd.pruebas, mockk<PruebaRemoteDataSource>(), Json, bd.db, bd.operaciones, bd.eliminaciones, bd.syncInicial, reloj
        )
        ResolutorOperaciones(
            bd.db, bd.operaciones, EncoladorOperaciones(bd.operaciones, reloj), bd.eliminaciones,
            Adaptadores(
                mapOf(
                    TipoEntidad.PRUEBA to adaptadorPrueba,
                    TipoEntidad.FICHA to AdaptadorFalso(TipoEntidad.FICHA),
                    TipoEntidad.PUNTO to AdaptadorFalso(TipoEntidad.PUNTO)
                )
            ),
            scheduler, reloj
        )
    }

    private val servidorV6 = """{"entityId":"e1","titulo":"Del servidor","texto":"t","numero":9,"createdAt":5,"version":6}"""

    private suspend fun registro(id: String = "e1", version: Int? = 2) =
        bd.pruebas.insert(PruebaEntity(id, "Mío", "texto", 1, createdAt = 1, version = version))

    private fun conflicto(
        id: String = "c",
        causa: CausaConflicto = CausaConflicto.VERSION_DISTINTA,
        snapshot: String? = servidorV6,
        tipo: TipoOperacion = TipoOperacion.ACTUALIZAR,
        createdAt: Long = 10
    ): OperationEntity = operacion(id, createdAt, estado = CONFLICTO, tipo = tipo, baseVersion = 2)
        .copy(conflictCause = causa, serverSnapshot = snapshot)

    private suspend fun estado(id: String) = bd.operaciones.buscar(id)!!.status

    private suspend fun resolver(id: String, accion: AccionOperacion) = resolutor.resolver(id, accion)

    // --- Resultado y ciclo ---

    @Test
    fun resolver_con_exito_pide_un_ciclo_y_sin_exito_no() = runBlocking {
        bd.operaciones.insert(operacion("f", 10, estado = FALLIDA))

        assertEquals(ResultadoResolucion.Resuelta(REINTENTAR), resolver("f", REINTENTAR))
        verify(exactly = 1) { scheduler.requestSync() }

        assertEquals(ResultadoResolucion.NoDisponible, resolver("f", REINTENTAR))
        verify(exactly = 1) { scheduler.requestSync() }
    }

    @Test
    fun una_operacion_que_no_existe_no_esta_disponible() = runBlocking {
        for (accion in AccionOperacion.entries) {
            assertEquals(accion.name, ResultadoResolucion.NoDisponible, resolver("fantasma", accion))
        }
        verify(exactly = 0) { scheduler.requestSync() }
    }

    // --- REINTENTAR ---

    @Test
    fun reintentar_devuelve_la_fallida_a_la_cola_con_el_mismo_id_y_libera_las_bloqueadas() = runBlocking {
        bd.operaciones.insert(operacion("f", 10, estado = FALLIDA).copy(attemptCount = 13, lastError = "x"))
        bd.operaciones.insert(operacion("b", 20, estado = BLOQUEADA))
        bd.operaciones.insert(operacion("otra", 20, estado = BLOQUEADA, entidad = "e2"))

        resolver("f", REINTENTAR)

        val f = bd.operaciones.buscar("f")!!
        assertEquals(PENDIENTE, f.status)
        assertEquals(0, f.attemptCount)
        assertNull(f.lastError)
        assertEquals(PENDIENTE, estado("b"))
        assertEquals(BLOQUEADA, estado("otra"))
    }

    @Test
    fun reintentar_no_aplica_a_un_conflicto() = runBlocking {
        bd.operaciones.insert(conflicto())

        assertEquals(ResultadoResolucion.NoDisponible, resolver("c", REINTENTAR))
        assertEquals(CONFLICTO, estado("c"))
    }

    // --- RECREAR ---

    @Test
    fun recrear_descarta_la_vieja_encola_una_creacion_nueva_y_olvida_la_version() = runBlocking {
        registro(version = 2)
        bd.operaciones.insert(conflicto(causa = CausaConflicto.INEXISTENTE_EN_SERVIDOR, snapshot = null, createdAt = 10))
        bd.operaciones.insert(operacion("b", 20, estado = BLOQUEADA))

        assertEquals(ResultadoResolucion.Resuelta(RECREAR), resolver("c", RECREAR))

        assertEquals(DESCARTADA, estado("c"))
        assertNull("la versión que conocía ya no existe", bd.pruebas.findById("e1")!!.version)
        val nueva = bd.operaciones.siguienteElegible()!!
        assertEquals(TipoOperacion.CREAR, nueva.type)
        assertNotEquals("c", nueva.operationId)
        assertEquals("conserva el orden", 10L, nueva.createdAt)
        assertEquals("e1", nueva.entityId)
        assertEquals("las posteriores vuelven a la cola", PENDIENTE, estado("b"))
    }

    @Test
    fun recrear_solo_aplica_a_una_actualizacion_inexistente_cuyo_registro_sigue_en_el_dispositivo() = runBlocking {
        registro()
        val casos = listOf(
            "por versión" to conflicto(causa = CausaConflicto.VERSION_DISTINTA),
            "era una creación" to conflicto(causa = CausaConflicto.INEXISTENTE_EN_SERVIDOR, tipo = TipoOperacion.CREAR),
            "era un borrado" to conflicto(causa = CausaConflicto.INEXISTENTE_EN_SERVIDOR, tipo = TipoOperacion.ELIMINAR),
            "fallida" to operacion("c", 10, estado = FALLIDA)
        )
        for ((nombre, op) in casos) {
            bd.operaciones.insert(op)
            assertEquals(nombre, ResultadoResolucion.NoDisponible, resolver("c", RECREAR))
            assertEquals(nombre, op.status, estado("c"))
            bd.db.clearAllTables()
            registro()
        }
    }

    @Test
    fun recrear_sin_el_registro_en_el_dispositivo_no_esta_disponible() = runBlocking {
        bd.operaciones.insert(conflicto(causa = CausaConflicto.INEXISTENTE_EN_SERVIDOR, snapshot = null))

        assertEquals(ResultadoResolucion.NoDisponible, resolver("c", RECREAR))
        assertEquals(CONFLICTO, estado("c"))
    }

    // --- CONSERVAR_MIA ---

    @Test
    fun conservar_la_mia_sigue_sobre_la_version_del_servidor_y_encadena_a_las_posteriores() = runBlocking {
        registro(version = 2)
        bd.operaciones.insert(conflicto(createdAt = 10))
        bd.operaciones.insert(operacion("b", 20, estado = BLOQUEADA, baseVersion = 2))

        assertEquals(ResultadoResolucion.Resuelta(CONSERVAR_MIA), resolver("c", CONSERVAR_MIA))

        val c = bd.operaciones.buscar("c")!!
        assertEquals(PENDIENTE, c.status)
        assertEquals(6, c.baseVersion)
        assertNull(c.conflictCause)
        assertNull(c.serverSnapshot)
        assertEquals("sigue siendo la misma operación", TipoOperacion.ACTUALIZAR, c.type)
        assertEquals(6, bd.pruebas.findById("e1")!!.version)
        val b = bd.operaciones.buscar("b")!!
        assertEquals(PENDIENTE, b.status)
        assertEquals(6, b.baseVersion)
    }

    @Test
    fun conservar_la_mia_no_aplica_sin_registro_del_servidor_ni_por_registro_inexistente() = runBlocking {
        registro()
        val casos = listOf(
            "sin snapshot" to conflicto(snapshot = null),
            "snapshot ilegible" to conflicto(snapshot = "{roto"),
            "snapshot sin versión" to conflicto(snapshot = servidorV6.replace(""","version":6""", "")),
            "registro inexistente" to conflicto(causa = CausaConflicto.INEXISTENTE_EN_SERVIDOR, snapshot = servidorV6)
        )
        for ((nombre, op) in casos) {
            bd.operaciones.insert(op)
            assertEquals(nombre, ResultadoResolucion.NoDisponible, resolver("c", CONSERVAR_MIA))
            assertEquals(nombre, CONFLICTO, estado("c"))
            assertEquals(nombre, 2, bd.pruebas.findById("e1")!!.version)
            bd.db.clearAllTables()
            registro()
        }
    }

    // --- ACEPTAR_SERVIDOR ---

    @Test
    fun aceptar_el_servidor_reemplaza_el_registro_y_abandona_lo_posterior() = runBlocking {
        registro()
        bd.operaciones.insert(conflicto(createdAt = 10))
        bd.operaciones.insert(operacion("bloqueada", 20, estado = BLOQUEADA))
        bd.operaciones.insert(operacion("pendiente", 30))
        bd.operaciones.insert(operacion("ajena", 20, entidad = "e2"))

        assertEquals(ResultadoResolucion.Resuelta(ACEPTAR_SERVIDOR), resolver("c", ACEPTAR_SERVIDOR))

        val guardado = bd.pruebas.findById("e1")!!
        assertEquals("Del servidor", guardado.titulo)
        assertEquals(6, guardado.version)
        assertEquals(DESCARTADA, estado("c"))
        assertEquals(DESCARTADA, estado("bloqueada"))
        assertEquals("lo creado después también se abandona", DESCARTADA, estado("pendiente"))
        assertEquals(PENDIENTE, estado("ajena"))
    }

    @Test
    fun aceptar_el_servidor_repone_un_registro_que_el_usuario_habia_borrado() = runBlocking {
        registro()
        bd.eliminaciones.insert(EliminacionEntity(TipoEntidad.PRUEBA, "e1", eliminadaEn = 3))
        bd.operaciones.insert(conflicto(tipo = TipoOperacion.ELIMINAR))

        resolver("c", ACEPTAR_SERVIDOR)

        assertTrue(bd.eliminaciones.entidadesEliminadas(TipoEntidad.PRUEBA).isEmpty())
        assertNotNull(bd.pruebas.findActivaById("e1"))
    }

    @Test
    fun aceptar_el_servidor_no_aplica_sin_su_registro_ni_por_registro_inexistente() = runBlocking {
        registro()
        val casos = listOf(
            "sin snapshot" to conflicto(snapshot = null),
            "snapshot ilegible" to conflicto(snapshot = "[]"),
            "registro inexistente" to conflicto(causa = CausaConflicto.INEXISTENTE_EN_SERVIDOR)
        )
        for ((nombre, op) in casos) {
            bd.operaciones.insert(op)
            assertEquals(nombre, ResultadoResolucion.NoDisponible, resolver("c", ACEPTAR_SERVIDOR))
            assertEquals(nombre, CONFLICTO, estado("c"))
            assertEquals(nombre, "Mío", bd.pruebas.findById("e1")!!.titulo)
            bd.db.clearAllTables()
            registro()
        }
    }

    // --- DESCARTAR ---

    @Test
    fun descartar_una_actualizacion_conserva_el_registro_y_abandona_lo_posterior() = runBlocking {
        registro()
        bd.operaciones.insert(operacion("f", 10, estado = FALLIDA))
        bd.operaciones.insert(operacion("b", 20, estado = BLOQUEADA))

        assertEquals(ResultadoResolucion.Resuelta(DESCARTAR), resolver("f", DESCARTAR))

        assertEquals(DESCARTADA, estado("f"))
        assertEquals(DESCARTADA, estado("b"))
        assertNotNull(bd.pruebas.findById("e1"))
    }

    @Test
    fun descartar_una_creacion_quita_el_registro_que_nunca_llego_al_servidor() = runBlocking {
        registro(version = null)
        bd.operaciones.insert(operacion("f", 10, estado = FALLIDA, tipo = TipoOperacion.CREAR))

        resolver("f", DESCARTAR)

        assertEquals(DESCARTADA, estado("f"))
        assertNull(bd.pruebas.findById("e1"))
    }

    @Test
    fun descartar_un_cambio_sobre_un_registro_que_ya_no_existe_en_el_servidor_quita_el_local() = runBlocking {
        registro()
        bd.operaciones.insert(conflicto(causa = CausaConflicto.INEXISTENTE_EN_SERVIDOR, snapshot = null))

        resolver("c", DESCARTAR)

        assertNull(bd.pruebas.findById("e1"))
    }

    @Test
    fun descartar_un_borrado_lo_abandona_y_el_registro_vuelve_a_la_lista() = runBlocking {
        registro()
        bd.eliminaciones.insert(EliminacionEntity(TipoEntidad.PRUEBA, "e1", eliminadaEn = 3))
        bd.operaciones.insert(operacion("f", 10, estado = FALLIDA, tipo = TipoOperacion.ELIMINAR))

        resolver("f", DESCARTAR)

        assertTrue(bd.eliminaciones.entidadesEliminadas(TipoEntidad.PRUEBA).isEmpty())
        assertNotNull(bd.pruebas.findActivaById("e1"))
    }

    @Test
    fun descartar_solo_aplica_a_fallidas_y_conflictos() = runBlocking {
        registro()
        for (inicial in listOf(PENDIENTE, BLOQUEADA, EstadoOperacion.COMPLETADA, EstadoOperacion.REINTENTABLE)) {
            bd.operaciones.insert(operacion("x", 10, estado = inicial))
            assertEquals(inicial.name, ResultadoResolucion.NoDisponible, resolver("x", DESCARTAR))
            assertEquals(inicial.name, inicial, estado("x"))
            bd.db.clearAllTables()
            registro()
        }
        assertNotNull(bd.pruebas.findById("e1"))
    }

    @Test
    fun la_misma_decision_dos_veces_la_segunda_ya_no_esta_disponible() = runBlocking {
        registro()
        bd.operaciones.insert(conflicto())

        assertEquals(ResultadoResolucion.Resuelta(ACEPTAR_SERVIDOR), resolver("c", ACEPTAR_SERVIDOR))
        assertEquals(ResultadoResolucion.NoDisponible, resolver("c", ACEPTAR_SERVIDOR))
        assertEquals(ResultadoResolucion.NoDisponible, resolver("c", CONSERVAR_MIA))
    }
}
