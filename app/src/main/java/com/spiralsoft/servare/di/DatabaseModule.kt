package com.spiralsoft.servare.di

import android.content.Context
import androidx.room.Room
import com.spiralsoft.servare.data.local.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides @Singleton
    fun database(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, "servare.db").build()

    @Provides fun siteDao(db: AppDatabase) = db.siteDao()
    @Provides fun operationDao(db: AppDatabase) = db.operationDao()
    @Provides fun clock(): java.time.Clock = java.time.Clock.systemUTC()
}
