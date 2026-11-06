package com.spiralsoft.servare.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

// Punto guardado desde el mapa. Es solo local: no tiene operaciones ni se sincroniza
@Entity(tableName = "punto")
data class PuntoEntity(
    @PrimaryKey val entityId: String,       // UUID generado en el cliente
    val nombre: String,
    val latitud: Double,
    val longitud: Double,
    val createdAt: Long                     // epoch ms
)
