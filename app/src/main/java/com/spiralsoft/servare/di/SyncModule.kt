package com.spiralsoft.servare.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.spiralsoft.servare.data.queue.OperationRetryPolicy
import com.spiralsoft.servare.data.sync.SyncScheduler
import com.spiralsoft.servare.data.sync.WorkManagerSyncScheduler

@Module
@InstallIn(SingletonComponent::class)
abstract class SyncBindsModule {
    @Binds abstract fun scheduler(impl: WorkManagerSyncScheduler): SyncScheduler
}

@Module
@InstallIn(SingletonComponent::class)
object SyncProvidesModule {
    @Provides fun operationRetryPolicy() = OperationRetryPolicy()
}
