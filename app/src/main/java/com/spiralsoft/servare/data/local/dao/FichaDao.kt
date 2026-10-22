package com.spiralsoft.servare.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.spiralsoft.servare.data.local.entity.FichaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FichaDao {
    @Query("SELECT * FROM fichas ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<FichaEntity>>

    @Query("SELECT * FROM fichas WHERE entityId = :entityId")
    suspend fun findById(entityId: String): FichaEntity?

    @Query("SELECT entityId FROM fichas")
    suspend fun findAllIds(): List<String>

    @Upsert
    suspend fun upsert(ficha: FichaEntity)

    /** Sin efecto si la ficha ya no existe (p. ej. tras un DELETE). */
    @Query("UPDATE fichas SET version = :version WHERE entityId = :entityId")
    suspend fun updateVersion(entityId: String, version: Int)

    /** Devuelve cuántas filas borró (0 = ya no existía). */
    @Query("DELETE FROM fichas WHERE entityId = :entityId")
    suspend fun deleteById(entityId: String): Int
}
