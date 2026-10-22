package com.spiralsoft.servare.data.sync

import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Un ciclo del motor de sincronización.
 * Los reintentos de cada operación los lleva la cola (backoff propio), no WorkManager.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val engine: SyncEngine
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        engine.runCycle()
        Result.success()
    } catch (e: SQLiteException) {
        // Falla del almacenamiento local: WorkManager repite el ciclo con su backoff
        Result.retry()
    }
}

/**
 * Vence el `nextAttemptAt` más próximo: pide el ciclo por el camino normal,
 * así sigue habiendo un solo ciclo a la vez.
 */
@HiltWorker
class SyncRetryWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val scheduler: SyncScheduler
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        scheduler.requestSync()
        return Result.success()
    }
}
