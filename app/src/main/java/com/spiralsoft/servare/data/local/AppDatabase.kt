package com.spiralsoft.servare.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.spiralsoft.servare.data.local.entity.SacredSiteEntity
import com.spiralsoft.servare.data.local.entity.OperationEntity
import com.spiralsoft.servare.data.local.dao.OperationDao
import com.spiralsoft.servare.data.local.dao.SacredSiteDao

@Database(
    entities = [SacredSiteEntity::class, OperationEntity::class],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun siteDao(): SacredSiteDao
    abstract fun operationDao(): OperationDao
}
