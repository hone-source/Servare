package com.spiralsoft.servare.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import com.spiralsoft.servare.domain.model.TipoRitual

// Ritual de una ficha, orden conserva la posición en la tabla
@Entity(
    tableName = "ficha_rituales",
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
data class RitualEntity(
    val fichaId: String,
    val orden: Int,
    val tipo: TipoRitual? = null,
    val tipoOtro: String = "",
    val nombres: String = "",
    val temporalidad: String = "",
    val descripcion: String = ""
)
