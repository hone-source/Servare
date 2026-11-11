package com.spiralsoft.servare.testutil

import com.spiralsoft.servare.data.local.entity.OperationEntity
import com.spiralsoft.servare.domain.model.EstadoOperacion
import com.spiralsoft.servare.domain.model.TipoEntidad
import com.spiralsoft.servare.domain.model.TipoOperacion

// Operación de la cola con los valores mínimos
// cada prueba cambia solo lo que le importa
fun operacion(
    id: String,
    createdAt: Long,
    estado: EstadoOperacion = EstadoOperacion.PENDIENTE,
    tipo: TipoOperacion = TipoOperacion.ACTUALIZAR,
    entidad: String = "e1",
    tipoEntidad: TipoEntidad = TipoEntidad.PRUEBA,
    dependsOn: String? = null,
    finishedAt: Long? = null,
    nextAttemptAt: Long? = null,
    baseVersion: Int? = null,
    uncertain: Boolean = false
) = OperationEntity(
    operationId = id,
    type = tipo,
    entityType = tipoEntidad,
    entityId = entidad,
    dependsOn = dependsOn,
    createdAt = createdAt,
    status = estado,
    finishedAt = finishedAt,
    nextAttemptAt = nextAttemptAt,
    baseVersion = baseVersion,
    uncertain = uncertain
)
