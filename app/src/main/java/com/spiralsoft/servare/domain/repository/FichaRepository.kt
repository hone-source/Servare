package com.spiralsoft.servare.domain.repository

import kotlinx.coroutines.flow.Flow
import com.spiralsoft.servare.domain.model.AccionOperacion
import com.spiralsoft.servare.domain.model.Ficha
import com.spiralsoft.servare.domain.model.FichaResumen

interface FichaRepository {
    fun observarResumenes(): Flow<List<FichaResumen>>

    // true cuando ya se descargaron las fichas del servidor al menos una vez.
    // Distingue "vacío porque no hay datos" de "vacío porque aún no se descargaron"
    fun observarDescargaInicial(): Flow<Boolean>

    suspend fun obtener(entityId: String): ResultadoObtencionFicha

    suspend fun guardar(ficha: Ficha): ResultadoGuardadoFicha

    suspend fun actualizar(entityId: String, ficha: Ficha): ResultadoGuardadoFicha

    suspend fun eliminar(entityId: String): ResultadoEliminacionFicha

    // El usuario decide qué hacer con una operación FALLIDA o en CONFLICTO
    suspend fun resolver(operationId: String, accion: AccionOperacion): ResultadoResolucion

    // El usuario pide comprobar los cambios del servidor.
    // Solo descarga: no envía la cola
    suspend fun refrescar(): ResultadoRefresco
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
