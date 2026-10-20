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
import java.time.Clock
import java.time.Clock.systemUTC

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides @Singleton
    fun database(@ApplicationContext ctx: Context):
            AppDatabase = Room.databaseBuilder(
        ctx,
        AppDatabase::class.java,
        "servare.db").build()

    @Provides fun operationDao(db: AppDatabase) = db.operationDao()
    @Provides fun fichaDao(db: AppDatabase) = db.fichaDao()
    @Provides fun clock(): Clock = systemUTC()

}
