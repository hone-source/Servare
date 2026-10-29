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

// El servidor devuelve el registro con su versión nueva
@Serializable
data class PruebaResponse(val version: Int? = null)

// Cuerpo del 409: el registro como está en el servidor
@Serializable
data class ConflictoRemoto(val actual: PruebaRemota? = null)

// Registro tal como lo devuelve el pull
@Serializable
data class PruebaRemota(
    val entityId: String,
    val titulo: String,
    val texto: String,
    val numero: Int,
    val createdAt: Long,
    val version: Int? = null
)
