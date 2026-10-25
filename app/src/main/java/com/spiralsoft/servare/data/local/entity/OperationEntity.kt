package com.spiralsoft.servare.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.spiralsoft.servare.domain.model.EstadoOperacion
import com.spiralsoft.servare.domain.model.TipoEntidad
import com.spiralsoft.servare.domain.model.TipoOperacion

// Operación de la cola (outbox)
@Entity(
    tableName = "operaciones",
    indices = [Index("entityType", "entityId"), Index("status")]
)
data class OperationEntity(
    @PrimaryKey val operationId: String,    // constante en todos los reintentos
    val type: TipoOperacion,
    val entityType: TipoEntidad,
    val entityId: String,
    val dependsOn: String? = null,
    val createdAt: Long,                    // epoch ms; ordena las operaciones de una misma entidad
    val status: EstadoOperacion = EstadoOperacion.PENDIENTE,
    val attemptCount: Int = 0,
    val lastAttemptAt: Long? = null,
    val nextAttemptAt: Long? = null,
    val lastError: String? = null,
    val baseVersion: Int? = null,           // vacía en CREAR
    val uncertain: Boolean = false
)
