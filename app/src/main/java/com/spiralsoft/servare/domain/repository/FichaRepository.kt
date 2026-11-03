package com.spiralsoft.servare.domain.repository

import kotlinx.coroutines.flow.Flow
import com.spiralsoft.servare.domain.model.Ficha
import com.spiralsoft.servare.domain.model.FichaResumen

interface FichaRepository : RepositorioSincronizable {
    fun observarResumenes(): Flow<List<FichaResumen>>

    suspend fun obtener(entityId: String): ResultadoObtencionFicha

    suspend fun guardar(ficha: Ficha): ResultadoGuardadoFicha

    suspend fun actualizar(entityId: String, ficha: Ficha): ResultadoGuardadoFicha

    suspend fun eliminar(entityId: String): ResultadoEliminacionFicha
}

sealed interface ResultadoGuardadoFicha {
    data class Guardada(val entityId: String) : ResultadoGuardadoFicha
    data object FaltaNombre : ResultadoGuardadoFicha
    data object NoEncontrada : ResultadoGuardadoFicha // solo al actualizar
    data object SinEspacio : ResultadoGuardadoFicha
    data object ErrorAlmacenamiento : ResultadoGuardadoFicha
}

sealed interface ResultadoObtencionFicha {
    data class Encontrada(val ficha: Ficha) : ResultadoObtencionFicha
    data object NoEncontrada : ResultadoObtencionFicha
    data object ErrorAlmacenamiento : ResultadoObtencionFicha
}

sealed interface ResultadoEliminacionFicha {
    data class Eliminada(val nombre: String) : ResultadoEliminacionFicha
    data object NoEncontrada : ResultadoEliminacionFicha
    data object ErrorAlmacenamiento : ResultadoEliminacionFicha
}
