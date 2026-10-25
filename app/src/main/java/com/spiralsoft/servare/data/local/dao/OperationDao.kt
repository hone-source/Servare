package com.spiralsoft.servare.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.spiralsoft.servare.data.local.entity.OperationEntity
import com.spiralsoft.servare.domain.model.TipoEntidad

@Dao
interface OperationDao {
    @Insert
    suspend fun insert(operacion: OperationEntity)

    // Operación aún no enviada: un nuevo guardado de la entidad no necesita otra,
    // porque al enviarse se lee el estado actual del registro
    @Query(
        """
        SELECT * FROM operaciones
        WHERE entityType = :entityType AND entityId = :entityId
        AND status = 'PENDIENTE' AND uncertain = 0
        ORDER BY createdAt DESC
        LIMIT 1
        """
    )
    suspend fun buscarAgrupable(entityType: TipoEntidad, entityId: String): OperationEntity?
}
