package com.spiralsoft.servare.data.remote

import javax.inject.Inject
import com.spiralsoft.servare.data.local.entity.PruebaEntity

// Fuente de datos remota, solo la usa el motor de sincronización.
// La traducción de respuestas a resultados es común a todos los recursos (TraductorRespuestas)
class PruebaRemoteDataSource @Inject constructor(
    private val api: PruebaApi,
    private val traductor: TraductorRespuestas
) {
    suspend fun crear(operationId: String, prueba: PruebaEntity): ResultadoEnvio =
        traductor.enviar { api.crear(prueba.entityId, operationId, prueba.aRequest(baseVersion = null)) }

    suspend fun actualizar(operationId: String, prueba: PruebaEntity, baseVersion: Int?): ResultadoEnvio =
        traductor.enviar { api.actualizar(prueba.entityId, operationId, prueba.aRequest(baseVersion)) }

    suspend fun eliminar(operationId: String, entityId: String, baseVersion: Int?): ResultadoEnvio =
        traductor.enviar { api.eliminar(entityId, operationId, baseVersion) }

    suspend fun listar(): ResultadoPull<PruebaRemota> = traductor.listar { api.listar() }
}

private fun PruebaEntity.aRequest(baseVersion: Int?) =
    PruebaRequest(entityId, titulo, texto, numero, createdAt, baseVersion)
