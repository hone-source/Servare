package com.spiralsoft.servare.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import com.spiralsoft.servare.data.local.entity.EliminacionEntity

@Dao
interface EliminacionDao {
    @Insert
    suspend fun insert(eliminacion: EliminacionEntity)
}
