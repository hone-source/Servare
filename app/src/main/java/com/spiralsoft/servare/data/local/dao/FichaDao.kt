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

    @Upsert
    suspend fun upsert(ficha: FichaEntity)

    /** Devuelve cuántas filas borró (0 = ya no existía). */
    @Query("DELETE FROM fichas WHERE entityId = :entityId")
    suspend fun deleteById(entityId: String): Int
}
