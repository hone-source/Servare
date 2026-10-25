package com.spiralsoft.servare.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import com.spiralsoft.servare.data.local.AppDatabase

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides fun pruebaDao(db: AppDatabase) = db.pruebaDao() //Para pruebas

    @Provides @Singleton
    fun database(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(
            ctx,
            AppDatabase::class.java,
            "servare.db"
        ).build()

    @Provides fun operationDao(db: AppDatabase) = db.operationDao()

    @Provides fun eliminacionDao(db: AppDatabase) = db.eliminacionDao()

    //Dao para las operaciones propias del sistema

}
