package com.spiralsoft.servare.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

// Mismo contrato que PruebaApi: el operationId viaja en Idempotency-Key
interface FichaApi {

    @GET("api/fichas")
    suspend fun listar(): Response<List<FichaRemota>>

    @POST("api/fichas/{entityId}")
    suspend fun crear(
        @Path("entityId") entityId: String,
        @Header(PruebaApi.IDEMPOTENCY_KEY) operationId: String,
        @Body ficha: FichaRequest
    ): Response<VersionResponse>

    @PUT("api/fichas/{entityId}")
    suspend fun actualizar(
        @Path("entityId") entityId: String,
        @Header(PruebaApi.IDEMPOTENCY_KEY) operationId: String,
        @Body ficha: FichaRequest
    ): Response<VersionResponse>

    @DELETE("api/fichas/{entityId}")
    suspend fun eliminar(
        @Path("entityId") entityId: String,
        @Header(PruebaApi.IDEMPOTENCY_KEY) operationId: String,
        @Query("baseVersion") baseVersion: Int?
    ): Response<VersionResponse>
}
