package com.spiralsoft.servare.data.queue

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

/**
 * Backoff de las operaciones de la cola.
 * Valores (10 s, tope 5 h, 5 intentos).
 *
 * Solo cuentan los intentos con el servidor alcanzable (timeout, 5xx);
 * perder la conexión no suma intento.
 * Con los valores actuales: 10 s, 20 s, 40 s, 80 s y al quinto fallo pasa a FALLIDA.
 */
class OperationRetryPolicy(
    val baseDelay: Duration = 10.seconds,
    val maxDelay: Duration = 5.hours,
    val maxAttempts: Int = 5
) {

    /** Espera antes del siguiente intento tras el fallo número [attempt]. */
    fun nextDelay(attempt: Int): Duration {
        val factor = 1L shl (attempt - 1).coerceIn(0, 20)
        return minOf(maxDelay, baseDelay * factor.toDouble())
    }

    fun isExhausted(attempt: Int) = attempt >= maxAttempts
}
