package com.spiralsoft.servare

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import com.spiralsoft.servare.data.connectivity.NetworkConnectivityMonitor

@HiltAndroidApp
class ServareApp : Application(), Configuration.Provider {
    @Inject
    lateinit var connectivityMonitor: NetworkConnectivityMonitor

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    // WorkManager se inicializa bajo demanda con esta configuración
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        connectivityMonitor.start()
    }
}
