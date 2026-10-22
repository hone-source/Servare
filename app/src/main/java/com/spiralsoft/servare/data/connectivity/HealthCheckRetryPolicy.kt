package com.spiralsoft.servare.data.connectivity

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Espera entre health checks fallidos: min(maxDelay, baseDelay × 2^(n−1)).
 * Valores provisionales.
 *
 * Es independiente del backoff de las operaciones de la cola (10 s, tope 5 h):
 * aquí no hay límite de intentos, se reintenta mientras siga habiendo red.
 * Con los valores actuales: 2 s, 4 s, 8 s, 16 s, 32 s y luego 60 s fijos.
 * */
class HealthCheckRetryPolicy(
    val baseDelay: Duration = 5.seconds,
    val maxDelay: Duration = 60.seconds
) {
    /**
     * Espera antes del siguiente health check tras el fallo número [attempt].
     */
    fun nextDelay(attempt: Int): Duration {
        val factor = 1L shl (attempt - 1).coerceIn(0, 10)
        return minOf(maxDelay, baseDelay * factor.toDouble())
    }
}
