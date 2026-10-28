package com.spiralsoft.servare.data.remote

// Resultado de pedir el registro al servidor, el repositorio lo traduce a ResultadoRegistro
sealed interface ResultadoRegistroRemoto {
    data class Creada(val token: String, val expiraEn: Long, val correo: String) : ResultadoRegistroRemoto
    // 409
    data object CorreoYaRegistrado : ResultadoRegistroRemoto
    // 400, 413 o 422
    data object Rechazado : ResultadoRegistroRemoto
    // 5xx, otro código inesperado o respuesta ilegible
    data object ErrorServidor : ResultadoRegistroRemoto
    // Timeout: el servidor no respondió a tiempo
    data object SinRespuesta : ResultadoRegistroRemoto
    // Se perdió la conexión: el servidor pudo haber creado la cuenta
    data object SinConexion : ResultadoRegistroRemoto
}
