package com.spiralsoft.servare.data.local.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import com.spiralsoft.servare.domain.model.Ficha
import com.spiralsoft.servare.domain.model.FichaResumen

@Entity(tableName = "fichas")
data class FichaEntity(
    @PrimaryKey val entityId: String,   // UUID generado en el cliente
    @Embedded val datos: Ficha,
    val createdAt: Instant,
    val updatedAt: Instant,
    val version: Int? = null            // última versión conocida del servidor (ETag); base de las operaciones
)

fun FichaEntity.toResumen() =
    FichaResumen(
        entityId = entityId,
        nombre = datos.nombre,
        tipoEspacio = datos.tipoEspacio,
        comunidad = datos.comunidad,
        fechaLevantamiento = datos.fechaLevantamiento
    )
