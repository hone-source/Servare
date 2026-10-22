package com.spiralsoft.servare.domain.repository

import com.spiralsoft.servare.data.connectivity.ConnectivityMonitor
import com.spiralsoft.servare.data.connectivity.ConnectivityState
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** El ViewModel consulta el estado de red vía repositorio */
interface ConectividadRepository {
    val estado: StateFlow<ConnectivityState>
}

class DefaultConectividadRepository @Inject constructor(
    monitor: ConnectivityMonitor
) : ConectividadRepository {
    override val estado: StateFlow<ConnectivityState> = monitor.state
}
