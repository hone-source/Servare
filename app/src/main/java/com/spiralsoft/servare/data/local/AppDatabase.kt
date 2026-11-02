package com.spiralsoft.servare.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.spiralsoft.servare.data.local.dao.EliminacionDao
import com.spiralsoft.servare.data.local.dao.FichaDao
import com.spiralsoft.servare.data.local.dao.OperationDao
import com.spiralsoft.servare.data.local.dao.PruebaDao
import com.spiralsoft.servare.data.local.dao.SyncInicialDao
import com.spiralsoft.servare.data.local.entity.EliminacionEntity
import com.spiralsoft.servare.data.local.entity.FichaEntity
import com.spiralsoft.servare.data.local.entity.OperationEntity
import com.spiralsoft.servare.data.local.entity.PruebaEntity
import com.spiralsoft.servare.data.local.entity.SyncInicialEntity

@Database(
    entities = [
        // Entities para las operaciones offline
        PruebaEntity::class, //Entity de prueba
        OperationEntity::class,
        EliminacionEntity::class,
        SyncInicialEntity::class,

        // Entities de las operaciones propias del sistema
        FichaEntity::class,
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    // Dao para las operaciones offline
    abstract fun pruebaDao(): PruebaDao // Dao para las operaciones de prueba
    abstract fun operationDao(): OperationDao
    abstract fun eliminacionDao(): EliminacionDao
    abstract fun syncInicialDao(): SyncInicialDao

    //Dao para las operaciones propias del sistema
    abstract fun fichaDao(): FichaDao

}
