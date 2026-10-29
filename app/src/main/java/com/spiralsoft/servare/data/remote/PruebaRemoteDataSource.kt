package com.spiralsoft.servare.data.remote

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException
import javax.inject.Inject
import com.spiralsoft.servare.data.local.entity.PruebaEntity
import com.spiralsoft.servare.domain.model.CausaConflicto

// Fuente de datos remota, solo la usa el motor de sincronización.
// Traduce cada respuesta a un ResultadoEnvio según la tabla de errores de docs/2.2.md
class PruebaRemoteDataSource @Inject constructor(
    private val api: PruebaApi,
    private val json: Json
) {
    suspend fun crear(operationId: String, prueba: PruebaEntity): ResultadoEnvio =
        ejecutar { api.crear(prueba.entityId, operationId, prueba.aRequest(baseVersion = null)) }

    suspend fun actualizar(operationId: String, prueba: PruebaEntity, baseVersion: Int?): ResultadoEnvio =
        ejecutar { api.actualizar(prueba.entityId, operationId, prueba.aRequest(baseVersion)) }

    suspend fun eliminar(operationId: String, entityId: String, baseVersion: Int?): ResultadoEnvio =
        ejecutar { api.eliminar(entityId, operationId, baseVersion) }

    suspend fun listar(): ResultadoPull = try {
        val respuesta = api.listar()
        val codigo = respuesta.code()
        when {
            respuesta.isSuccessful -> ResultadoPull.Datos(respuesta.body().orEmpty())
            codigo == 401 || codigo == 403 -> ResultadoPull.SesionExpirada
            else -> ResultadoPull.Fallo("El servidor no pudo entregar los registros (HTTP $codigo)")
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: SocketTimeoutException) {
        // No se reporta como servidor caído: con un health check que sí responde, cada ciclo repetiría el timeout
        ResultadoPull.Fallo("El servidor tardó demasiado en entregar los registros")
    } catch (_: IOException) {
        ResultadoPull.SinServidor
    } catch (_: SerializationException) {
        ResultadoPull.Fallo("El servidor devolvió una respuesta que la app no entiende")
    }

    private suspend fun ejecutar(llamada: suspend () -> Response<PruebaResponse>): ResultadoEnvio = try {
        traducir(llamada())
    } catch (e: CancellationException) {
        throw e
    } catch (_: SocketTimeoutException) {
        ResultadoEnvio.Temporal("El servidor tardó demasiado en responder", sinRespuesta = true)
    } catch (_: IOException) {
        ResultadoEnvio.Incierto
    } catch (_: SerializationException) {
        ResultadoEnvio.Definitivo("El servidor devolvió una respuesta que la app no entiende")
    }

    private suspend fun traducir(respuesta: Response<PruebaResponse>): ResultadoEnvio {
        val codigo = respuesta.code()
        return when {
            respuesta.isSuccessful -> ResultadoEnvio.Confirmada(respuesta.body()?.version)
            codigo == 401 || codigo == 403 -> ResultadoEnvio.SesionExpirada
            codigo == 409 -> ResultadoEnvio.Conflicto(
                "El registro cambió en el servidor", CausaConflicto.VERSION_DISTINTA, leerActual(respuesta)
            )
            codigo == 404 || codigo == 410 -> ResultadoEnvio.Conflicto(
                "El registro ya no existe en el servidor", CausaConflicto.INEXISTENTE_EN_SERVIDOR
            )
            codigo == 413 -> ResultadoEnvio.Definitivo("El servidor rechazó el tamaño del envío")
            codigo == 422 -> ResultadoEnvio.Definitivo("El servidor rechazó los datos por no ser válidos")
            codigo == 408 || codigo == 429 || codigo >= 500 ->
                ResultadoEnvio.Temporal("El servidor no pudo atender la solicitud (HTTP $codigo)")
            else -> ResultadoEnvio.Definitivo("El servidor rechazó la operación (HTTP $codigo)")
        }
    }

    // El 409 trae el registro vigente. Sin él (cuerpo ilegible) el conflicto sigue siéndolo,
    // pero el usuario no podrá compararlo: solo descartar
    private suspend fun leerActual(respuesta: Response<PruebaResponse>): PruebaRemota? =
        withContext(Dispatchers.IO) {
            try {
                respuesta.errorBody()?.string()?.let { json.decodeFromString<ConflictoRemoto>(it).actual }
            } catch (_: SerializationException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
        }
}

private fun PruebaEntity.aRequest(baseVersion: Int?) =
    PruebaRequest(entityId, titulo, texto, numero, createdAt, baseVersion)
