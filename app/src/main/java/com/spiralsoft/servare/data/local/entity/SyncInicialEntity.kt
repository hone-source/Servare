package com.spiralsoft.servare.data.local.entity

import androidx.room.Entity
import com.spiralsoft.servare.domain.model.TipoEntidad

// Existe una fila cuando la sincronización inicial de esa entidad terminó. Se escribe en la misma
// transacción que los registros descargados, así nunca está marcada sin tener los datos
@Entity(tableName = "sync_inicial", primaryKeys = ["entityType"])
data class SyncInicialEntity(
    val entityType: TipoEntidad,
    val completadaEn: Long                  // epoch ms
)
