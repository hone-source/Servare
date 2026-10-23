package com.spiralsoft.servare.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import com.spiralsoft.servare.data.local.entity.OperationEntity

@Dao
interface OperationDao {
    @Insert
    suspend fun insert(operacion: OperationEntity)
}
