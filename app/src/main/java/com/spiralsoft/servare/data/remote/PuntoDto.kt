package com.spiralsoft.servare.data.remote

import kotlinx.serialization.Serializable

// Contenido del punto tal como se envía, se lee de la tabla en el momento del envío
@Serializable
data class PuntoRequest(
    val entityId: String,
    val nombre: String,
    val latitud: Double,
    val longitud: Double,
    val createdAt: Long,
    val baseVersion: Int? = null            // vacía en CREAR
)

// Registro tal como lo devuelve el pull y el campo "actual" del 409
@Serializable
data class PuntoRemoto(
    val entityId: String,
    val nombre: String,
    val latitud: Double,
    val longitud: Double,
    val createdAt: Long,
    val version: Int? = null
)
