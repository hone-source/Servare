package com.spiralsoft.servare.domain.model

// Lo que se le dice al usuario de un cambio que no pudo sincronizarse. El cambio sigue guardado en el dispositivo
data class AvisoOperacion(
    val operationId: String,
    val estado: EstadoOperacion, // FALLIDA, CONFLICTO, o BLOQUEADA si su origen es de otro registro
    val titulo: String,
    val detalle: String
)
