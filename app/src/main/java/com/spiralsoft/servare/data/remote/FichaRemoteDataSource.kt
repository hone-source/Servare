package com.spiralsoft.servare.data.remote

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import javax.inject.Inject
import com.spiralsoft.servare.data.local.entity.FichaEntity
import com.spiralsoft.servare.domain.model.Ficha

// Fuente de datos remota de las fichas, solo la usa el motor de sincronización
class FichaRemoteDataSource @Inject constructor(
    private val api: FichaApi,
    private val traductor: TraductorRespuestas,
    private val json: Json
) {
    suspend fun crear(operationId: String, ficha: FichaEntity): ResultadoEnvio {
        val request = ficha.aRequest(baseVersion = null) ?: return ilegible()
        return traductor.enviar { api.crear(ficha.entityId, operationId, request) }
    }

    suspend fun actualizar(operationId: String, ficha: FichaEntity, baseVersion: Int?): ResultadoEnvio {
        val request = ficha.aRequest(baseVersion) ?: return ilegible()
        return traductor.enviar { api.actualizar(ficha.entityId, operationId, request) }
    }

    suspend fun eliminar(operationId: String, entityId: String, baseVersion: Int?): ResultadoEnvio =
        traductor.enviar { api.eliminar(entityId, operationId, baseVersion) }

    suspend fun listar(): ResultadoPull<FichaRemota> = traductor.listar { api.listar() }

    // null si el JSON guardado no se puede leer: no hay nada que enviar
    private fun FichaEntity.aRequest(baseVersion: Int?): FichaRequest? =
        try {
            FichaRequest(json.decodeFromString<Ficha>(datos), createdAt, baseVersion)
        } catch (_: SerializationException) {
            null
        }

    private fun ilegible() = ResultadoEnvio.Definitivo("La ficha guardada en el dispositivo no se pudo leer")
}
