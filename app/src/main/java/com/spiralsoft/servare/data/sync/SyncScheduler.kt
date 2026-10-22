package com.spiralsoft.servare.data.sync

import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.await
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Disparadores del motor de sincronización.
 * Single-flight: un solo ciclo a la vez, como trabajo único [WorkManagerSyncScheduler.SYNC_WORK];
 * un ciclo nuevo se encadena detrás del que corre, nunca en paralelo.
 */
interface SyncScheduler {
    /**
     * Solicita un ciclo. Si hay uno esperando a empezar, ese la atiende; si hay uno en curso, se
     * encadena otro detrás. [pull]: también descargar cambios (recuperación de conexión, inicio).
     * No lanza: si WorkManager no pudo registrar el ciclo, lo toma el siguiente disparador.
     */
    suspend fun requestSync(pull: Boolean = false)

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

    /** Revisar y encolar sin intercalarse: dos solicitudes simultáneas no encadenan dos ciclos. */
    private val enqueueLock = Mutex()

    override suspend fun requestSync(pull: Boolean) {
        // Primero la solicitud y después la revisión: un ciclo que aún no empieza la lee al arrancar
        requests.add(pull)
        try {
            enqueueLock.withLock {
                val enEspera = workManager.getWorkInfosForUniqueWorkFlow(SYNC_WORK).first()
                    .any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED }
                if (enEspera) return
                // Con uno en curso, KEEP descartaría la solicitud si llegó después de su última revisión
                // de la cola; APPEND_OR_REPLACE la encadena detrás (si el anterior falló, inicia otra cadena)
                workManager.enqueueUniqueWork(
                    SYNC_WORK,
                    ExistingWorkPolicy.APPEND_OR_REPLACE,
                    OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(withNetwork).build()
                ).await()
            }
        } catch (_: SQLiteException) {
            // La operación ya está PENDIENTE en Room: el guardado no falló y el siguiente disparador la envía
        }
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
