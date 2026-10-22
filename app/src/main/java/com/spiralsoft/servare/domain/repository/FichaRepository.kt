package com.spiralsoft.servare.domain.repository

import com.spiralsoft.servare.data.local.entity.OperationEntity
import com.spiralsoft.servare.domain.model.Ficha
import com.spiralsoft.servare.domain.model.FichaResumen
import kotlinx.coroutines.flow.Flow

interface FichaRepository {
    fun observarResumenes(): Flow<List<FichaResumen>>

    /** entityId null = ficha nueva; el UUID se genera aquí. */
    suspend fun guardar(entityId: String?, ficha: Ficha): ResultadoGuardado

    suspend fun obtener(entityId: String): ResultadoCarga

    suspend fun eliminar(entityId: String): ResultadoEliminacion

    /** Pull: la ficha tal como la tiene el servidor, con su [version] (ETag) si la envía. */
    suspend fun aplicarRemota(entityId: String, ficha: Ficha, version: Int?)

    /** Ids de las fichas guardadas en el dispositivo. */
    suspend fun idsLocales(): Set<String>

    /**
     * Pull: el servidor ya no tiene estas fichas y se quitan del dispositivo, salvo las que tienen
     * operaciones sin terminar (esas las resuelve el push). Devuelve el nombre de cada ficha quitada.
     */
    suspend fun quitarEliminadasEnServidor(entityIds: Collection<String>): List<String?>

    /** El servidor confirmó [op]: COMPLETADA y la ficha guarda [version], en una sola transacción. */
    suspend fun confirmarEnvio(op: OperationEntity, version: Int?)
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
    data object Eliminada : ResultadoEliminacion
    data object NoEncontrada : ResultadoEliminacion
    data object ErrorAlmacenamiento : ResultadoEliminacion
}
