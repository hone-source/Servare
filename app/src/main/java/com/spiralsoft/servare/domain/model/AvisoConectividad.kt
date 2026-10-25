package com.spiralsoft.servare.domain.model

// Lo que se le muestra al usuario sobre la conectividad
data class AvisoConectividad(
    val estado: EstadoConectividad,
    val titulo: String,
    val detalle: String
)
