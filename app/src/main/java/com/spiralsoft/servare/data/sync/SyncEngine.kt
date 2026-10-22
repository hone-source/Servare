package com.spiralsoft.servare.data.sync

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds
import com.spiralsoft.servare.data.connectivity.ConnectivityMonitor
import com.spiralsoft.servare.data.connectivity.ConnectivityState
import com.spiralsoft.servare.data.local.entity.OperationEntity
import com.spiralsoft.servare.data.local.operation.OperationStatus
import com.spiralsoft.servare.data.local.operation.OperationType
import com.spiralsoft.servare.data.queue.OperationQueue
import com.spiralsoft.servare.data.queue.nombreFicha
import com.spiralsoft.servare.data.remote.RemoteDataSource
import com.spiralsoft.servare.data.remote.RespuestaRemota
import com.spiralsoft.servare.domain.repository.FichaRepository
import com.spiralsoft.servare.notification.StatusNotifier

/**
 * Motor de sincronización: vacía la cola (push), descarga cambios (pull) y aplica reintentos con backoff.
 * Lo ejecuta [SyncWorker]; el trabajo único de WorkManager garantiza un solo ciclo a la vez.
 *
 * Ciclo: esperar CONECTADO → recuperar operaciones huérfanas → push → pull (si se pidió)
 * → programar el siguiente reintento → purgar. Siempre push antes de pull.
 * El resultado de cada operación finalizada llega al usuario vía [StatusNotifier].
 */
@Singleton
class SyncEngine @Inject constructor(
    private val queue: OperationQueue,
    private val remote: RemoteDataSource,
    private val monitor: ConnectivityMonitor,
    private val repository: FichaRepository,
    private val requests: SyncRequests,
    private val scheduler: SyncScheduler,
    private val notifier: StatusNotifier
) {

    suspend fun runCycle() {
        // Si no se llega a CONECTADO no se hace nada: al entrar, el monitor vuelve a pedir el ciclo
        if (!awaitConnected()) return
        // TODO: verificar sesión vigente con el Gestor de sesión (aún no existe); hoy la detecta el 401/403
        queue.recoverOrphans()
        requests.takeSync()
        do {
            if (!push()) break // sin conexión o sesión inválida: el pull espera
            if (requests.takePull()) pull()
        } while (requests.takeSync() && monitor.isConnected()) // llegaron solicitudes durante el ciclo
        queue.nextRetryAt()?.let(scheduler::scheduleRetry)
        queue.purge()
    }

    /**
     * Si WorkManager despertó la app, el monitor tarda unos segundos en confirmar el servidor.
     */
    private suspend fun awaitConnected(): Boolean = withTimeoutOrNull(CONNECTED_WAIT) {
        monitor.state.first { it == ConnectivityState.CONECTADO }
    } != null

    /**
     * Envía operaciones mientras haya elegibles (orden por entidad, dependsOn, backoff).
     * Devuelve `false` si se detuvo antes de vaciarla: sin conexión o sesión inválida.
     */
    private suspend fun push(): Boolean {
        var sincronizadas = 0
        try {
            while (monitor.isConnected()) {
                val op = queue.take() ?: return true
                when (val r = send(op)) {
                    is RespuestaRemota.Exito -> {
                        queue.complete(op, r.version)
                        sincronizadas++
                    }
                    RespuestaRemota.SinRespuesta -> {
                        // Se repetirá con el mismo operationId; perder la conexión no suma intento
                        queue.returnToPending(op, uncertain = true)
                        monitor.onRequestFailedByTimeout()
                    }
                    RespuestaRemota.Timeout -> {
                        failTemporarily(op, "El servidor no respondió a tiempo", uncertain = true)
                        monitor.onRequestFailedByTimeout()
                    }
                    is RespuestaRemota.ErrorTemporal ->
                        failTemporarily(op, "Error temporal del servidor (${r.codigo})", op.uncertain)
                    is RespuestaRemota.Conflicto -> {
                        queue.conflict(op, r.motivo ?: "Versión distinta en el servidor (${r.codigo})")
                        notifier.notificar(ResultadoSync.Conflicto(op.nombreFicha(), op.type))
                    }
                    is RespuestaRemota.Rechazada -> {
                        queue.fail(op, r.motivo ?: "Rechazada por el servidor (${r.codigo})")
                        notifier.notificar(ResultadoSync.Fallida(op.nombreFicha(), op.type, r.motivo, porIntentos = false))
                    }
                    RespuestaRemota.SesionInvalida -> {
                        // 401/403 pausa toda la cola: no es culpa de la operación, sigue PENDIENTE
                        queue.returnToPending(op, op.uncertain)
                        notifier.notificar(ResultadoSync.SesionRequerida)
                        return false
                    }
                }
            }
            return false
        } finally {
            if (sincronizadas > 0) notifier.notificar(ResultadoSync.Sincronizadas(sincronizadas))
        }
    }

    private suspend fun send(op: OperationEntity): RespuestaRemota<Unit> = when (op.type) {
        OperationType.CREATE, OperationType.UPDATE ->
            remote.putFicha(op.entityId, op.data, op.operationId, op.baseVersion)
        OperationType.DELETE ->
            remote.deleteFicha(op.entityId, op.operationId, op.baseVersion)
        // TODO: adjuntos (estado propio, subida por fragmentos); todavía no se encolan
        OperationType.ATTACH ->
            RespuestaRemota.Rechazada(null, "Los adjuntos todavía no se sincronizan")
    }

    private suspend fun failTemporarily(op: OperationEntity, error: String, uncertain: Boolean) {
        if (queue.failTemporarily(op, error, uncertain) == OperationStatus.FALLIDA) {
            notifier.notificar(ResultadoSync.Fallida(op.nombreFicha(), op.type, error, porIntentos = true))
        }
    }

    /**
     * Descarga las fichas del servidor y las entrega al repositorio. Las que tienen operaciones
     * sin terminar no se piden: un registro con cambios locales no se sobrescribe.
     */
    private suspend fun pull() {
        val ids = when (val r = remote.listFichaIds()) {
            is RespuestaRemota.Exito -> r.valor
            else -> return pullFailed(r)
        }
        for (id in ids) {
            if (!monitor.isConnected()) return requests.retainPull()
            if (queue.hasUnfinished(id)) continue
            when (val r = remote.getFicha(id)) {
                is RespuestaRemota.Exito -> repository.aplicarRemota(id, r.valor)
                is RespuestaRemota.Conflicto -> Unit    // se borró en el servidor entre la lista y la consulta
                else -> return pullFailed(r)
            }
        }
    }

    /** El pull queda pendiente para el siguiente ciclo. */
    private fun pullFailed(r: RespuestaRemota<*>) {
        requests.retainPull()
        when (r) {
            // El aviso de conexión ya lo explica
            RespuestaRemota.SinRespuesta, RespuestaRemota.Timeout -> monitor.onRequestFailedByTimeout()
            RespuestaRemota.SesionInvalida -> notifier.notificar(ResultadoSync.SesionRequerida)
            else -> notifier.notificar(ResultadoSync.DescargaFallida)
        }
    }

    private companion object {
        val CONNECTED_WAIT = 15.seconds
    }
}
