package com.spiralsoft.servare.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import com.spiralsoft.servare.data.local.operation.OperationStatus
import com.spiralsoft.servare.data.local.operation.OperationType

@Entity(
    tableName = "operations",
    indices = [
        Index("entityId"),
        Index("status", "nextAttemptAt"),
        Index("dependsOn")
    ]
)
data class OperationEntity(
    @PrimaryKey val operationId: String,
    val type: OperationType,
    val entityId: String,
    val dependsOn: String? = null,
    val data: String,                       // JSON; archivos solo por referencia
    val createdAt: Instant,
    val status: OperationStatus = OperationStatus.PENDIENTE,
    val attemptCount: Int = 0,
    val lastAttemptAt: Instant? = null,
    val nextAttemptAt: Instant? = null,
    val lastError: String? = null,
    val baseVersion: Int? = null,           // null en CREATE
    val uncertain: Boolean = false,
    val finishedAt: Instant? = null         // al pasar a COMPLETADA o DESCARTADA: cuenta la retención
)
