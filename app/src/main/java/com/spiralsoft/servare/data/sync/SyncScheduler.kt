package com.spiralsoft.servare.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Disparadores del motor de sincronización.
 * Single-flight: un solo ciclo a la vez, como trabajo único [WorkManagerSyncScheduler.SYNC_WORK] con KEEP.
 */
interface SyncScheduler {
    /**
     * Solicita un ciclo. Si ya hay uno en curso no se inicia otro: el que corre vuelve a revisar
     * la cola antes de terminar. [pull]: también descargar cambios (recuperación de conexión, inicio).
     */
    fun requestSync(pull: Boolean = false)

    /** Programa un ciclo para cuando venza el `nextAttemptAt` más próximo. */
    fun scheduleRetry(at: Instant)
}

/**
 * Solicitudes que el ciclo en curso todavía no atiende. Viven en memoria: si el proceso muere,
 * al volver a CONECTADO el monitor pide el ciclo con pull.
 */
@Singleton
class SyncRequests @Inject constructor() {
    private val sync = AtomicBoolean(false)
    private val pull = AtomicBoolean(false)

    fun add(pull: Boolean) {
        if (pull) this.pull.set(true)
        sync.set(true)
    }

    /** El pull falló o se interrumpió: lo hará el siguiente ciclo, sin forzar otra vuelta del actual. */
    fun retainPull() = pull.set(true)

    /** `true` si hubo solicitudes desde la última llamada. */
    fun takeSync(): Boolean = sync.getAndSet(false)

    fun takePull(): Boolean = pull.getAndSet(false)
}

@Singleton
class WorkManagerSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val requests: SyncRequests,
    private val clock: Clock
) : SyncScheduler {

    // Se obtiene al usarlo: la configuración con HiltWorkerFactory la da ServareApp una vez inyectada
    private val workManager by lazy { WorkManager.getInstance(context) }

    // El SO solo despierta el trabajo con red; que el servidor responda lo confirma el monitor
    private val withNetwork = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    override fun requestSync(pull: Boolean) {
        requests.add(pull)
        workManager.enqueueUniqueWork(
            SYNC_WORK,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(withNetwork).build()
        )
    }

    override fun scheduleRetry(at: Instant) {
        val delay = Duration.between(Instant.now(clock), at).coerceAtLeast(Duration.ZERO)
        workManager.enqueueUniqueWork(
            RETRY_WORK,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<SyncRetryWorker>()
                .setInitialDelay(delay)
                .setConstraints(withNetwork)
                .build()
        )
    }

    companion object {
        const val SYNC_WORK = "sync"
        const val RETRY_WORK = "sync-reintento"
    }

}
