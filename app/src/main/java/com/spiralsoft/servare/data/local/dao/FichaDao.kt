package com.spiralsoft.servare.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
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
        ORDER BY f.createdAt DESC
        """
    )
    fun observarResumenes(): Flow<List<FichaResumen>>

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

    // Incluye las marcadas como eliminadas, un CREAR pendiente debe enviarse aunque se haya borrado después
    @Query("SELECT * FROM fichas WHERE entityId = :entityId")
    suspend fun findById(entityId: String): FichaEntity?

    // Las que el servidor ya conoció, si dejan de venir en el pull, se borraron allí.
    // Sin versión son fichas que el servidor nunca confirmó, esas no se tocan
    @Query("SELECT * FROM fichas WHERE version IS NOT NULL")
    suspend fun confirmadasPorElServidor(): List<FichaEntity>

    // Solo la versión: no pisa una edición hecha mientras la operación estaba en vuelo
    @Query("UPDATE fichas SET version = :version WHERE entityId = :entityId")
    suspend fun actualizarVersion(entityId: String, version: Int)

    // La ficha se vuelve a crear en el servidor: la versión que conocía ya no existe
    @Query("UPDATE fichas SET version = NULL WHERE entityId = :entityId")
    suspend fun limpiarVersion(entityId: String)

    @Query("DELETE FROM fichas WHERE entityId = :entityId")
    suspend fun deleteById(entityId: String)

    @Insert
    suspend fun insert(ficha: FichaEntity)

    @Update
    suspend fun update(ficha: FichaEntity)

    // Pull: crea la ficha bajada del servidor o actualiza la que ya estaba
    @Upsert
    suspend fun upsert(ficha: FichaEntity)
}
