package com.spiralsoft.servare.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.spiralsoft.servare.notification.DefaultStatusNotifier
import com.spiralsoft.servare.notification.StatusNotifier

@Module
@InstallIn(SingletonComponent::class)
abstract class NotificationModule {
    @Binds abstract fun statusNotifier(impl: DefaultStatusNotifier): StatusNotifier
}
