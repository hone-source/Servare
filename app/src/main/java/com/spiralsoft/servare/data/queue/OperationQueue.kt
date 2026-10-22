package com.spiralsoft.servare.data.queue

import androidx.room.withTransaction
import com.google.gson.JsonParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.toJavaDuration
import com.spiralsoft.servare.data.local.AppDatabase
import com.spiralsoft.servare.data.local.dao.OperationDao
import com.spiralsoft.servare.data.local.entity.OperationEntity
import com.spiralsoft.servare.data.local.operation.OperationStatus
import com.spiralsoft.servare.data.local.operation.OperationType

/**
 * Cola de operaciones (outbox) sobre Room.
 *
 * - El repositorio encola con [enqueue] DENTRO de su transacción: cambio + operación PENDIENTE.
 * - El motor de sincronización toma con [take] y registra el resultado con [complete],
 *   [returnToPending], [failTemporarily], [conflict] o [fail].
 * - El usuario decide con [retry] y [discard].
 *
 * Cada transición que toca más de una fila corre en una transacción (si ya hay una abierta, se une).
 * Las dependientes de una operación FALLIDA o en CONFLICTO (misma entidad o `dependsOn`, a cualquier
 * profundidad) quedan BLOQUEADAS y se recalculan cada vez que una operación entra o sale de esos estados.
 */
@Singleton
class OperationQueue @Inject constructor(
    private val database: AppDatabase,
    private val dao: OperationDao,
    private val retryPolicy: OperationRetryPolicy,
    private val clock: Clock
) {

    /**
     * Registra una operación nueva y devuelve su `operationId`, que no cambia en ningún reintento.
     * Si algo anterior de la entidad (o su dependencia) ya está FALLIDA, en CONFLICTO o BLOQUEADA,
     * nace BLOQUEADA. El llamador pide la sincronización fuera de la transacción.
     */
    suspend fun enqueue(
        type: OperationType,
        entityId: String,
        data: String,
        baseVersion: Int? = null,
        dependsOn: String? = null
    ): String = database.withTransaction {
        val blocked = dao.hasBlockingPredecessor(entityId, dependsOn)
        val op = OperationEntity(
            operationId = UUID.randomUUID().toString(),
            type = type,
            entityId = entityId,
            dependsOn = dependsOn,
            data = data,
            createdAt = now(),
            status = if (blocked) OperationStatus.BLOQUEADA else OperationStatus.PENDIENTE,
            baseVersion = baseVersion
        )
        dao.insert(op)
        op.operationId
    }

    /** Entidades con operaciones sin terminar (RF-08). */
    fun observeUnsyncedEntityIds(): Flow<Set<String>> =
        dao.observeUnsyncedEntityIds().map { it.toSet() }

    /** Un registro con operaciones sin terminar no se sobrescribe con el pull. */
    suspend fun hasUnfinished(entityId: String): Boolean = dao.hasUnfinished(entityId)

    /** Al iniciar el ciclo: lo que quedó en PROCESANDO vuelve a PENDIENTE como incierto. */
    suspend fun recoverOrphans() {
        dao.resetOrphans()
    }

    /** Pasa a PROCESANDO la siguiente operación elegible; antes libera los reintentos vencidos. */
    suspend fun take(): OperationEntity? = database.withTransaction {
        val now = now()
        dao.releaseDueRetries(now)
        dao.nextEligible(now)
            ?.copy(status = OperationStatus.PROCESANDO, lastAttemptAt = now)
            ?.also { dao.update(it) }
    }

    /** El servidor confirmó. Con [newVersion], las siguientes de la entidad parten de ella. */
    suspend fun complete(op: OperationEntity, newVersion: Int?) = database.withTransaction {
        dao.update(
            op.copy(
                status = OperationStatus.COMPLETADA,
                uncertain = false,
                lastError = null,
                nextAttemptAt = null,
                finishedAt = now()
            )
        )
        if (newVersion != null) dao.rebaseFollowing(op.operationId, op.entityId, newVersion)
    }

    /** Sin sumar intento: conexión perdida (resultado incierto) o sesión inválida (la cola se pausa). */
    suspend fun returnToPending(op: OperationEntity, uncertain: Boolean) {
        dao.update(op.copy(status = OperationStatus.PENDIENTE, uncertain = uncertain))
    }

    /**
     * Timeout o 5xx con el servidor alcanzable: suma un intento.
     * Devuelve REINTENTABLE, o FALLIDA si se agotaron los intentos.
     */
    suspend fun failTemporarily(
        op: OperationEntity,
        error: String,
        uncertain: Boolean
    ): OperationStatus = database.withTransaction {
        val attempts = op.attemptCount + 1
        if (retryPolicy.isExhausted(attempts)) {
            dao.update(
                op.copy(
                    status = OperationStatus.FALLIDA,
                    attemptCount = attempts,
                    lastError = error,
                    nextAttemptAt = null,
                    uncertain = uncertain
                )
            )
            recomputeBlocked()
            OperationStatus.FALLIDA
        } else {
            dao.update(
                op.copy(
                    status = OperationStatus.REINTENTABLE,
                    attemptCount = attempts,
                    lastError = error,
                    nextAttemptAt = now() + retryPolicy.nextDelay(attempts).toJavaDuration(),
                    uncertain = uncertain
                )
            )
            OperationStatus.REINTENTABLE
        }
    }

    /** Versión distinta de baseVersion: requiere decisión del usuario. */
    suspend fun conflict(op: OperationEntity, error: String) =
        finishWithProblem(op, OperationStatus.CONFLICTO, error)

    /** Error definitivo (validación, regla de negocio, archivo demasiado grande). */
    suspend fun fail(op: OperationEntity, error: String) =
        finishWithProblem(op, OperationStatus.FALLIDA, error)

    private suspend fun finishWithProblem(op: OperationEntity, status: OperationStatus, error: String) =
        database.withTransaction {
            dao.update(op.copy(status = status, lastError = error, nextAttemptAt = null, uncertain = false))
            recomputeBlocked()
        }

    /**
     * El usuario reintenta (FALLIDA) o conserva su versión (CONFLICTO): vuelve a PENDIENTE con
     * todos sus intentos y libera sus dependientes. En un CONFLICTO se quita la baseVersion: se envía
     * sin If-Match y reemplaza la versión del servidor, que es lo que el usuario decidió.
     * El llamador pide la sincronización después.
     */
    suspend fun retry(operationId: String): Boolean = database.withTransaction {
        val op = dao.findById(operationId)
        if (op == null || op.status !in PROBLEM_STATES) return@withTransaction false
        dao.update(
            op.copy(
                status = OperationStatus.PENDIENTE,
                attemptCount = 0,
                nextAttemptAt = null,
                lastError = null,
                baseVersion = if (op.status == OperationStatus.CONFLICTO) null else op.baseVersion
            )
        )
        recomputeBlocked()
        true
    }

    /**
     * El usuario abandona su cambio: la operación y las que dependen de ella pasan a DESCARTADA.
     * Devuelve las descartadas tal como estaban (la primera es [operationId]), o vacía si ya no
     * esperaba una decisión. Se une a la transacción del llamador para revertir el registro.
     */
    suspend fun discard(operationId: String): List<OperationEntity> = database.withTransaction {
        val op = dao.findById(operationId)
        if (op == null || op.status !in PROBLEM_STATES) return@withTransaction emptyList()
        val now = now()
        val dependents = dao.findByIds(dependentsOf(operationId))
        dao.update(op.copy(status = OperationStatus.DESCARTADA, finishedAt = now))
        if (dependents.isNotEmpty()) dao.discard(dependents.map { it.operationId }, now)
        recomputeBlocked()
        listOf(op) + dependents
    }

    /** FALLIDA y CONFLICTO, en orden de generación: esperan reintentar o descartar. */
    fun observeNeedingAttention(): Flow<List<OperationEntity>> = dao.observeNeedingAttention()

    /** Entidades con alguna operación FALLIDA, en CONFLICTO o BLOQUEADA. */
    fun observeEntityIdsNeedingAttention(): Flow<Set<String>> =
        dao.observeEntityIdsNeedingAttention().map { it.toSet() }

    /** Cuántas operaciones esperan a [operationId] (misma entidad y dependsOn). */
    suspend fun countDependents(operationId: String): Int = dependentsOf(operationId).size

    /** Momento del reintento más próximo, o `null` si no hay operaciones REINTENTABLE. */
    suspend fun nextRetryAt(): Instant? = dao.earliestRetryAt()

    /** COMPLETADA y DESCARTADA se conservan [RETENTION]; las demás no se purgan. */
    suspend fun purge(): Int = dao.purgeFinishedBefore(now() - RETENTION)

    /** Debe correr dentro de una transacción. */
    private suspend fun recomputeBlocked() {
        dao.unblockAll()
        for (op in dao.findFailedOrConflicted()) {
            val ids = dependentsOf(op.operationId)
            if (ids.isNotEmpty()) dao.block(ids)
        }
    }

    /** Cierre transitivo de las que esperan a [operationId] (misma entidad y dependsOn). */
    private suspend fun dependentsOf(operationId: String): List<String> {
        val found = LinkedHashSet<String>()
        val pending = ArrayDeque(listOf(operationId))
        while (pending.isNotEmpty()) {
            for (id in dao.findDependentIds(pending.removeFirst())) {
                if (found.add(id)) pending.add(id)
            }
        }
        return found.toList()
    }

    private fun now(): Instant = Instant.now(clock)

    private companion object {
        val RETENTION: Duration = Duration.ofDays(7)
        val PROBLEM_STATES = setOf(OperationStatus.FALLIDA, OperationStatus.CONFLICTO)
    }
}

/** Nombre de la ficha guardado en el `data` de la operación, para los mensajes. */
fun OperationEntity.nombreFicha(): String? = try {
    JsonParser.parseString(data).asJsonObject.get("nombre")?.asString?.ifBlank { null }
} catch (e: Exception) {
    null
}
