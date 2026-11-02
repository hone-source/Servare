package com.spiralsoft.servare.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey

// Fila de la poligonal de una ficha, orden conserva la posición en la tabla
@Entity(
    tableName = "ficha_poligonal",
    primaryKeys = ["fichaId", "orden"],
    foreignKeys = [
        ForeignKey(
            entity = FichaEntity::class,
            parentColumns = ["entityId"],
            childColumns = ["fichaId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class PuntoPoligonalEntity(
    val fichaId: String,
    val orden: Int,
    val puntoReferencia: String = "",
    val utm: String = "",
    val margenError: String = "",
    val altura: String = ""
)
