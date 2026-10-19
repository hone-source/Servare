package com.spiralsoft.servare.data.connectivity

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Callback
import java.io.IOException
import javax.inject.Inject
import javax.inject.Named
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

class OkHttpHealthChecker @Inject constructor(
    @Named("baseUrl") private val baseUrl: String
) : ServerHealthChecker {

    // TODO(RNF-05): valor pendiente; define el límite de detección (5 s)
    private val timeout: Duration = 3.seconds

    // Cliente propio, sin AuthInterceptor: el health check no debe depender de la sesión
    private val client = OkHttpClient.Builder()
        .callTimeout(timeout.toJavaDuration())
        .build()

    override suspend fun check(): Boolean =
        try {
            client.newCall(Request.Builder().url(baseUrl + "health").build())
                .await().use { it.isSuccessful }
        } catch (e: IOException) {
            false   // sin DNS, portal cautivo, timeout, conexión rechazada…
        }
}

private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (cont.isActive) cont.resumeWithException(e)
        }
        override fun onResponse(call: Call, response: Response) = cont.resume(response)
    })
}
