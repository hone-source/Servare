package com.spiralsoft.servare

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import com.spiralsoft.servare.data.connectivity.ConnectivityMonitor
import com.spiralsoft.servare.data.sync.SyncScheduler

@HiltAndroidApp
class ServareApp : Application(), Configuration.Provider {
    @Inject lateinit var connectivityMonitor: ConnectivityMonitor
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var syncScheduler: SyncScheduler

    // WorkManager se inicializa a demanda con la fábrica de Hilt
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        connectivityMonitor.iniciar()
        // Al iniciar la app se revisa si quedaron operaciones pendientes
        syncScheduler.requestSync()
    }
}
