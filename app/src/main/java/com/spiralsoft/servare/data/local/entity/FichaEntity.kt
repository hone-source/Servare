package com.spiralsoft.servare.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.spiralsoft.servare.domain.model.TipoEspacio

@Entity(tableName = "fichas")
data class FichaEntity(
    @PrimaryKey val entityId: String,       // UUID generado en el cliente
    val datos: String,                      // Ficha completa en JSON
    // Columnas de resumen para la lista
    val nombre: String,
    val comunidad: String,
    val tipoEspacio: TipoEspacio?,
    val fechaLevantamiento: String?,
    val createdAt: Long,                    // epoch ms
    val updatedAt: Long,
    val version: Int? = null                // última versión conocida del servidor; null hasta el primer push
)
