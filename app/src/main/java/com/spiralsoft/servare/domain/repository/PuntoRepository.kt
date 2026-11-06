package com.spiralsoft.servare.domain.repository

import kotlinx.coroutines.flow.Flow
import com.spiralsoft.servare.domain.model.Punto

interface PuntoRepository {
    fun observarTodos(): Flow<List<Punto>>

    suspend fun guardar(nombre: String, latitud: Double, longitud: Double): ResultadoGuardadoPunto
}

sealed interface ResultadoGuardadoPunto {
    data class Guardado(val nombre: String) : ResultadoGuardadoPunto
    data object FaltaNombre : ResultadoGuardadoPunto
    data object SinEspacio : ResultadoGuardadoPunto
    data object ErrorAlmacenamiento : ResultadoGuardadoPunto
}
