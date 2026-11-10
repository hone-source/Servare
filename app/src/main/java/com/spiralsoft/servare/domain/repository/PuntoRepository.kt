package com.spiralsoft.servare.domain.repository

import kotlinx.coroutines.flow.Flow
import com.spiralsoft.servare.domain.model.Punto

interface PuntoRepository {
    fun observarTodos(): Flow<List<Punto>>

    suspend fun guardar(nombre: String, latitud: Double, longitud: Double): ResultadoGuardadoPunto

    suspend fun actualizar(entityId: String, nombre: String, latitud: Double, longitud: Double): ResultadoGuardadoPunto

    suspend fun eliminar(entityId: String): ResultadoEliminacionPunto
}

sealed interface ResultadoGuardadoPunto {
    data class Guardado(val nombre: String) : ResultadoGuardadoPunto
    data object FaltaNombre : ResultadoGuardadoPunto
    data object CoordenadasInvalidas : ResultadoGuardadoPunto
    data object NoEncontrado : ResultadoGuardadoPunto
    data object SinEspacio : ResultadoGuardadoPunto
    data object ErrorAlmacenamiento : ResultadoGuardadoPunto
}

sealed interface ResultadoEliminacionPunto {
    data class Eliminado(val nombre: String) : ResultadoEliminacionPunto
    data object NoEncontrado : ResultadoEliminacionPunto
    data object ErrorAlmacenamiento : ResultadoEliminacionPunto
}
