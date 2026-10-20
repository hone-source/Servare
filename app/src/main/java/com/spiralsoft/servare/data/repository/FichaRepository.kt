package com.spiralsoft.servare.data.repository

import com.spiralsoft.servare.data.model.Ficha
import com.spiralsoft.servare.data.model.FichaResumen
import kotlinx.coroutines.flow.Flow

interface FichaRepository {
    fun observarResumenes(): Flow<List<FichaResumen>>

    /** entityId null = ficha nueva; el UUID se genera aquí. */
    suspend fun guardar(entityId: String?, ficha: Ficha): ResultadoGuardado

    suspend fun obtener(entityId: String): ResultadoCarga
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
