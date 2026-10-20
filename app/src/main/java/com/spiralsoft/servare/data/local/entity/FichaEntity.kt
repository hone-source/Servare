package com.spiralsoft.servare.data.local.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import com.spiralsoft.servare.data.model.Ficha
import com.spiralsoft.servare.data.model.FichaResumen

// Las listas repetibles y los RegistroArchivos se guardan como JSON (ver Converters)
@Entity(tableName = "fichas")
data class FichaEntity(
    @PrimaryKey val entityId: String,   // UUID generado en el cliente
    @Embedded val datos: Ficha,
    val createdAt: Instant,
    val updatedAt: Instant
)

fun FichaEntity.toResumen() = FichaResumen(
    entityId = entityId,
    nombre = datos.nombre,
    tipoEspacio = datos.tipoEspacio,
    comunidad = datos.comunidad,
    fechaLevantamiento = datos.fechaLevantamiento,
    pendienteSincronizar = false        // TODO: cola de operaciones
)
