package com.spiralsoft.servare.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.spiralsoft.servare.data.local.entity.EliminacionEntity
import com.spiralsoft.servare.domain.model.TipoEntidad

@Dao
interface EliminacionDao {
    @Insert
    suspend fun insert(eliminacion: EliminacionEntity)

    // Borrados que el servidor aún no confirma, el pull no debe resucitarlos
    @Query("SELECT entityId FROM eliminaciones WHERE entityType = :entityType")
    suspend fun entidadesEliminadas(entityType: TipoEntidad): List<String>

    @Query("DELETE FROM eliminaciones WHERE entityType = :entityType AND entityId = :entityId")
    suspend fun delete(entityType: TipoEntidad, entityId: String)
}
