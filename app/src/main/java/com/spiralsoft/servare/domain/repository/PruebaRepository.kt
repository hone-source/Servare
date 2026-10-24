package com.spiralsoft.servare.domain.repository

import kotlinx.coroutines.flow.Flow
import com.spiralsoft.servare.domain.model.Prueba

interface PruebaRepository {
    fun observarTodas(): Flow<List<Prueba>>

    suspend fun guardar(titulo: String, texto: String, numero: Int): ResultadoGuardadoPrueba

    suspend fun eliminar(entityId: String): ResultadoEliminacionPrueba
}

sealed interface ResultadoGuardadoPrueba {
    data class Guardada(val entityId: String) : ResultadoGuardadoPrueba
    data object FaltaTitulo : ResultadoGuardadoPrueba
    data object SinEspacio : ResultadoGuardadoPrueba
    data object ErrorAlmacenamiento : ResultadoGuardadoPrueba
}

sealed interface ResultadoEliminacionPrueba {
    data class Eliminada(val titulo: String) : ResultadoEliminacionPrueba
    data object NoEncontrada : ResultadoEliminacionPrueba
    data object ErrorAlmacenamiento : ResultadoEliminacionPrueba
}
