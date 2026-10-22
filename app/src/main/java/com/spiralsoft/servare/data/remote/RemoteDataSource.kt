package com.spiralsoft.servare.data.remote

import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import java.io.IOException
import java.io.InterruptedIOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds
import com.spiralsoft.servare.domain.model.Ficha

/**
 * Cliente del servidor API.
 * Solo lo usan el repositorio, el motor de sincronización y el monitor (health check).
 */
interface RemoteDataSource {
    /** true solo si el servidor respondió que está en funcionamiento. */
    suspend fun healthCheck(): Boolean

    /** Push de CREATE/UPDATE: [json] es el `data` de la operación, tal como se guardó. */
    suspend fun putFicha(entityId: String, json: String, operationId: String, baseVersion: Int?): RespuestaRemota<Unit>

    /** Push de DELETE. Un 404/410 es éxito: la ficha ya no existe, que es lo que se pedía. */
    suspend fun deleteFicha(entityId: String, operationId: String, baseVersion: Int?): RespuestaRemota<Unit>

    /** Pull: ids de las fichas en el servidor. */
    suspend fun listFichaIds(): RespuestaRemota<List<String>>

    /** Pull: ficha completa. */
    suspend fun getFicha(entityId: String): RespuestaRemota<Ficha>
}

/** Resultado de una petición, clasificado según la tabla de errores de 2.2. */
sealed interface RespuestaRemota<out T> {
    /** [version] viene del ETag, si el servidor la envía. */
    data class Exito<T>(val valor: T, val version: Int? = null) : RespuestaRemota<T>

    /** Conexión perdida o sin respuesta: no se sabe si el servidor aplicó la petición. */
    data object SinRespuesta : RespuestaRemota<Nothing>

    /** El servidor no respondió a tiempo: también incierto, pero cuenta como intento. */
    data object Timeout : RespuestaRemota<Nothing>

    /** 401/403: se pausa toda la cola. */
    data object SesionInvalida : RespuestaRemota<Nothing>

    /** 5xx, 408 o 429: error temporal con el servidor alcanzable. */
    data class ErrorTemporal(val codigo: Int) : RespuestaRemota<Nothing>

    /** 409/412 (versión distinta) o 404/410 (el registro ya no existe en el servidor). */
    data class Conflicto(val codigo: Int, val motivo: String?) : RespuestaRemota<Nothing>

    /** 400, 413, 422 u otro 4xx, o una respuesta que no se pudo leer: error definitivo. */
    data class Rechazada(val codigo: Int?, val motivo: String?) : RespuestaRemota<Nothing>
}

@Singleton
class RetrofitRemoteDataSource @Inject constructor(
    private val api: ServareApi,
    private val json: Json
) : RemoteDataSource {

    // RNF-05: el cambio de conectividad debe detectarse en 5 s
    private val healthTimeout = 5.seconds

    override suspend fun healthCheck(): Boolean =
        withTimeoutOrNull(healthTimeout) {
            try {
                val response = api.health()
                response.isSuccessful && response.body()?.status == "ok"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // sin DNS, conexión rechazada, TLS no válido, respuesta que no es del API
                false
            }
        } ?: false

    override suspend fun putFicha(
        entityId: String,
        json: String,
        operationId: String,
        baseVersion: Int?
    ): RespuestaRemota<Unit> =
        call { api.putFicha(entityId, operationId, baseVersion?.let(::etag), json.toRequestBody(JSON)) }
            .map { }

    override suspend fun deleteFicha(entityId: String, operationId: String, baseVersion: Int?): RespuestaRemota<Unit> {
        val r = call { api.deleteFicha(entityId, operationId, baseVersion?.let(::etag)) }.map { }
        // Un reintento incierto encuentra la ficha ya borrada por el envío anterior
        val yaNoExiste = r is RespuestaRemota.Conflicto && (r.codigo == 404 || r.codigo == 410)
        return if (yaNoExiste) RespuestaRemota.Exito(Unit) else r
    }

    override suspend fun listFichaIds(): RespuestaRemota<List<String>> =
        call { api.listFichas() }.map { lista -> lista.orEmpty().mapNotNull { it.entityId } }

    override suspend fun getFicha(entityId: String): RespuestaRemota<Ficha> =
        call { api.getFicha(entityId) }.map { it ?: return RespuestaRemota.Rechazada(null, SIN_CUERPO) }

    /** Ejecuta la petición y la clasifica; 204 y similares llegan como `Exito(null)`. */
    private suspend fun <T> call(request: suspend () -> Response<T>): RespuestaRemota<T?> = try {
        val response = request()
        if (response.isSuccessful) {
            RespuestaRemota.Exito(response.body(), versionOf(response))
        } else {
            classify(response.code(), reasonOf(response))
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: InterruptedIOException) {
        RespuestaRemota.Timeout              // callTimeout de OkHttp o timeout de socket
    } catch (e: IOException) {
        RespuestaRemota.SinRespuesta         // sin red, conexión cortada, sin DNS, TLS
    } catch (e: SerializationException) {
        RespuestaRemota.Rechazada(null, RESPUESTA_INVALIDA)
    }

    private fun classify(code: Int, reason: String?): RespuestaRemota<Nothing> = when (code) {
        401, 403 -> RespuestaRemota.SesionInvalida
        408, 429, in 500..599 -> RespuestaRemota.ErrorTemporal(code)
        404, 409, 410, 412 -> RespuestaRemota.Conflicto(code, reason)
        else -> RespuestaRemota.Rechazada(code, reason)
    }

    /** Motivo que da el API en `{"error": "..."}`; Retrofit ya dejó el cuerpo en memoria. */
    private fun reasonOf(response: Response<*>): String? = try {
        response.errorBody()?.string()?.let { json.decodeFromString<ErrorResponse>(it).error }
    } catch (e: Exception) {
        null
    }

    private fun versionOf(response: Response<*>): Int? =
        response.headers()["ETag"]?.removePrefix("W/")?.trim('"')?.toIntOrNull()

    private fun etag(version: Int) = "\"$version\""

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
        const val SIN_CUERPO = "El servidor no entregó el contenido"
        const val RESPUESTA_INVALIDA = "La respuesta del servidor no es válida"
    }
}

private inline fun <T, R> RespuestaRemota<T>.map(transform: (T) -> R): RespuestaRemota<R> = when (this) {
    is RespuestaRemota.Exito -> RespuestaRemota.Exito(transform(valor), version)
    RespuestaRemota.SinRespuesta -> RespuestaRemota.SinRespuesta
    RespuestaRemota.Timeout -> RespuestaRemota.Timeout
    RespuestaRemota.SesionInvalida -> RespuestaRemota.SesionInvalida
    is RespuestaRemota.ErrorTemporal -> this
    is RespuestaRemota.Conflicto -> this
    is RespuestaRemota.Rechazada -> this
}
