package com.spiralsoft.servare.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import com.spiralsoft.servare.data.local.entity.FichaEntity
import com.spiralsoft.servare.domain.model.FichaResumen

@Dao
interface FichaDao {
    // Lee también la cola y las eliminaciones: el Flow emite cuando cambia cualquiera de las tres tablas
    @Query(
        """
        SELECT f.entityId, f.nombre, f.tipoEspacio, f.comunidad, f.fechaLevantamiento,
            EXISTS(
                SELECT 1 FROM operaciones o
                WHERE o.entityType = 'FICHA' AND o.entityId = f.entityId
                AND o.status IN ('PENDIENTE', 'PROCESANDO', 'REINTENTABLE')
            ) AS pendiente
        FROM fichas f
        WHERE NOT EXISTS (
            SELECT 1 FROM eliminaciones e
            WHERE e.entityType = 'FICHA' AND e.entityId = f.entityId
        )
        ORDER BY f.updatedAt DESC
        """
    )
    fun observarResumenes(): Flow<List<FichaResumen>>

    @Query("SELECT * FROM fichas WHERE entityId = :entityId")
    suspend fun findById(entityId: String): FichaEntity?

    // Excluye las marcadas como eliminadas
    @Query(
        """
        SELECT * FROM fichas f
        WHERE f.entityId = :entityId
        AND NOT EXISTS (
            SELECT 1 FROM eliminaciones e
            WHERE e.entityType = 'FICHA' AND e.entityId = f.entityId
        )
        """
    )
    suspend fun findActivaById(entityId: String): FichaEntity?

    @Upsert
    suspend fun upsert(ficha: FichaEntity)

    @Query("DELETE FROM fichas WHERE entityId = :entityId")
    suspend fun deleteById(entityId: String): Int
}
