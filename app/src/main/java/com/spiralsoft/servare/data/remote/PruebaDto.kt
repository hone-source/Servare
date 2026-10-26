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

// El servidor devuelve el registro. La versión la usará la detección de conflictos
// cuando el servidor la implemente; hasta entonces llega vacía
@Serializable
data class PruebaResponse(val version: Int? = null)

// Registro tal como lo devuelve el pull. La versión llegará cuando el servidor la implemente
@Serializable
data class PruebaRemota(
    val entityId: String,
    val titulo: String,
    val texto: String,
    val numero: Int,
    val createdAt: Long,
    val version: Int? = null
)
