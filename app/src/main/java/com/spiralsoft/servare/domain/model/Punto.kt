package com.spiralsoft.servare.domain.model

// Punto del mapa guardado por el usuario
data class Punto(
    val entityId: String,
    val nombre: String,
    val latitud: Double,
    val longitud: Double,
    val createdAt: Long
)
