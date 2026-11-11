package com.spiralsoft.servare.data.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.WorkManagerTestInitHelper
import com.spiralsoft.servare.data.sesion.GestorSesion
import com.spiralsoft.servare.domain.model.EstadoSesion
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import javax.inject.Provider

// Single-flight: un solo ciclo en espera, con uno en curso, otro encadenado detrás.
// WorkManager de pruebas: los trabajos no corren hasta que la prueba cumple sus restricciones
@RunWith(RobolectricTestRunner::class)
class SyncSchedulerUnitTest {
    private lateinit var contexto: Context
    private lateinit var workManager: WorkManager
    private val sesion = MutableStateFlow<EstadoSesion>(EstadoSesion.ConSesion("a@b.c"))
    private val gestor = mockk<GestorSesion>().also { io.mockk.every { it.estado } returns sesion }
    private val motor = mockk<SyncEngine>()
    private val bloqueo = CompletableDeferred<ResultadoCiclo>()
    private lateinit var scheduler: SyncScheduler

    @Before
    fun preparar() {
        contexto = ApplicationProvider.getApplicationContext()
        val fabrica = object : WorkerFactory() {
            override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker? =
                if (workerClassName == SyncWorker::class.java.name) SyncWorker(appContext, workerParameters, motor, scheduler) else null
        }
        WorkManagerTestInitHelper.initializeTestWorkManager(
            contexto, Configuration.Builder().setWorkerFactory(fabrica).build()
        )
        workManager = WorkManager.getInstance(contexto)
        scheduler = SyncScheduler(contexto, Provider { gestor })
    }

    private fun estados(cola: String) = workManager.getWorkInfosForUniqueWork(cola).get().map { it.state }

    // requestSync no bloquea al llamador: lanza en su propio ámbito
    private suspend fun esperarCola(cola: String, cantidad: Int) =
        withTimeout(5_000) { while (estados(cola).size < cantidad) delay(20) }

    @Test
    fun sin_sesion_no_se_encola_nada() = runBlocking {
        for (estado in listOf(EstadoSesion.SinSesion, EstadoSesion.Cargando, EstadoSesion.Expirada("a@b.c"))) {
            sesion.value = estado
            scheduler.requestSync()
        }
        delay(400)

        assertTrue(estados("sync-ciclo").isEmpty())
    }

    @Test
    fun con_sesion_se_encola_un_ciclo() = runBlocking {
        scheduler.requestSync()
        esperarCola("sync-ciclo", 1)

        assertEquals(listOf(WorkInfo.State.ENQUEUED), estados("sync-ciclo"))
    }

    @Test
    fun con_un_ciclo_en_espera_no_se_encola_otro() = runBlocking {
        repeat(5) { scheduler.requestSync() }
        esperarCola("sync-ciclo", 1)
        delay(400)

        assertEquals(listOf(WorkInfo.State.ENQUEUED), estados("sync-ciclo"))
    }

    @Test
    fun con_un_ciclo_en_curso_la_nueva_solicitud_se_encadena_detras() = runBlocking {
        coEvery { motor.ejecutarCiclo() } coAnswers { bloqueo.await() }
        scheduler.requestSync()
        esperarCola("sync-ciclo", 1)
        val id = workManager.getWorkInfosForUniqueWork("sync-ciclo").get().single().id
        WorkManagerTestInitHelper.getTestDriver(contexto)!!.setAllConstraintsMet(id)
        withTimeout(5_000) { while (estados("sync-ciclo").first() != WorkInfo.State.RUNNING) delay(20) }

        scheduler.requestSync()
        esperarCola("sync-ciclo", 2)

        // WorkManager no garantiza el orden de la lista
        assertEquals(
            "el ciclo en curso sigue y la solicitud nueva espera detrás de él, sin perderse",
            listOf(WorkInfo.State.BLOCKED, WorkInfo.State.RUNNING), estados("sync-ciclo").sortedBy { it.name }
        )
        bloqueo.complete(ResultadoCiclo.Terminado(null))
        Unit
    }

    @Test
    fun cancelar_detiene_el_ciclo_y_el_reintento() = runBlocking {
        scheduler.requestSync()
        scheduler.programarReintento(60_000)
        esperarCola("sync-ciclo", 1)
        esperarCola("sync-reintento", 1)

        scheduler.cancelar()

        assertEquals(listOf(WorkInfo.State.CANCELLED), estados("sync-ciclo"))
        assertEquals(listOf(WorkInfo.State.CANCELLED), estados("sync-reintento"))
    }

    @Test
    fun el_reintento_va_en_su_propia_cola_con_la_espera_pedida() = runBlocking {
        scheduler.programarReintento(60_000)
        esperarCola("sync-reintento", 1)

        val info = workManager.getWorkInfosForUniqueWork("sync-reintento").get().single()
        assertEquals(60_000L, info.initialDelayMillis)
        assertTrue("no ocupa la cola de ciclos", estados("sync-ciclo").isEmpty())
    }

    @Test
    fun una_espera_negativa_se_toma_como_cero() = runBlocking {
        scheduler.programarReintento(-5_000)
        esperarCola("sync-reintento", 1)

        assertEquals(0L, workManager.getWorkInfosForUniqueWork("sync-reintento").get().single().initialDelayMillis)
    }

    @Test
    fun el_reintento_se_programa_aunque_no_haya_sesion_porque_el_motor_lo_vuelve_a_comprobar() = runBlocking {
        sesion.value = EstadoSesion.SinSesion
        scheduler.programarReintento(1_000)
        esperarCola("sync-reintento", 1)

        assertEquals(1, estados("sync-reintento").size)
    }
}
