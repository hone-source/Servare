package com.spiralsoft.servare.data.remote

import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds

/**
 * Cliente del servidor API.
 * Solo lo usan el repositorio, el motor de sincronización y el monitor (health check).
 */
interface RemoteDataSource {
    /** true solo si el servidor respondió que está en funcionamiento. */
    suspend fun healthCheck(): Boolean
}

@Singleton
class RetrofitRemoteDataSource @Inject constructor(
    private val api: ServareApi
) : RemoteDataSource {

    // RNF-05: el cambio de conectividad debe detectarse en 5 s
    private val healthTimeout = 5.seconds

    override suspend fun healthCheck(): Boolean =
        withTimeoutOrNull(healthTimeout) {
            try {
                val response = api.health()
                response.isSuccessful && response.body()?.status == "ok"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // sin DNS, conexión rechazada, TLS no válido, respuesta que no es del API
                false
            }
        } ?: false
}
