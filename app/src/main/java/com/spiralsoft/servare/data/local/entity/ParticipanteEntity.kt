package com.spiralsoft.servare.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey

// Participante del aval de una ficha
// Orden conserva la posición en la tabla
@Entity(
    tableName = "ficha_participantes",
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
data class ParticipanteEntity(
    val fichaId: String,
    val orden: Int,
    val cargo: String = "",
    val nombre: String = ""
)
