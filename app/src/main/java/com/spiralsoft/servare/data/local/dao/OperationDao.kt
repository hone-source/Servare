package com.spiralsoft.servare.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import com.spiralsoft.servare.data.local.entity.OperationEntity

@Dao
interface OperationDao {
    @Insert
    suspend fun insert(op: OperationEntity)

    @Update
    suspend fun update(op: OperationEntity)

    @Query("SELECT * FROM operations WHERE operationId = :operationId")
    suspend fun findById(operationId: String): OperationEntity?

    @Query("SELECT * FROM operations WHERE operationId IN (:ids) ORDER BY rowid")
    suspend fun findByIds(ids: List<String>): List<OperationEntity>

    @Query("""
        SELECT * FROM operations o
        WHERE o.status = 'PENDIENTE'
          AND (o.nextAttemptAt IS NULL OR o.nextAttemptAt <= :now)
          AND (o.dependsOn IS NULL OR NOT EXISTS (
                SELECT 1 FROM operations d
                WHERE d.operationId = o.dependsOn AND d.status <> 'COMPLETADA'))
          AND NOT EXISTS (
                SELECT 1 FROM operations p
                WHERE p.entityId = o.entityId
                  AND p.rowid < o.rowid
                  AND p.status NOT IN ('COMPLETADA','DESCARTADA'))
        ORDER BY o.rowid
        LIMIT 1
    """)
    suspend fun nextEligible(now: Instant): OperationEntity?

    @Query("""
        SELECT d.operationId FROM operations d, operations o
        WHERE o.operationId = :operationId
          AND ((d.entityId = o.entityId AND d.rowid > o.rowid) OR d.dependsOn = o.operationId)
          AND d.status IN ('PENDIENTE','BLOQUEADA')
        ORDER BY d.rowid
    """)
    suspend fun findDependentIds(operationId: String): List<String>

    @Query("""
        SELECT EXISTS (
            SELECT 1 FROM operations
            WHERE (entityId = :entityId OR operationId = :dependsOn)
              AND status IN ('FALLIDA','CONFLICTO','BLOQUEADA'))
    """)
    suspend fun hasBlockingPredecessor(entityId: String, dependsOn: String?): Boolean

    @Query("SELECT * FROM operations WHERE status IN ('FALLIDA','CONFLICTO')")
    suspend fun findFailedOrConflicted(): List<OperationEntity>

    @Query("UPDATE operations SET status = 'PENDIENTE' WHERE status = 'BLOQUEADA'")
    suspend fun unblockAll()

    @Query("UPDATE operations SET status = 'BLOQUEADA' WHERE operationId IN (:ids) AND status = 'PENDIENTE'")
    suspend fun block(ids: List<String>)

    @Query("""
        UPDATE operations SET status = 'DESCARTADA', finishedAt = :now
        WHERE operationId IN (:ids) AND status IN ('PENDIENTE','BLOQUEADA')
    """)
    suspend fun discard(ids: List<String>, now: Instant)

    @Query("UPDATE operations SET status = 'PENDIENTE', uncertain = 1 WHERE status = 'PROCESANDO'")
    suspend fun resetOrphans(): Int

    @Query("""
        UPDATE operations SET status = 'PENDIENTE'
        WHERE status = 'REINTENTABLE' AND nextAttemptAt <= :now
    """)
    suspend fun releaseDueRetries(now: Instant)

    @Query("SELECT MIN(nextAttemptAt) FROM operations WHERE status = 'REINTENTABLE'")
    suspend fun earliestRetryAt(): Instant?

    @Query("""
        UPDATE operations SET baseVersion = :version
        WHERE entityId = :entityId
          AND rowid > (SELECT rowid FROM operations WHERE operationId = :operationId)
          AND status IN ('PENDIENTE','REINTENTABLE','BLOQUEADA')
    """)
    suspend fun rebaseFollowing(operationId: String, entityId: String, version: Int)

    @Query("""
        SELECT EXISTS (
            SELECT 1 FROM operations
            WHERE entityId = :entityId AND status NOT IN ('COMPLETADA','DESCARTADA'))
    """)
    suspend fun hasUnfinished(entityId: String): Boolean

    @Query("""
        DELETE FROM operations
        WHERE status IN ('COMPLETADA','DESCARTADA')
          AND COALESCE(finishedAt, createdAt) < :before
    """)
    suspend fun purgeFinishedBefore(before: Instant): Int

    @Query("SELECT * FROM operations WHERE entityId = :entityId ORDER BY rowid")
    fun observeByEntity(entityId: String): Flow<List<OperationEntity>>

    @Query("SELECT * FROM operations WHERE status IN ('FALLIDA','CONFLICTO') ORDER BY rowid")
    fun observeNeedingAttention(): Flow<List<OperationEntity>>

    @Query("""
        SELECT DISTINCT entityId FROM operations
        WHERE status IN ('FALLIDA','CONFLICTO','BLOQUEADA')
    """)
    fun observeEntityIdsNeedingAttention(): Flow<List<String>>

    @Query("""
        SELECT DISTINCT entityId FROM operations
        WHERE status NOT IN ('COMPLETADA','DESCARTADA')
    """)
    fun observeUnsyncedEntityIds(): Flow<List<String>>

}
