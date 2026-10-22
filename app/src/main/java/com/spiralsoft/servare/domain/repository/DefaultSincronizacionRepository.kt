package com.spiralsoft.servare.domain.repository

import android.database.sqlite.SQLiteException
import androidx.room.withTransaction
import com.spiralsoft.servare.data.local.AppDatabase
import com.spiralsoft.servare.data.local.dao.FichaDao
import com.spiralsoft.servare.data.local.entity.FichaEntity
import com.spiralsoft.servare.data.local.entity.OperationEntity
import com.spiralsoft.servare.data.local.operation.OperationStatus
import com.spiralsoft.servare.data.local.operation.OperationType
import com.spiralsoft.servare.data.queue.OperationQueue
import com.spiralsoft.servare.data.queue.OperationRetryPolicy
import com.spiralsoft.servare.data.queue.nombreFicha
import com.spiralsoft.servare.data.sync.SyncScheduler
import com.spiralsoft.servare.domain.model.CambioPorResolver
import com.spiralsoft.servare.domain.model.Ficha
import com.spiralsoft.servare.domain.model.MotivoProblema
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.time.Clock
import java.time.Instant
import javax.inject.Inject

class DefaultSincronizacionRepository @Inject constructor(
    private val database: AppDatabase,
    private val fichaDao: FichaDao,
    private val queue: OperationQueue,
    private val scheduler: SyncScheduler,
    private val retryPolicy: OperationRetryPolicy,
    private val json: Json,
    private val clock: Clock
) : SincronizacionRepository {

    override fun observarCambiosPorResolver(): Flow<List<CambioPorResolver>> =
        queue.observeNeedingAttention().map { ops -> ops.map { it.toCambio() } }

    override suspend fun reintentar(operationId: String): ResultadoDecision = try {
        if (queue.retry(operationId)) {
            scheduler.requestSync()
            ResultadoDecision.Hecha
        } else {
            ResultadoDecision.YaResuelto
        }
    } catch (_: SQLiteException) {
        ResultadoDecision.ErrorAlmacenamiento
    }

    override suspend fun descartar(operationId: String): ResultadoDecision = try {
        val descartadas = database.withTransaction {
            queue.discard(operationId).also { ops ->
                ops.groupBy { it.entityId }.forEach { (entityId, deLaFicha) -> revertir(entityId, deLaFicha) }
            }
        }
        if (descartadas.isEmpty()) {
            ResultadoDecision.YaResuelto
        } else {
            // La versión del servidor reemplaza a la local en cuanto haya conexión
            scheduler.requestSync(pull = true)
            ResultadoDecision.Hecha
        }
    } catch (_: SQLiteException) {
        ResultadoDecision.ErrorAlmacenamiento
    }

    /** Deja la ficha como la conoce el servidor; corre en la transacción del descarte. */
    private suspend fun revertir(entityId: String, descartadas: List<OperationEntity>) {
        val ordenadas = descartadas.sortedBy { it.createdAt }
        if (ordenadas.first().type == OperationType.CREATE) {
            // El servidor no confirmó el registro: volver a su versión es que no exista
            fichaDao.deleteById(entityId)
        } else if (fichaDao.findById(entityId) == null) {
            // Se descartó una eliminación: la ficha vuelve con la copia que guardó la operación;
            // si el servidor tiene otra versión, el pull la reemplaza
            val copia = ordenadas.last().fichaGuardada() ?: return
            val ahora = Instant.now(clock)
            fichaDao.upsert(FichaEntity(entityId, copia, createdAt = ahora, updatedAt = ahora))
        }
    }

    private fun OperationEntity.fichaGuardada(): Ficha? = try {
        json.decodeFromString<Ficha>(data)
    } catch (_: SerializationException) {
        null
    }

    private suspend fun OperationEntity.toCambio(): CambioPorResolver {
        val motivo = when {
            status == OperationStatus.CONFLICTO -> MotivoProblema.CONFLICTO
            // Solo los timeout y 5xx suman intentos; un rechazo llega antes de agotarlos
            retryPolicy.isExhausted(attemptCount) -> MotivoProblema.INTENTOS_AGOTADOS
            else -> MotivoProblema.RECHAZADO
        }
        return CambioPorResolver(
            operationId = operationId,
            entityId = entityId,
            nombreFicha = nombreFicha(),
            tipo = type,
            motivo = motivo,
            detalle = lastError.takeIf { motivo == MotivoProblema.RECHAZADO },
            creadoEl = createdAt,
            dependientes = queue.countDependents(operationId)
        )
    }
}
