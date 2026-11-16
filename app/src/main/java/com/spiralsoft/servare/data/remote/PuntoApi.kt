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

// Contrato con el servidor.
// El operationId viaja en Idempotency-Key, con el mismo valor el servidor
// devuelve el resultado previo en lugar de volver a aplicar la operación
interface PuntoApi {

    @GET("api/puntos")
    suspend fun listar(): Response<List<PuntoRemoto>>

    @POST("api/puntos/{entityId}")
    suspend fun crear(
        @Path("entityId") entityId: String,
        @Header("Idempotency-Key") operationId: String,
        @Body punto: PuntoRequest
    ): Response<VersionResponse>

    @PUT("api/puntos/{entityId}")
    suspend fun actualizar(
        @Path("entityId") entityId: String,
        @Header("Idempotency-Key") operationId: String,
        @Body punto: PuntoRequest
    ): Response<VersionResponse>

    @DELETE("api/puntos/{entityId}")
    suspend fun eliminar(
        @Path("entityId") entityId: String,
        @Header("Idempotency-Key") operationId: String,
        @Query("baseVersion") baseVersion: Int?
    ): Response<VersionResponse>
}
