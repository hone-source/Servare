package com.spiralsoft.servare.domain.repository

import kotlinx.coroutines.flow.Flow
import com.spiralsoft.servare.domain.model.Prueba

interface PruebaRepository {
    fun observarTodas(): Flow<List<Prueba>>

    suspend fun obtener(entityId: String): ResultadoObtencionPrueba

    suspend fun guardar(titulo: String, texto: String, numero: Int): ResultadoGuardadoPrueba

    suspend fun actualizar(entityId: String, titulo: String, texto: String, numero: Int): ResultadoGuardadoPrueba

    suspend fun eliminar(entityId: String): ResultadoEliminacionPrueba
}

sealed interface ResultadoGuardadoPrueba {
    data class Guardada(val entityId: String) : ResultadoGuardadoPrueba
    data object FaltaTitulo : ResultadoGuardadoPrueba
    data object NoEncontrada : ResultadoGuardadoPrueba // solo al actualizar
    data object SinEspacio : ResultadoGuardadoPrueba
    data object ErrorAlmacenamiento : ResultadoGuardadoPrueba
}

sealed interface ResultadoObtencionPrueba {
    data class Encontrada(val prueba: Prueba) : ResultadoObtencionPrueba
    data object NoEncontrada : ResultadoObtencionPrueba
    data object ErrorAlmacenamiento : ResultadoObtencionPrueba
}

sealed interface ResultadoEliminacionPrueba {
    data class Eliminada(val titulo: String) : ResultadoEliminacionPrueba
    data object NoEncontrada : ResultadoEliminacionPrueba
    data object ErrorAlmacenamiento : ResultadoEliminacionPrueba
}
