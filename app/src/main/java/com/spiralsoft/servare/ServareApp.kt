package com.spiralsoft.servare

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import com.spiralsoft.servare.data.connectivity.ConnectivityMonitor

@HiltAndroidApp
class ServareApp : Application() {
    @Inject lateinit var connectivityMonitor: ConnectivityMonitor

    override fun onCreate() {
        super.onCreate()
        connectivityMonitor.iniciar()
    }
}
