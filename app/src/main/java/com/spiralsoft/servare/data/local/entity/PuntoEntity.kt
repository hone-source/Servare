package com.spiralsoft.servare.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

// Punto guardado desde el mapa. Se sincroniza con el servidor por el outbox
@Entity(tableName = "punto")
data class PuntoEntity(
    @PrimaryKey val entityId: String,       // UUID generado en el cliente
    val nombre: String,
    val latitud: Double,
    val longitud: Double,
    val createdAt: Long,                    // epoch ms
    val version: Int? = null                // última versión conocida del servidor, null hasta el primer push
)
