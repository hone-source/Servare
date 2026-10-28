package com.spiralsoft.servare.data.remote

import kotlinx.serialization.Serializable

@Serializable
data class RegistroRequest(
    val correo: String,
    val contrasena: String
)

@Serializable
data class LoginRequest(
    val correo: String,
    val contrasena: String
)

// Sesión que devuelve el servidor al registrarse o iniciar sesión. Expira en en epoch ms
@Serializable
data class SesionResponse(
    val token: String,
    val expiraEn: Long,
    val correo: String
)
