package com.spiralsoft.servare.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import com.spiralsoft.servare.domain.model.TipoLugar

// Elemento del espacio sagrado de una ficha; orden conserva la posición en la tabla
@Entity(
    tableName = "ficha_elementos",
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
data class ElementoEspacioEntity(
    val fichaId: String,
    val orden: Int,
    val tipo: TipoLugar? = null,
    val forma: String = "",
    val ancestro: String = "",
    val uso: String = "",
    val caracteristicas: String = "",
    val elementos: String = "",
    val largo: String = "",
    val ancho: String = "",
    val alto: String = "",
    val profundidad: String = ""
)
