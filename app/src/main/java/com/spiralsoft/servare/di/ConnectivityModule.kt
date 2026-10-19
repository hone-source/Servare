package com.spiralsoft.servare.di


import android.content.Context
import android.net.ConnectivityManager
import com.spiralsoft.servare.data.connectivity.*
import com.spiralsoft.servare.data.sync.*
import dagger.*
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.*
import javax.inject.*

@Qualifier @Retention(AnnotationRetention.RUNTIME)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
abstract class ConnectivityBindsModule {
    @Binds abstract fun monitor(impl: NetworkConnectivityMonitor): ConnectivityMonitor
    @Binds abstract fun healthChecker(impl: OkHttpHealthChecker): ServerHealthChecker
    @Binds abstract fun scheduler(impl: PlaceholderSyncScheduler): SyncScheduler
}

@Module
@InstallIn(SingletonComponent::class)
object ConnectivityProvidesModule {
    @Provides @Singleton @ApplicationScope
    fun appScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides fun connectivityManager(@ApplicationContext ctx: Context): ConnectivityManager =
        ctx.getSystemService(ConnectivityManager::class.java)

    @Provides fun retryPolicy() = HealthCheckRetryPolicy()

    // 10.0.2.2 = el localhost desde la PC visto desde el emulador. TODO: pasar a BuildConfig
    @Provides @Named("baseUrl") fun baseUrl(): String = "http://10.0.2.2:8080/"
}
