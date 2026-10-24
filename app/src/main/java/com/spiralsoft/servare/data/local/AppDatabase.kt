package com.spiralsoft.servare.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.spiralsoft.servare.data.local.dao.FichaDao
import com.spiralsoft.servare.data.local.dao.OperationDao
import com.spiralsoft.servare.data.local.dao.PruebaDao
import com.spiralsoft.servare.data.local.entity.FichaEntity
import com.spiralsoft.servare.data.local.entity.OperationEntity
import com.spiralsoft.servare.data.local.entity.PruebaEntity

@Database(
    entities = [
        PruebaEntity::class, //Operaciones de prueba
        FichaEntity::class,
        OperationEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun pruebaDao(): PruebaDao // Operaciones de prueba Dao
    abstract fun fichaDao(): FichaDao
    abstract fun operationDao(): OperationDao
}
