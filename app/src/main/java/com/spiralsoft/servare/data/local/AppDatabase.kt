package com.spiralsoft.servare.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.spiralsoft.servare.data.local.entity.FichaEntity
import com.spiralsoft.servare.data.local.entity.OperationEntity
import com.spiralsoft.servare.data.local.dao.FichaDao
import com.spiralsoft.servare.data.local.dao.OperationDao

@Database(
    entities = [
        OperationEntity::class,
        FichaEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun operationDao(): OperationDao
    abstract fun fichaDao(): FichaDao
}
