package com.spiralsoft.servare.domain.repository

import kotlinx.coroutines.flow.Flow
import com.spiralsoft.servare.domain.model.Prueba

interface PruebaRepository {
    fun observarTodas(): Flow<List<Prueba>>

    suspend fun guardar(titulo: String, texto: String, numero: Int): ResultadoGuardadoPrueba
}

sealed interface ResultadoGuardadoPrueba {
    data class Guardada(val entityId: String) : ResultadoGuardadoPrueba
    data object FaltaTitulo : ResultadoGuardadoPrueba
    data object SinEspacio : ResultadoGuardadoPrueba
    data object ErrorAlmacenamiento : ResultadoGuardadoPrueba
}
