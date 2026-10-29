package com.spiralsoft.servare.data.local.entity

import androidx.room.Entity
import com.spiralsoft.servare.domain.model.TipoEntidad

// Borrado lógico: el registro sigue en su tabla hasta que el servidor confirma el ELIMINAR,
// así un borrado en CONFLICTO o FALLIDA no pierde los datos
@Entity(
    tableName = "eliminaciones",
    primaryKeys = ["entityType", "entityId"]
)
data class EliminacionEntity(
    val entityType: TipoEntidad,
    val entityId: String,
    val eliminadaEn: Long                   // epoch ms
)
