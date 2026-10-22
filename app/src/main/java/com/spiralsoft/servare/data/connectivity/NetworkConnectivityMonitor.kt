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

/**
 * Monitor de conectividad.
 *
 * Combina dos fuentes: `ConnectivityManager.NetworkCallback` (el SO dice si hay red)
 * y el health check de [RemoteDataSource] (confirma que el servidor responde).
 * Transiciones:
 * - SIN_RED → RED_SIN_SERVIDOR: el SO reporta red disponible.
 * - RED_SIN_SERVIDOR → CONECTADO: el health check responde.
 * - RED_SIN_SERVIDOR → RED_SIN_SERVIDOR: el health check falla; se reintenta según [HealthCheckRetryPolicy].
 * - CONECTADO → RED_SIN_SERVIDOR: una petición expira sin respuesta ([onRequestFailedByTimeout]).
 * - cualquiera → SIN_RED: el SO reporta pérdida de la red activa.
 *
 * Entrar en CONECTADO es la "recuperación de conexión" y dispara [SyncScheduler.requestSync].
 * Se arranca una sola vez con [start] desde ServareApp.
 */
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

    /** Protege [recheckJob]: los callbacks del SO y los timeouts llegan desde hilos distintos. */
    private val lock = Any()
    /** Bucle de health check en curso; como máximo uno a la vez. */
    private var recheckJob: Job? = null
    /** Red por defecto actual según el SO; `null` si no hay ninguna. */
    @Volatile private var currentNetwork: Network? = null
    private var callback: ConnectivityManager.NetworkCallback? = null

    override fun isConnected() = _state.value == ConnectivityState.CONECTADO

    /** Registra el callback de la red por defecto. Llamadas repetidas no hacen nada. */
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

    private fun onAvailable(network: Network) {
        currentNetwork = network
        // El SO dice "hay red", no "hay servidor": hay que confirmarlo (también si cambió Wi-Fi ↔ datos)
        _state.value = ConnectivityState.RED_SIN_SERVIDOR
        startRecheck()
    }

    private fun onLost(network: Network) {
        // Al cambiar de red el SO puede reportar onLost de la anterior DESPUÉS de onAvailable de la nueva
        if (network != currentNetwork) return
        currentNetwork = null
        cancelRecheck()
        _state.value = ConnectivityState.SIN_RED
    }

    /**
     * Lo llamará OperationProcessor / un interceptor cuando una petición expire sin respuesta.
     * Solo actúa desde CONECTADO: baja a RED_SIN_SERVIDOR y reanuda los health checks.
     */
    fun onRequestFailedByTimeout() {
        if (_state.compareAndSet(ConnectivityState.CONECTADO, ConnectivityState.RED_SIN_SERVIDOR)) {
            startRecheck()
        }
    }

    /** Reinicia el bucle de health check, cancelando el anterior
     * (el contador de intentos vuelve a 0). */
    private fun startRecheck() = synchronized(lock) {
        recheckJob?.cancel()
        recheckJob = scope.launch { recheckWithBackoff() }
    }

    private fun cancelRecheck() = synchronized(lock) {
        recheckJob?.cancel()
        recheckJob = null
    }

    /** Health check hasta que responda o se cancele el job
     * No tiene límite de intentos. */
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
        if (currentNetwork == null) return // la red se perdió mientras se verificaba
        val previous = _state.getAndUpdate { ConnectivityState.CONECTADO }
        // Solo ENTRAR a CONECTADO es "recuperación de conexión" y dispara la sincronización
        if (previous != ConnectivityState.CONECTADO) scheduler.requestSync()
    }

}
