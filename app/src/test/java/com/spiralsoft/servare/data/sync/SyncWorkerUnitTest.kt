package com.spiralsoft.servare.data.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// El trabajo siempre termina en success: los fallos viven en el motor y un retry de WorkManager duplicaría el backoff
@RunWith(RobolectricTestRunner::class)
class SyncWorkerUnitTest {
    private val motor = mockk<SyncEngine>()
    private val scheduler = mockk<SyncScheduler>(relaxed = true)

    private fun ejecutar(resultado: ResultadoCiclo): ListenableWorker.Result = runBlocking {
        coEvery { motor.ejecutarCiclo() } returns resultado
        val fabrica = object : WorkerFactory() {
            override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker =
                SyncWorker(appContext, workerParameters, motor, scheduler)
        }
        TestListenableWorkerBuilder<SyncWorker>(ApplicationProvider.getApplicationContext())
            .setWorkerFactory(fabrica).build().doWork()
    }

    @Test
    fun un_ciclo_terminado_con_reintento_pendiente_lo_programa() {
        assertEquals(ListenableWorker.Result.success(), ejecutar(ResultadoCiclo.Terminado(reintentoEnMs = 40_000)))
        verify(exactly = 1) { scheduler.programarReintento(40_000) }
    }

    @Test
    fun un_ciclo_terminado_sin_nada_que_reintentar_no_programa_nada() {
        assertEquals(ListenableWorker.Result.success(), ejecutar(ResultadoCiclo.Terminado(null)))
        verify(exactly = 0) { scheduler.programarReintento(any()) }
    }

    @Test
    fun un_pull_fallido_no_hace_fallar_el_trabajo() {
        assertEquals(ListenableWorker.Result.success(), ejecutar(ResultadoCiclo.Terminado(null, errorPull = "500")))
    }

    @Test
    fun sin_servidor_o_sin_sesion_termina_en_success_sin_reprogramar() {
        for (resultado in listOf(ResultadoCiclo.SinServidor, ResultadoCiclo.SesionExpirada)) {
            assertEquals(ListenableWorker.Result.success(), ejecutar(resultado))
        }
        verify(exactly = 0) { scheduler.programarReintento(any()) }
    }
}
