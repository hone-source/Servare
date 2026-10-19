package com.spiralsoft.servare.data.connectivity

import kotlinx.coroutines.flow.StateFlow

enum class ConnectivityState { SIN_RED, RED_SIN_SERVIDOR, CONECTADO }

interface ConnectivityMonitor {
    val state: StateFlow<ConnectivityState>
    fun isConnected(): Boolean
}

interface ServerHealthChecker {
    suspend fun check(): Boolean
}
