package com.spiralsoft.servare.data.remote

import kotlinx.serialization.Serializable
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PUT
import retrofit2.http.Path
import com.spiralsoft.servare.domain.model.Ficha

/**
 * Endpoints del servidor API. Rutas relativas a la baseUrl
 *
 * En push van `Idempotency-Key` (el operationId) e `If-Match` (la baseVersion, si hay):
 * el servidor reconoce un reintento y detecta el conflicto de versión (409/412).
 * La versión nueva se lee del `ETag` de la respuesta.
 */
interface ServareApi {
    @GET("health")
    suspend fun health(): Response<HealthResponse>

    /** Crea (201) o reemplaza (200) la ficha; el id es el entityId generado en la app. */
    @PUT("fichas/{id}")
    suspend fun putFicha(
        @Path("id") entityId: String,
        @Header("Idempotency-Key") operationId: String,
        @Header("If-Match") ifMatch: String?,
        @Body ficha: RequestBody
    ): Response<Unit>

    @DELETE("fichas/{id}")
    suspend fun deleteFicha(
        @Path("id") entityId: String,
        @Header("Idempotency-Key") operationId: String,
        @Header("If-Match") ifMatch: String?
    ): Response<Unit>

    @GET("fichas")
    suspend fun listFichas(): Response<List<FichaRemotaResumen>>

    @GET("fichas/{id}")
    suspend fun getFicha(@Path("id") entityId: String): Response<Ficha>
}

@Serializable
data class HealthResponse(
    val status: String?,
    val time: String?
)

/** Del resumen que entrega el API solo se usa el id. */
@Serializable
data class FichaRemotaResumen(
    val entityId: String?
)

/** Cuerpo de error del API: `{"error": "..."}`. */
@Serializable
data class ErrorResponse(
    val error: String?
)
