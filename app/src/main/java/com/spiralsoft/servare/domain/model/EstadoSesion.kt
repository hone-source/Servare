package com.spiralsoft.servare.domain.model

// Estado de la sesión del usuario. El token nunca sale de la capa de datos
sealed interface EstadoSesion {
    // Leyendo la sesión guardada en el dispositivo
    data object Cargando : EstadoSesion
    data object SinSesion : EstadoSesion
    data class ConSesion(val correo: String) : EstadoSesion

    // El token ya no vale (expiró o el servidor lo rechazó). La app sigue funcionando con lo guardado,
    // pero la cola de sincronización está pausada hasta iniciar sesión de nuevo con la misma cuenta
    data class Expirada(val correo: String) : EstadoSesion
}

// Cuenta de la sesión, aunque haya expirado. null si no hay ninguna
val EstadoSesion.correoActual: String?
    get() = when (this) {
        is EstadoSesion.ConSesion -> correo
        is EstadoSesion.Expirada -> correo
        EstadoSesion.Cargando, EstadoSesion.SinSesion -> null
    }
