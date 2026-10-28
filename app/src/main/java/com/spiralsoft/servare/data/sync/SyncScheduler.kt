package com.spiralsoft.servare.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.await
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

// Punto único para pedir un ciclo de sincronización (single-flight)
@Singleton
class SyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // WorkManager se inicializa en ServareApp.onCreate, no al construir este objeto
    private val workManager by lazy { WorkManager.getInstance(context) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val candado = Mutex() // revisar y encolar es una sola acción

    private val restricciones = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    // Siempre fuera de una transacción de Room. No bloquea al llamador
    fun requestSync() {
        scope.launch {
            candado.withLock {
                // Con un ciclo en espera no se encola otro: ese leerá la cola cuando arranque
                val enEspera = workManager.getWorkInfosForUniqueWorkFlow(CICLO).first()
                    .any { it.state == WorkInfo.State.ENQUEUED }
                if (enEspera) return@withLock
                // Con uno en curso se encadena detrás: KEEP perdía solicitudes al final del ciclo
                workManager.enqueueUniqueWork(CICLO, ExistingWorkPolicy.APPEND_OR_REPLACE, solicitud(0)).await()
            }
        }
    }

    // Despierta la cola cuando vence el siguiente reintento, aunque la app esté cerrada.
    // Cola propia: así una solicitud nueva no queda detrás de la espera del backoff
    fun programarReintento(retrasoMs: Long) {
        scope.launch {
            workManager.enqueueUniqueWork(
                REINTENTO, ExistingWorkPolicy.APPEND_OR_REPLACE, solicitud(retrasoMs.coerceAtLeast(0))
            ).await()
        }
    }

    // Cancela el ciclo en curso y los que esperan (cierre de sesión). Las operaciones no se tocan
    suspend fun cancelar() {
        workManager.cancelUniqueWork(CICLO).await()
        workManager.cancelUniqueWork(REINTENTO).await()
    }

    private fun solicitud(retrasoMs: Long) = OneTimeWorkRequestBuilder<SyncWorker>()
        .setConstraints(restricciones)
        .setInitialDelay(retrasoMs, TimeUnit.MILLISECONDS)
        .build()

    private companion object {
        const val CICLO = "sync-ciclo"
        const val REINTENTO = "sync-reintento"
    }
}
