package com.spiralsoft.servare.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import com.spiralsoft.servare.data.local.entity.PruebaEntity

@Dao
interface PruebaDao {
    // Lee también las eliminaciones: el Flow emite cuando se marca una prueba como eliminada
    @Query(
        """
        SELECT * FROM pruebas p
        WHERE NOT EXISTS (
            SELECT 1 FROM eliminaciones e
            WHERE e.entityType = 'PRUEBA' AND e.entityId = p.entityId
        )
        ORDER BY p.createdAt DESC
        """
    )
    fun observarTodas(): Flow<List<PruebaEntity>>

    // Excluye las marcadas como eliminadas
    @Query(
        """
        SELECT * FROM pruebas p
        WHERE p.entityId = :entityId
        AND NOT EXISTS (
            SELECT 1 FROM eliminaciones e
            WHERE e.entityType = 'PRUEBA' AND e.entityId = p.entityId
        )
        """
    )
    suspend fun findActivaById(entityId: String): PruebaEntity?

    // Incluye las marcadas como eliminadas, un CREAR pendiente debe enviarse aunque se haya borrado después
    @Query("SELECT * FROM pruebas WHERE entityId = :entityId")
    suspend fun findById(entityId: String): PruebaEntity?

    // Solo la versión: no pisa una edición hecha mientras la operación estaba en vuelo
    @Query("UPDATE pruebas SET version = :version WHERE entityId = :entityId")
    suspend fun actualizarVersion(entityId: String, version: Int)

    @Query("DELETE FROM pruebas WHERE entityId = :entityId")
    suspend fun deleteById(entityId: String)

    @Insert
    suspend fun insert(prueba: PruebaEntity)

    @Update
    suspend fun update(prueba: PruebaEntity)
}
