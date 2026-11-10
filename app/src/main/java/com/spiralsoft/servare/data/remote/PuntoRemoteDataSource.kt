package com.spiralsoft.servare.data.remote

import javax.inject.Inject
import com.spiralsoft.servare.data.local.entity.PuntoEntity

// Fuente de datos remota, solo la usa el motor de sincronización.
// La traducción de respuestas a resultados es común a todos los recursos (TraductorRespuestas)
class PuntoRemoteDataSource @Inject constructor(
    private val api: PuntoApi,
    private val traductor: TraductorRespuestas
) {
    suspend fun crear(operationId: String, punto: PuntoEntity): ResultadoEnvio =
        traductor.enviar { api.crear(punto.entityId, operationId, punto.aRequest(baseVersion = null)) }

    suspend fun actualizar(operationId: String, punto: PuntoEntity, baseVersion: Int?): ResultadoEnvio =
        traductor.enviar { api.actualizar(punto.entityId, operationId, punto.aRequest(baseVersion)) }

    suspend fun eliminar(operationId: String, entityId: String, baseVersion: Int?): ResultadoEnvio =
        traductor.enviar { api.eliminar(entityId, operationId, baseVersion) }

    suspend fun listar(): ResultadoPull<PuntoRemoto> = traductor.listar { api.listar() }
}

private fun PuntoEntity.aRequest(baseVersion: Int?) =
    PuntoRequest(entityId, nombre, latitud, longitud, createdAt, baseVersion)
