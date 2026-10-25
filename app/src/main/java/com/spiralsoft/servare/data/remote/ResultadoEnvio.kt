package com.spiralsoft.servare.data.remote

// Resultado de enviar una operación, el motor lo traduce al estado de la operación
sealed interface ResultadoEnvio {
    data class Confirmada(val version: Int?) : ResultadoEnvio
    // Sin respuesta o conexión perdida: el servidor pudo haberla aplicado. No cuenta como intento
    data object Incierto : ResultadoEnvio
    // Timeout o 5xx: cuenta como intento. sinRespuesta (timeout) indica que el servidor dejó de ser alcanzable
    data class Temporal(val motivo: String, val sinRespuesta: Boolean = false) : ResultadoEnvio
    // Versión distinta, 404 o 410
    data class Conflicto(val motivo: String) : ResultadoEnvio
    // 422, 413 o regla de negocio
    data class Definitivo(val motivo: String) : ResultadoEnvio
    // 401/403: pausa toda la cola, las operaciones siguen PENDIENTE
    data object SesionExpirada : ResultadoEnvio
}
