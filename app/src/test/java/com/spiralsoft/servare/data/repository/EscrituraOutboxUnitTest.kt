package com.spiralsoft.servare.data.repository

import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteFullException
import com.spiralsoft.servare.data.local.entity.PruebaEntity
import com.spiralsoft.servare.data.repository.EscrituraOutbox.Existente
import com.spiralsoft.servare.data.sync.SyncScheduler
import com.spiralsoft.servare.domain.model.EstadoOperacion
import com.spiralsoft.servare.domain.model.TipoEntidad
import com.spiralsoft.servare.domain.model.TipoOperacion
import com.spiralsoft.servare.testutil.BaseDeDatosRegla
import com.spiralsoft.servare.testutil.RelojFalso
import com.spiralsoft.servare.testutil.operacion
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Outbox transaccional: el cambio del registro y su operación van en UNA transacción,
// y el ciclo de sincronización se pide después, solo si se escribió
@RunWith(RobolectricTestRunner::class)
class EscrituraOutboxUnitTest {
    @get:Rule val bd = BaseDeDatosRegla()

    private val reloj = RelojFalso()
    private val scheduler = mockk<SyncScheduler>(relaxed = true)
    private val encolador by lazy { spyk(EncoladorOperaciones(bd.operaciones, reloj)) }
    private val outbox by lazy { EscrituraOutbox(bd.db, encolador, bd.eliminaciones, scheduler, reloj) }

    private val tipo = TipoEntidad.PRUEBA

    private fun entidad(id: String, version: Int? = 1) = PruebaEntity(id, "Título", "texto", 1, createdAt = 1, version = version)

    private fun operacionesDe(id: String) =
        bd.db.query("SELECT operationId FROM operaciones WHERE entityId = ?", arrayOf(id)).use { it.count }

    // --- crear ---

    @Test
    fun crear_guarda_el_registro_y_una_operacion_CREAR_PENDIENTE_con_la_misma_marca_de_tiempo() = runBlocking {
        val resultado = outbox.crear(tipo) { id, creada -> bd.pruebas.insert(entidad(id).copy(createdAt = creada, version = null)) }

        val id = (resultado as ResultadoEscritura.Ok).valor
        val registro = bd.pruebas.findById(id)!!
        val op = bd.operaciones.siguienteElegible()!!
        assertEquals(TipoOperacion.CREAR, op.type)
        assertEquals(EstadoOperacion.PENDIENTE, op.status)
        assertEquals(id, op.entityId)
        assertEquals(tipo, op.entityType)
        assertEquals(registro.createdAt, op.createdAt)
        assertEquals(reloj.ahora, op.createdAt)
        assertNull(op.baseVersion)
    }

    @Test
    fun crear_pide_el_ciclo_una_vez_y_despues_de_guardar() = runBlocking {
        var guardadoAlPedir: Boolean? = null
        every { scheduler.requestSync() } answers { guardadoAlPedir = hayRegistros() }

        outbox.crear(tipo) { id, _ -> bd.pruebas.insert(entidad(id)) }

        verify(exactly = 1) { scheduler.requestSync() }
        assertEquals(true, guardadoAlPedir)
    }

    @Test
    fun cada_creacion_recibe_un_id_distinto() = runBlocking {
        val a = outbox.crear(tipo) { id, _ -> bd.pruebas.insert(entidad(id)) }
        val b = outbox.crear(tipo) { id, _ -> bd.pruebas.insert(entidad(id)) }
        assertNotEquals((a as ResultadoEscritura.Ok).valor, (b as ResultadoEscritura.Ok).valor)
    }

    @Test
    fun si_falla_encolar_el_registro_no_queda_guardado_y_se_avisa_que_no_hay_espacio() = runBlocking {
        coEvery { encolador.encolar(any(), any(), any(), any(), any()) } throws SQLiteFullException("disco lleno")

        val resultado = outbox.crear(tipo) { id, _ -> bd.pruebas.insert(entidad(id)) }

        assertEquals(ResultadoEscritura.SinEspacio, resultado)
        assertTrue("la transacción se deshizo", !hayRegistros())
        assertEquals(0, bd.operaciones.contarSinTerminar())
        verify(exactly = 0) { scheduler.requestSync() }
    }

    @Test
    fun si_falla_guardar_no_queda_operacion_huerfana_y_se_avisa_del_error_de_almacenamiento() = runBlocking {
        val resultado = outbox.crear(tipo) { _, _ -> throw SQLiteException("corrupto") }

        assertEquals(ResultadoEscritura.ErrorAlmacenamiento, resultado)
        assertEquals(0, bd.operaciones.contarSinTerminar())
        verify(exactly = 0) { scheduler.requestSync() }
    }

    // --- actualizar ---

    @Test
    fun actualizar_guarda_el_cambio_y_encola_una_ACTUALIZAR_con_la_version_conocida() = runBlocking {
        bd.pruebas.insert(entidad("e1", version = 4))

        val resultado = outbox.actualizar(
            tipo, "e1",
            buscar = { bd.pruebas.findActivaById("e1")?.let { Existente(it, it.version) } },
            guardar = { bd.pruebas.update(it.copy(titulo = "Cambiado")) }
        )

        assertEquals(ResultadoEscritura.Ok("e1"), resultado)
        assertEquals("Cambiado", bd.pruebas.findById("e1")!!.titulo)
        val op = bd.operaciones.siguienteElegible()!!
        assertEquals(TipoOperacion.ACTUALIZAR, op.type)
        assertEquals(4, op.baseVersion)
        verify(exactly = 1) { scheduler.requestSync() }
    }

    @Test
    fun un_segundo_guardado_con_la_primera_operacion_sin_enviar_no_encola_otra() = runBlocking {
        bd.pruebas.insert(entidad("e1"))
        repeat(3) { n ->
            outbox.actualizar(
                tipo, "e1",
                buscar = { bd.pruebas.findActivaById("e1")?.let { Existente(it, it.version) } },
                guardar = { bd.pruebas.update(it.copy(titulo = "Cambio $n")) }
            )
        }

        assertEquals(1, operacionesDe("e1"))
        assertEquals("Cambio 2", bd.pruebas.findById("e1")!!.titulo)
    }

    @Test
    fun si_la_operacion_anterior_ya_esta_en_vuelo_el_nuevo_cambio_tiene_su_propia_operacion() = runBlocking {
        bd.pruebas.insert(entidad("e1"))
        bd.operaciones.insert(operacion("en_vuelo", 1, estado = EstadoOperacion.PROCESANDO, entidad = "e1"))

        outbox.actualizar(
            tipo, "e1",
            buscar = { bd.pruebas.findActivaById("e1")?.let { Existente(it, it.version) } },
            guardar = { bd.pruebas.update(it.copy(titulo = "Nuevo")) }
        )

        assertEquals(2, operacionesDe("e1"))
    }

    @Test
    fun actualizar_un_registro_que_ya_no_existe_no_escribe_ni_pide_ciclo() = runBlocking {
        var guardo = false

        val resultado = outbox.actualizar<PruebaEntity>(tipo, "nada", buscar = { null }, guardar = { guardo = true })

        assertEquals(ResultadoEscritura.NoEncontrada, resultado)
        assertTrue(!guardo)
        assertEquals(0, bd.operaciones.contarSinTerminar())
        verify(exactly = 0) { scheduler.requestSync() }
    }

    @Test
    fun si_falla_encolar_la_actualizacion_el_cambio_se_deshace() = runBlocking {
        bd.pruebas.insert(entidad("e1"))
        coEvery { encolador.encolarActualizacion(any(), any(), any()) } throws SQLiteException("error")

        val resultado = outbox.actualizar(
            tipo, "e1",
            buscar = { bd.pruebas.findActivaById("e1")?.let { Existente(it, it.version) } },
            guardar = { bd.pruebas.update(it.copy(titulo = "No debe quedar")) }
        )

        assertEquals(ResultadoEscritura.ErrorAlmacenamiento, resultado)
        assertEquals("Título", bd.pruebas.findById("e1")!!.titulo)
    }

    // --- eliminar ---

    @Test
    fun eliminar_marca_el_borrado_conserva_el_registro_y_encola_un_ELIMINAR() = runBlocking {
        bd.pruebas.insert(entidad("e1", version = 3))

        val resultado = outbox.eliminar(tipo, "e1") { bd.pruebas.findActivaById("e1")?.let { Existente(it, it.version) } }

        assertEquals("Título", (resultado as ResultadoEscritura.Ok).valor.titulo)
        assertNotNull("el registro sigue hasta que el servidor confirme", bd.pruebas.findById("e1"))
        assertNull("pero ya no se ve como activo", bd.pruebas.findActivaById("e1"))
        assertEquals(listOf("e1"), bd.eliminaciones.entidadesEliminadas(tipo))
        val op = bd.operaciones.siguienteElegible()!!
        assertEquals(TipoOperacion.ELIMINAR, op.type)
        assertEquals(3, op.baseVersion)
        verify(exactly = 1) { scheduler.requestSync() }
    }

    @Test
    fun eliminar_un_registro_que_no_existe_no_deja_marca_ni_operacion() = runBlocking {
        val resultado = outbox.eliminar<PruebaEntity>(tipo, "nada") { null }

        assertEquals(ResultadoEscritura.NoEncontrada, resultado)
        assertTrue(bd.eliminaciones.entidadesEliminadas(tipo).isEmpty())
        assertEquals(0, bd.operaciones.contarSinTerminar())
        verify(exactly = 0) { scheduler.requestSync() }
    }

    @Test
    fun si_falla_encolar_el_ELIMINAR_la_marca_de_borrado_se_deshace() = runBlocking {
        bd.pruebas.insert(entidad("e1"))
        coEvery { encolador.encolar(any(), any(), any(), any(), any()) } throws SQLiteFullException("lleno")

        val resultado = outbox.eliminar(tipo, "e1") { bd.pruebas.findActivaById("e1")?.let { Existente(it, it.version) } }

        assertEquals(ResultadoEscritura.SinEspacio, resultado)
        assertTrue(bd.eliminaciones.entidadesEliminadas(tipo).isEmpty())
        assertNotNull(bd.pruebas.findActivaById("e1"))
    }

    // --- EncoladorOperaciones ---

    @Test
    fun encolar_crea_una_PENDIENTE_con_id_propio_y_la_marca_pedida_o_la_del_reloj() = runBlocking {
        encolador.encolar(TipoOperacion.CREAR, tipo, "e1", createdAt = 42)
        encolador.encolar(TipoOperacion.ELIMINAR, tipo, "e2", baseVersion = 7)

        val a = bd.operaciones.siguienteElegible()!!
        assertEquals(42L, a.createdAt)
        assertEquals(EstadoOperacion.PENDIENTE, a.status)
        val porEntidad = listOf("e1", "e2").associateWith { id ->
            bd.db.query("SELECT operationId, createdAt, baseVersion FROM operaciones WHERE entityId = ?", arrayOf(id))
                .use { c -> c.moveToFirst(); Triple(c.getString(0), c.getLong(1), if (c.isNull(2)) null else c.getInt(2)) }
        }
        assertNotEquals(porEntidad.getValue("e1").first, porEntidad.getValue("e2").first)
        assertEquals(reloj.ahora, porEntidad.getValue("e2").second)
        assertEquals(7, porEntidad.getValue("e2").third)
        assertNull(porEntidad.getValue("e1").third)
    }

    @Test
    fun no_se_agrupa_sobre_una_operacion_incierta_ni_sobre_otra_entidad() = runBlocking {
        bd.operaciones.insert(operacion("incierta", 1, entidad = "e1", uncertain = true))
        bd.operaciones.insert(operacion("otra", 2, entidad = "e2"))

        encolador.encolarActualizacion(tipo, "e1", baseVersion = 1)

        assertEquals("la incierta ya pudo haberse aplicado: hace falta otra", 2, operacionesDe("e1"))
    }

    private fun hayRegistros() = bd.db.query("SELECT 1 FROM pruebas LIMIT 1", null).use { it.count > 0 }
}
