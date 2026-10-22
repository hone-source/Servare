package com.spiralsoft.servare.data.connectivity

import kotlinx.coroutines.flow.StateFlow

/**
 * Estados de conectividad del monitor.
 * Solo [CONECTADO] habilita la sincronización y los recursos remotos.
 */
enum class ConnectivityState {
    /** El SO no reporta ninguna red. Estado inicial. */
    SIN_RED,

    /** Hay red, pero el servidor no respondió el health check (aún o por timeout) */
    RED_SIN_SERVIDOR,

    /** Hay red y el health check respondió: el servidor es alcanzable. */
    CONECTADO
}

/**
 * Único punto de la app que conoce el estado de red; la UI lo lee vía
 * ConectividadRepository y nunca consulta la red por su cuenta.
 */

interface ConnectivityMonitor {
    /** Estado actual y sus cambios. */
    val state: StateFlow<ConnectivityState>

    /** `true` solo en [ConnectivityState.CONECTADO]. */
    fun isConnected(): Boolean
}
