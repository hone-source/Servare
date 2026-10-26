package com.spiralsoft.servare.data.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import com.spiralsoft.servare.data.sync.SyncScheduler
import com.spiralsoft.servare.domain.model.EstadoConectividad
import com.spiralsoft.servare.domain.model.EstadoConectividad.CONECTADO
import com.spiralsoft.servare.domain.model.EstadoConectividad.RED_SIN_SERVIDOR
import com.spiralsoft.servare.domain.model.EstadoConectividad.SIN_RED
import kotlin.time.Duration.Companion.milliseconds

// El monitor de conectividad
// el health check confirma que el servidor es alcanzable
@Singleton
class ConnectivityMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    okHttpClient: OkHttpClient,
    baseUrl: HttpUrl,
    private val syncScheduler: SyncScheduler
) {
    private val _estado = MutableStateFlow(SIN_RED)
    val estado: StateFlow<EstadoConectividad> = _estado.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val candado = Mutex() // un solo health check a la vez
    private val iniciado = AtomicBoolean(false)
    private var sondeo: Job? = null

    // Timeout corto para detectar la pérdida del servidor en menos de 5 s
    private val cliente = okHttpClient.newBuilder()
        .connectTimeout(TIMEOUT_S, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_S, TimeUnit.SECONDS)
        .callTimeout(TIMEOUT_S, TimeUnit.SECONDS)
        .build()
    private val healthCheckUrl = checkNotNull(baseUrl.resolve(RUTA_HEALTH_CHECK))

    // Los callbacks llegan en un solo hilo del SO, redActual solo se toca ahí
    private var redActual: Network? = null
    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, capacidades: NetworkCapabilities) {
            // No se exige VALIDATED: la validación es el health check
            val red = network.takeIf {
                capacidades.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            }
            if (red == redActual) return // cambios de señal, etc.
            redActual = red
            if (red != null) redDisponible() else redPerdida()
        }

        override fun onLost(network: Network) {
            if (network != redActual) return
            redActual = null
            redPerdida()
        }
    }

    fun iniciar() {
        if (!iniciado.compareAndSet(false, true)) return
        context.getSystemService(ConnectivityManager::class.java)
            .registerDefaultNetworkCallback(callback)
    }

    // Lo usa el motor antes de cada ciclo de sincronización
    suspend fun confirmarAlcanzabilidad(): Boolean {
        if (_estado.value == SIN_RED) return false
        val responde = healthCheck()
        if (responde) entrarConectado() else reportarServidorNoAlcanzable()
        return responde
    }

    // Para peticiones que fallan por timeout o sin respuesta estando CONECTADO
    fun reportarServidorNoAlcanzable() {
        if (cambiar(RED_SIN_SERVIDOR, soloDesde = CONECTADO)) lanzarSondeo()
    }

    private fun redDisponible() {
        // Una red nueva obliga a confirmar de nuevo, aunque estuviéramos CONECTADO
        cambiar(RED_SIN_SERVIDOR)
        lanzarSondeo()
    }

    private fun redPerdida() {
        cancelarSondeo()
        cambiar(SIN_RED)
    }

    // Health check periódico y espaciado mientras estemos en RED_SIN_SERVIDOR
    private fun lanzarSondeo() = synchronized(this) {
        sondeo?.cancel()
        sondeo = scope.launch {
            var intento = 1
            while (!healthCheck()) {
                Log.d(TAG, "Health check falló (intento $intento)")
                delay(espera(intento++).milliseconds)
            }
            entrarConectado()
        }
    }

    private fun cancelarSondeo() = synchronized(this) {
        sondeo?.cancel()
        sondeo = null
    }

    private fun entrarConectado() {
        // soloDesde evita pisar un SIN_RED que llegó mientras corría el health check
        if (cambiar(CONECTADO, soloDesde = RED_SIN_SERVIDOR)) {
            cancelarSondeo()
            syncScheduler.requestSync() // recuperación de conexión
        }
    }

    // Devuelve true solo si hubo transición
    private fun cambiar(nuevo: EstadoConectividad, soloDesde: EstadoConectividad? = null): Boolean {
        while (true) {
            val actual = _estado.value
            if (actual == nuevo || (soloDesde != null && actual != soloDesde)) return false
            if (_estado.compareAndSet(actual, nuevo)) {
                Log.d(TAG, "$actual → $nuevo")
                return true
            }
        }
    }

    private suspend fun healthCheck(): Boolean = candado.withLock {
        suspendCancellableCoroutine { cont ->
            val call = cliente.newCall(Request.Builder().url(healthCheckUrl).build())
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) = cont.resume(false)

                override fun onResponse(call: Call, response: Response) =
                    response.use { cont.resume(it.isSuccessful) }
            })
        }
    }

    private companion object {
        const val TAG = "ConnectivityMonitor"
        const val RUTA_HEALTH_CHECK = "api/healthcheck"
        const val TIMEOUT_S = 3L
        const val ESPERA_BASE_MS = 5_000L
        const val ESPERA_MAX_MS = 60_000L

        // min(60 s, 5 s × 2^(n−1))
        fun espera(intento: Int): Long =
            (ESPERA_BASE_MS shl (intento - 1).coerceAtMost(10)).coerceAtMost(ESPERA_MAX_MS)
    }
}
