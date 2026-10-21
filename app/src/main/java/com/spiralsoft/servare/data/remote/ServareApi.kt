package com.spiralsoft.servare.data.remote

import retrofit2.Response
import retrofit2.http.GET

/** Endpoints del servidor API. Rutas relativas a la baseUrl */
interface ServareApi {
    @GET("health")
    suspend fun health(): Response<HealthResponse>
}

data class HealthResponse(
    val status: String?,
    val time: String?
)
