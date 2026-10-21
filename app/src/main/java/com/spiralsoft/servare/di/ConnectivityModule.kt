package com.spiralsoft.servare.di

import android.content.Context
import android.net.ConnectivityManager
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.Binds
import dagger.Module
import dagger.Provides
import javax.inject.Named
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import com.spiralsoft.servare.data.connectivity.ConnectivityMonitor
import com.spiralsoft.servare.data.connectivity.HealthCheckRetryPolicy
import com.spiralsoft.servare.data.connectivity.NetworkConnectivityMonitor
import com.spiralsoft.servare.data.connectivity.OkHttpHealthChecker
import com.spiralsoft.servare.data.connectivity.ServerHealthChecker
import com.spiralsoft.servare.data.sync.PlaceholderSyncScheduler
import com.spiralsoft.servare.data.sync.SyncScheduler

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
abstract class ConnectivityBindsModule {
    @Binds
    abstract fun monitor(impl: NetworkConnectivityMonitor): ConnectivityMonitor
    @Binds abstract fun healthChecker(impl: OkHttpHealthChecker): ServerHealthChecker
    @Binds abstract fun scheduler(impl: PlaceholderSyncScheduler): SyncScheduler
}

@Module
@InstallIn(SingletonComponent::class)
object ConnectivityProvidesModule {
    @Provides
    @Singleton
    @ApplicationScope
    fun appScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides fun connectivityManager(@ApplicationContext ctx: Context): ConnectivityManager =
        ctx.getSystemService(ConnectivityManager::class.java)

    @Provides fun retryPolicy() = HealthCheckRetryPolicy()

    // 10.0.2.2 = el localhost desde la PC visto desde el emulador.
    // TODO: pasar a un archivo aparte
    @Provides @Named("baseUrl") fun baseUrl(): String = "https://10.0.2.2:8000/api/v1/"
}
