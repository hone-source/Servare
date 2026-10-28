package com.spiralsoft.servare.ui.viewModel.state

import com.spiralsoft.servare.domain.model.AvisoSinConexion

// Estado de las pantallas que piden correo y contraseña (registro e inicio de sesión)
data class CredencialesState(
    val correo: String = "",
    val contrasena: String = "",
    val contrasenaVisible: Boolean = false,
    val errorCorreo: String? = null,
    val errorContrasena: String? = null,
    val enviando: Boolean = false,
    val sinConexion: AvisoSinConexion? = null, // por qué no se puede enviar ahora; null si se puede
    val mensaje: MensajeSesion? = null // se muestra una vez en un Snackbar
) {
    val puedeEnviar: Boolean
        get() = !enviando && sinConexion == null
}

// reintentable: el Snackbar ofrece volver a intentarlo
data class MensajeSesion(
    val texto: String,
    val reintentable: Boolean = false
)

// Mensajes de fallo que son iguales al registrarse y al iniciar sesión
object MensajesSesion {
    // El aviso de conexión ya explica la causa; esto cubre una conexión perdida a mitad del envío
    val sinConexion = MensajeSesion("Se perdió la conexión con el servidor. Intenta de nuevo cuando vuelva.")
    val sinRespuesta = MensajeSesion("El servidor no respondió a tiempo.", reintentable = true)
    val rechazado = MensajeSesion("El servidor no aceptó el correo o la contraseña. Revísalos.")
    val errorServidor = MensajeSesion(
        "El servidor tuvo un problema. Intenta de nuevo en un momento.",
        reintentable = true
    )
}
