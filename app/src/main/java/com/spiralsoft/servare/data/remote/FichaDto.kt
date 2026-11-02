package com.spiralsoft.servare.data.remote

import kotlinx.serialization.Serializable
import com.spiralsoft.servare.domain.model.Ficha

// Contenido de la ficha tal como se envía, se lee de la tabla en el momento del envío.
// Los campos que valen su valor por defecto no viajan: el servidor los completa
@Serializable
data class FichaRequest(
    val datos: Ficha,
    val createdAt: Long,
    val baseVersion: Int? = null            // vacía en CREAR
)

// Ficha tal como la devuelve el pull y el campo "actual" del 409
@Serializable
data class FichaRemota(
    val entityId: String,
    val datos: Ficha,
    val createdAt: Long,
    val version: Int? = null
)
