package com.spiralsoft.servare.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

// Contrato con el servidor. El operationId viaja en Idempotency-Key: con el mismo valor
// el servidor devuelve el resultado previo en lugar de volver a aplicar la operación
interface PruebaApi {
    @POST("api/pruebas/{entityId}")
    suspend fun crear(
        @Path("entityId") entityId: String,
        @Header(IDEMPOTENCY_KEY) operationId: String,
        @Body prueba: PruebaRequest
    ): Response<PruebaResponse>

    @PUT("api/pruebas/{entityId}")
    suspend fun actualizar(
        @Path("entityId") entityId: String,
        @Header(IDEMPOTENCY_KEY) operationId: String,
        @Body prueba: PruebaRequest
    ): Response<PruebaResponse>

    @DELETE("api/pruebas/{entityId}")
    suspend fun eliminar(
        @Path("entityId") entityId: String,
        @Header(IDEMPOTENCY_KEY) operationId: String,
        @Query("baseVersion") baseVersion: Int?
    ): Response<PruebaResponse>

    companion object {
        const val IDEMPOTENCY_KEY = "Idempotency-Key"
    }
}
