package com.spiralsoft.servare.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.spiralsoft.servare.data.local.entity.PruebaEntity
import com.spiralsoft.servare.data.repository.EncoladorOperaciones
import com.spiralsoft.servare.domain.model.EstadoOperacion
import com.spiralsoft.servare.domain.model.TipoEntidad
import com.spiralsoft.servare.domain.model.TipoOperacion
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock

// Outbox transaccional sobre el SQLite y el Room reales del dispositivo, con la base en un archivo
// (no en memoria): lo que las pruebas unitarias con Robolectric no pueden demostrar
@RunWith(AndroidJUnit4::class)
class OutboxInstrumentedTest {
    private val contexto: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val nombreBd = "outbox-prueba.db"

    private lateinit var db: AppDatabase
    private lateinit var encolador: EncoladorOperaciones

    private fun abrirBase() {
        db = Room.databaseBuilder(contexto, AppDatabase::class.java, nombreBd).build()
        encolador = EncoladorOperaciones(db.operationDao(), Clock.systemUTC())
    }

    private fun prueba(id: String) = PruebaEntity(id, "Título", "texto", 1, createdAt = System.currentTimeMillis())

    // Lo mismo que hace EscrituraOutbox.crear: registro + operación PENDIENTE en UNA transacción
    private suspend fun guardarConOperacion(id: String) = db.withTransaction {
        db.pruebaDao().insert(prueba(id))
        encolador.encolar(TipoOperacion.CREAR, TipoEntidad.PRUEBA, id)
    }

    @Before
    fun abrir() {
        contexto.deleteDatabase(nombreBd)
        abrirBase()
    }

    @After
    fun cerrar() {
        db.close()
        contexto.deleteDatabase(nombreBd)
    }

    @Test
    fun registro_y_operacion_PENDIENTE_se_guardan_en_una_transaccion() = runBlocking {
        guardarConOperacion("p1")

        assertNotNull(db.pruebaDao().findById("p1"))
        val op = db.operationDao().siguienteElegible()!!
        assertEquals(TipoOperacion.CREAR, op.type)
        assertEquals(EstadoOperacion.PENDIENTE, op.status)
        assertEquals("p1", op.entityId)
    }

    @Test
    fun si_falla_la_transaccion_no_queda_ni_registro_ni_operacion() = runBlocking {
        try {
            db.withTransaction {
                db.pruebaDao().insert(prueba("p1"))
                error("fallo antes de encolar")
            }
        } catch (_: IllegalStateException) {
        }

        assertNull(db.pruebaDao().findById("p1"))
        assertNull(db.operationDao().siguienteElegible())
    }

    @Test
    fun los_datos_sobreviven_a_cerrar_y_reabrir_la_base() = runBlocking {
        guardarConOperacion("p1")

        db.close()
        abrirBase()

        assertNotNull(db.pruebaDao().findById("p1"))
        assertEquals("p1", db.operationDao().siguienteElegible()?.entityId)
    }

    @Test
    fun observarTodas_emite_al_insertar() = runBlocking {
        assertTrue(db.pruebaDao().observarTodas().first().isEmpty())

        guardarConOperacion("p1")

        assertEquals(listOf("p1"), db.pruebaDao().observarTodas().first().map { it.entityId })
    }
}
