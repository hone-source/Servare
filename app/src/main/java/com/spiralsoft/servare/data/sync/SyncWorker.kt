package com.spiralsoft.servare.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

// Los fallos se resuelven dentro del motor (estados de la operación y backoff),
// por eso el trabajo siempre termina en success: un retry de WorkManager duplicaría el backoff
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val motor: SyncEngine,
    private val scheduler: SyncScheduler
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val resultado = motor.ejecutarCiclo()
        if (resultado is ResultadoCiclo.Terminado && resultado.reintentoEnMs != null) {
            scheduler.programarReintento(resultado.reintentoEnMs)
        }
        return Result.success()
    }
}
