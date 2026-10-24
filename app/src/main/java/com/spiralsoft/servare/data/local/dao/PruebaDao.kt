package com.spiralsoft.servare.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import com.spiralsoft.servare.data.local.entity.PruebaEntity

@Dao
interface PruebaDao {
    @Query("SELECT * FROM pruebas ORDER BY createdAt DESC")
    fun observarTodas(): Flow<List<PruebaEntity>>

    @Insert
    suspend fun insert(prueba: PruebaEntity)
}
