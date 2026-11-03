package com.spiralsoft.servare.data.repository

import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import com.spiralsoft.servare.data.local.dao.OperationDao
import com.spiralsoft.servare.data.local.entity.OperationEntity
import com.spiralsoft.servare.domain.model.EstadoOperacion
import com.spiralsoft.servare.domain.model.TipoEntidad
import com.spiralsoft.servare.domain.model.TipoOperacion

// Único lugar que escribe operaciones PENDIENTE en la cola (outbox).
// Solo inserta: el llamador lo invoca dentro de la misma transacción que el cambio del registro
// y pide requestSync() después de ella
class EncoladorOperaciones @Inject constructor(
    private val operationDao: OperationDao,
    private val clock: Clock
) {
    // createdAt ordena las operaciones de una entidad: el que guarda el registro pasa la misma marca
    // que usó para él, y quien rehace una operación conserva la de la original
    suspend fun encolar(
        tipo: TipoOperacion,
        entidad: TipoEntidad,
        entityId: String,
        createdAt: Long = clock.millis(),
        baseVersion: Int? = null
    ) = operationDao.insert(
        OperationEntity(
            operationId = UUID.randomUUID().toString(),
            type = tipo,
            entityType = entidad,
            entityId = entityId,
            createdAt = createdAt,
            status = EstadoOperacion.PENDIENTE,
            baseVersion = baseVersion
        )
    )

    // Si ya hay una operación sin enviar (CREAR o ACTUALIZAR), enviará el contenido actual al leerlo
    // y no hace falta otra
    suspend fun encolarActualizacion(entidad: TipoEntidad, entityId: String, baseVersion: Int?) {
        if (operationDao.buscarAgrupable(entidad, entityId) != null) return
        encolar(TipoOperacion.ACTUALIZAR, entidad, entityId, baseVersion = baseVersion)
    }
}
