package com.spiralsoft.servare.data.remote

// Resultado de pedir el inicio de sesión al servidor, el repositorio lo traduce a ResultadoInicioSesion
sealed interface ResultadoLoginRemoto {
    data class Autenticada(val token: String, val expiraEn: Long, val correo: String) : ResultadoLoginRemoto
    // 401: el servidor no dice si falló el correo o la contraseña
    data object CredencialesInvalidas : ResultadoLoginRemoto
    // 400, 413 o 422
    data object Rechazado : ResultadoLoginRemoto
    // 5xx, otro código inesperado o respuesta ilegible
    data object ErrorServidor : ResultadoLoginRemoto
    // Timeout: el servidor no respondió a tiempo
    data object SinRespuesta : ResultadoLoginRemoto
    // Se perdió la conexión
    data object SinConexion : ResultadoLoginRemoto
}
