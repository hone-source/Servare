package com.spiralsoft.servare.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sacred_sites")
data class SacredSiteEntity(
    @PrimaryKey val entityId: String,   // UUID generado en el cliente
    val name: String,
    val description: String,
    val type: String,
    val latitude: Double,
    val longitude: Double,
    val serverVersion: Int = 0          // 0 = el servidor aún no lo conoce
)
