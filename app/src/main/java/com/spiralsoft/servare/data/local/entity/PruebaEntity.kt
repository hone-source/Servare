package com.spiralsoft.servare.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

// Registro de la pantalla de prueba; solo local, no pasa por la cola
@Entity(tableName = "pruebas")
data class PruebaEntity(
    @PrimaryKey val entityId: String,       // UUID generado en el cliente
    val titulo: String,
    val texto: String,
    val numero: Int,
    val createdAt: Long                     // epoch ms
)
