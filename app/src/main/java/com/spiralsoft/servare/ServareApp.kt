package com.spiralsoft.servare

import android.app.Application
import com.spiralsoft.servare.data.connectivity.NetworkConnectivityMonitor
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class ServareApp : Application() {
    @Inject
    lateinit var connectivityMonitor: NetworkConnectivityMonitor

    override fun onCreate() {
        super.onCreate()
        connectivityMonitor.start()
    }
}
