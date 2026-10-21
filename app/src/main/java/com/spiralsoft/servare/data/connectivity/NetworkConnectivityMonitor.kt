package com.spiralsoft.servare.data.connectivity

import android.net.ConnectivityManager
import android.net.Network
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import com.spiralsoft.servare.data.remote.RemoteDataSource
import com.spiralsoft.servare.data.sync.SyncScheduler
import com.spiralsoft.servare.di.ApplicationScope

@Singleton
class NetworkConnectivityMonitor @Inject constructor(
    private val connectivityManager: ConnectivityManager,
    private val remote: RemoteDataSource,
    @ApplicationScope private val scope: CoroutineScope,
    private val retryPolicy: HealthCheckRetryPolicy,
    private val scheduler: SyncScheduler
) : ConnectivityMonitor {

    private val _state = MutableStateFlow(ConnectivityState.SIN_RED)
    override val state: StateFlow<ConnectivityState> = _state.asStateFlow()

    private val lock = Any()
    private var recheckJob: Job? = null
    @Volatile private var currentNetwork: Network? = null
    private var callback: ConnectivityManager.NetworkCallback? = null

    override fun isConnected() = _state.value == ConnectivityState.CONECTADO

    fun start() {
        if (callback != null) return
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) =
                this@NetworkConnectivityMonitor.onAvailable(network)
            override fun onLost(network: Network) =
                this@NetworkConnectivityMonitor.onLost(network)
        }
        callback = cb
        connectivityManager.registerDefaultNetworkCallback(cb)
    }

    // internal (D2 las declara private): así se pueden invocar desde pruebas unitarias
    internal fun onAvailable(network: Network) {
        currentNetwork = network
        // El SO dice "hay red", no "hay servidor": hay que confirmarlo (también si cambió Wi-Fi ↔ datos)
        _state.value = ConnectivityState.RED_SIN_SERVIDOR
        startRecheck()
    }

    internal fun onLost(network: Network) {
        // Al cambiar de red el SO puede reportar onLost de la anterior DESPUÉS de onAvailable de la nueva
        if (network != currentNetwork) return
        currentNetwork = null
        cancelRecheck()
        _state.value = ConnectivityState.SIN_RED
    }

    /** Lo llamará OperationProcessor / un interceptor cuando una petición expire sin respuesta. */
    fun onRequestFailedByTimeout() {
        if (_state.compareAndSet(ConnectivityState.CONECTADO, ConnectivityState.RED_SIN_SERVIDOR)) {
            startRecheck()
        }
    }

    private fun startRecheck() = synchronized(lock) {
        recheckJob?.cancel()
        recheckJob = scope.launch { recheckWithBackoff() }
    }

    private fun cancelRecheck() = synchronized(lock) {
        recheckJob?.cancel()
        recheckJob = null
    }

    private suspend fun recheckWithBackoff() {
        var attempt = 0
        while (currentCoroutineContext().isActive) {
            if (remote.healthCheck()) {
                enterConnected()
                return
            }
            attempt++
            delay(retryPolicy.nextDelay(attempt))
        }
    }

    private fun enterConnected() {
        if (currentNetwork == null) return          // la red se perdió mientras se verificaba
        val previous = _state.getAndUpdate { ConnectivityState.CONECTADO }
        // Solo ENTRAR a CONECTADO es "recuperación de conexión" y dispara la sincronización
        if (previous != ConnectivityState.CONECTADO) scheduler.requestSync()
    }
}
