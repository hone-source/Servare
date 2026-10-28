package com.spiralsoft.servare.domain.model

// Un campo del registro con su valor en el dispositivo y en el servidor
data class CampoComparado(
    val nombre: String,
    val local: String?,
    val servidor: String?
)

// Lo que se le dice al usuario de un cambio que no pudo sincronizarse. El cambio sigue guardado en el dispositivo
data class AvisoOperacion(
    val operationId: String,
    val estado: EstadoOperacion, // FALLIDA, CONFLICTO, o BLOQUEADA si su origen es de otro registro
    val titulo: String,
    val detalle: String,
    val acciones: List<AccionOperacion> = emptyList(),
    // Acciones que cambian o pierden datos: se confirman con este texto, que dice qué pasará
    val confirmaciones: Map<AccionOperacion, String> = emptyMap(),
    // Ambas versiones, si el servidor entregó la suya
    val comparacion: List<CampoComparado>? = null
)
