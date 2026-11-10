package com.spiralsoft.servare.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

// Contrato de sesión con el servidor
interface AuthApi {

    // La clave viaja en Idempotency-Key, con la misma clave el servidor reconoce
    // el reintento de un registro que sí llegó a crear la cuenta
    @POST("api/auth/registro")
    suspend fun registrar(
        @Header("Idempotency-Key") claveRegistro: String,
        @Body registro: RegistroRequest
    ): Response<SesionResponse>

    // Iniciar sesión no crea nada, repetirlo es inofensivo y no necesita clave
    @POST("api/auth/login")
    suspend fun iniciarSesion(
        @Body credenciales: LoginRequest
    ): Response<SesionResponse>
}
