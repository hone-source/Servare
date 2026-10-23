package com.spiralsoft.servare.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import com.spiralsoft.servare.data.local.entity.FichaEntity
import com.spiralsoft.servare.domain.model.FichaResumen

@Dao
interface FichaDao {
    // Lee también la cola: el Flow emite cuando cambia cualquiera de las dos tablas
    @Query(
        """
        SELECT f.entityId, f.nombre, f.tipoEspacio, f.comunidad, f.fechaLevantamiento,
            EXISTS(
                SELECT 1 FROM operaciones o
                WHERE o.entityId = f.entityId
                AND o.status IN ('PENDIENTE', 'PROCESANDO', 'REINTENTABLE')
            ) AS pendiente
        FROM fichas f
        ORDER BY f.updatedAt DESC
        """
    )
    fun observarResumenes(): Flow<List<FichaResumen>>

    @Query("SELECT * FROM fichas WHERE entityId = :entityId")
    suspend fun findById(entityId: String): FichaEntity?

    @Upsert
    suspend fun upsert(ficha: FichaEntity)

    @Query("DELETE FROM fichas WHERE entityId = :entityId")
    suspend fun deleteById(entityId: String): Int
}
