package com.spiralsoft.servare.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

// Registro de la pantalla de prueba
@Entity(tableName = "pruebas")
data class PruebaEntity(
    @PrimaryKey val entityId: String,       // UUID generado en el cliente
    val titulo: String,
    val texto: String,
    val numero: Int,
    val createdAt: Long,                    // epoch ms
    val version: Int? = null                // última versión conocida del servidor; null hasta el primer push
)
