package com.spiralsoft.servare.data.sync

// Reintentos de la estructura cola.
// Solo cuentan los fallos temporales con el servidor alcanzable
internal object PoliticaReintentos {
    // Con 13 el tope de 5 h interviene (ventana total alrededor de 10.7 h)
    const val MAX_INTENTOS = 13
    private const val ESPERA_BASE_MS = 10_000L
    private const val ESPERA_MAX_MS = 5 * 60 * 60 * 1000L

    // Espera tras el intento n (n ≥ 1): min(5 h, 10 s × 2^(n−1))
    fun espera(intento: Int): Long =
        (ESPERA_BASE_MS shl (intento - 1).coerceAtMost(20)).coerceAtMost(ESPERA_MAX_MS)
}
