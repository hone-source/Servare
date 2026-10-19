package com.spiralsoft.servare.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import com.spiralsoft.servare.data.local.entity.OperationEntity
import com.spiralsoft.servare.data.local.operation.OperationStatus

@Dao
interface OperationDao {
    @Insert
    suspend fun insert(op: OperationEntity)

    /** Siguiente operación elegible: respeta backoff, dependsOn y orden por entidad. */
    @Query("""
        SELECT * FROM operations o
        WHERE o.status = 'PENDIENTE'
          AND (o.nextAttemptAt IS NULL OR o.nextAttemptAt <= :now)
          AND (o.dependsOn IS NULL OR EXISTS (
                SELECT 1 FROM operations d
                WHERE d.operationId = o.dependsOn AND d.status = 'COMPLETADA'))
          AND NOT EXISTS (
                SELECT 1 FROM operations p
                WHERE p.entityId = o.entityId
                  AND p.createdAt < o.createdAt
                  AND p.status NOT IN ('COMPLETADA','DESCARTADA'))
        ORDER BY o.createdAt
        LIMIT 1
    """)
    suspend fun nextEligible(now: Instant): OperationEntity?

    @Query("UPDATE operations SET status = :status WHERE operationId = :operationId")
    suspend fun updateStatus(operationId: String, status: OperationStatus)

    @Query("""
        UPDATE operations SET status = 'BLOQUEADA'
        WHERE entityId = :entityId AND status = 'PENDIENTE'
    """)
    suspend fun blockDependents(entityId: String)

    @Query("SELECT * FROM operations WHERE entityId = :entityId ORDER BY createdAt")
    fun observeByEntity(entityId: String): Flow<List<OperationEntity>>

    /** Para marcar "pendiente de sincronizar" en la UI (RF-08). */
    @Query("""
        SELECT DISTINCT entityId FROM operations
        WHERE status NOT IN ('COMPLETADA','DESCARTADA')
    """)
    fun observeUnsyncedEntityIds(): Flow<List<String>>
}
