package com.spiralsoft.servare.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import com.spiralsoft.servare.data.local.entity.PuntoEntity

@Dao
interface PuntoDao {
    @Query("SELECT * FROM punto ORDER BY createdAt DESC")
    fun observarTodos(): Flow<List<PuntoEntity>>

    @Query("SELECT * FROM punto WHERE entityId = :entityId")
    suspend fun findById(entityId: String): PuntoEntity?

    @Query("DELETE FROM punto WHERE entityId = :entityId")
    suspend fun deleteById(entityId: String)

    @Insert
    suspend fun insert(punto: PuntoEntity)

    @Update
    suspend fun update(punto: PuntoEntity)
}
