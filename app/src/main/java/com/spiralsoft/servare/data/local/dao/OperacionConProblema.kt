package com.spiralsoft.servare.data.local.dao

import com.spiralsoft.servare.domain.model.CausaConflicto
import com.spiralsoft.servare.domain.model.EstadoOperacion
import com.spiralsoft.servare.domain.model.TipoEntidad
import com.spiralsoft.servare.domain.model.TipoOperacion

// Operación FALLIDA, en CONFLICTO o BLOQUEADA, con el título del registro para poder nombrarlo al usuario
data class OperacionConProblema(
    val operationId: String,
    val type: TipoOperacion,
    val status: EstadoOperacion,
    val entityType: TipoEntidad,
    val entityId: String,
    val lastError: String?,
    val titulo: String?, // null si el registro ya no está en el dispositivo
    val texto: String? = null,
    val numero: Int? = null,
    val conflictCause: CausaConflicto? = null,
    val serverSnapshot: String? = null,
    // Cambios posteriores del mismo registro que esperan a que se resuelva este (BLOQUEADA, o PENDIENTE
    // creados después, que el motor no envía mientras haya uno anterior sin terminar)
    val posteriores: Int = 0
)
