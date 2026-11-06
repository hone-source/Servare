package com.spiralsoft.servare.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import com.spiralsoft.servare.data.local.entity.PuntoEntity

@Dao
interface PuntoDao {
    @Query("SELECT * FROM punto ORDER BY createdAt DESC")
    fun observarTodos(): Flow<List<PuntoEntity>>

    @Insert
    suspend fun insert(punto: PuntoEntity)
}
