package com.spiralsoft.servare.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import com.spiralsoft.servare.data.local.entity.PuntoEntity

@Dao
interface PuntoDao {
    // Lee también las eliminaciones: el Flow emite cuando se marca un punto como eliminado
    @Query(
        """
        SELECT * FROM punto p
        WHERE NOT EXISTS (
            SELECT 1 FROM eliminaciones e
            WHERE e.entityType = 'PUNTO' AND e.entityId = p.entityId
        )
        ORDER BY p.createdAt DESC
        """
    )
    fun observarTodos(): Flow<List<PuntoEntity>>

    // Excluye los marcados como eliminados
    @Query(
        """
        SELECT * FROM punto p
        WHERE p.entityId = :entityId
        AND NOT EXISTS (
            SELECT 1 FROM eliminaciones e
            WHERE e.entityType = 'PUNTO' AND e.entityId = p.entityId
        )
        """
    )
    suspend fun findActivaById(entityId: String): PuntoEntity?

    // Incluye los marcados como eliminados, un CREAR pendiente debe enviarse aunque se haya borrado después
    @Query("SELECT * FROM punto WHERE entityId = :entityId")
    suspend fun findById(entityId: String): PuntoEntity?

    // Los que el servidor ya conoció, si dejan de venir en el pull, se borraron allí.
    // Sin versión son registros que el servidor nunca confirmó, esos no se tocan
    @Query("SELECT * FROM punto WHERE version IS NOT NULL")
    suspend fun confirmadosPorElServidor(): List<PuntoEntity>

    // Solo la versión: no pisa una edición hecha mientras la operación estaba en vuelo
    @Query("UPDATE punto SET version = :version WHERE entityId = :entityId")
    suspend fun actualizarVersion(entityId: String, version: Int)

    // El registro se vuelve a crear en el servidor: la versión que conocía ya no existe
    @Query("UPDATE punto SET version = NULL WHERE entityId = :entityId")
    suspend fun limpiarVersion(entityId: String)

    @Query("DELETE FROM punto WHERE entityId = :entityId")
    suspend fun deleteById(entityId: String)

    @Insert
    suspend fun insert(punto: PuntoEntity)

    @Update
    suspend fun update(punto: PuntoEntity)

    // Pull: crea el registro bajado del servidor o actualiza el que ya estaba
    @Upsert
    suspend fun upsert(punto: PuntoEntity)
}
