package com.spiralsoft.servare.data.remote

import kotlinx.serialization.Serializable

// Contenido de la prueba tal como se envía, se lee de la tabla en el momento del envío
@Serializable
data class PruebaRequest(
    val entityId: String,
    val titulo: String,
    val texto: String,
    val numero: Int,
    val createdAt: Long,
    val baseVersion: Int? = null            // vacía en CREAR
)

// Registro tal como lo devuelve el pull y el campo "actual" del 409
@Serializable
data class PruebaRemota(
    val entityId: String,
    val titulo: String,
    val texto: String,
    val numero: Int,
    val createdAt: Long,
    val version: Int? = null
)
