package com.spiralsoft.servare.domain.repository

import kotlinx.coroutines.flow.Flow
import com.spiralsoft.servare.domain.model.Ficha
import com.spiralsoft.servare.domain.model.FichaResumen

interface FichaRepository {
    fun observarResumenes(): Flow<List<FichaResumen>>

    /** entityId null = ficha nueva; el UUID se genera aquí. */
    suspend fun guardar(entityId: String?, ficha: Ficha): ResultadoGuardado

    suspend fun obtener(entityId: String): ResultadoCarga

    /** Quita la ficha del dispositivo y deja su eliminación en la cola. */
    suspend fun eliminar(entityId: String): ResultadoEliminacion
}

sealed interface ResultadoGuardado {
    data class Guardada(val entityId: String) : ResultadoGuardado
    data object FaltaNombre : ResultadoGuardado
    data object SinEspacio : ResultadoGuardado
    data object ErrorAlmacenamiento : ResultadoGuardado
}

sealed interface ResultadoCarga {
    data class Encontrada(val ficha: Ficha) : ResultadoCarga
    data object NoEncontrada : ResultadoCarga
    data object ErrorAlmacenamiento : ResultadoCarga
}

sealed interface ResultadoEliminacion {
    data class Eliminada(val nombre: String) : ResultadoEliminacion
    data object NoEncontrada : ResultadoEliminacion
    data object ErrorAlmacenamiento : ResultadoEliminacion
}
