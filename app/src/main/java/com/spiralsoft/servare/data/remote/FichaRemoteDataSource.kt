package com.spiralsoft.servare.data.remote

import javax.inject.Inject
import com.spiralsoft.servare.data.local.dao.FichaCompleta
import com.spiralsoft.servare.data.local.dao.aFicha

// Fuente de datos remota de las fichas, solo la usa el motor de sincronización
class FichaRemoteDataSource @Inject constructor(
    private val api: FichaApi,
    private val traductor: TraductorRespuestas
) {
    suspend fun crear(operationId: String, ficha: FichaCompleta): ResultadoEnvio {
        val request = ficha.aRequest(baseVersion = null)
        return traductor.enviar { api.crear(ficha.ficha.entityId, operationId, request) }
    }

    suspend fun actualizar(operationId: String, ficha: FichaCompleta, baseVersion: Int?): ResultadoEnvio {
        val request = ficha.aRequest(baseVersion)
        return traductor.enviar { api.actualizar(ficha.ficha.entityId, operationId, request) }
    }

    suspend fun eliminar(operationId: String, entityId: String, baseVersion: Int?): ResultadoEnvio =
        traductor.enviar { api.eliminar(entityId, operationId, baseVersion) }

    suspend fun listar(): ResultadoPull<FichaRemota> = traductor.listar { api.listar() }

    private fun FichaCompleta.aRequest(baseVersion: Int?) =
        FichaRequest(aFicha().aDto(), ficha.createdAt, baseVersion)
}
