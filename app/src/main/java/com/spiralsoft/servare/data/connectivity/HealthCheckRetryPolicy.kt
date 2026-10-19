package com.spiralsoft.servare.data.connectivity

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Espera entre health checks fallidos: min(maxDelay, baseDelay × 2^(n−1)).
 * Valores provisionales.
 * */
class HealthCheckRetryPolicy(
    val baseDelay: Duration = 2.seconds,
    val maxDelay: Duration = 60.seconds
) {
    fun nextDelay(attempt: Int): Duration {
        val factor = 1L shl (attempt - 1).coerceIn(0, 20)
        return minOf(maxDelay, baseDelay * factor.toDouble())
    }
}
