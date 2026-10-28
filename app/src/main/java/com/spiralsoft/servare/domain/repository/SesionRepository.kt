package com.spiralsoft.servare.domain.repository

import kotlinx.coroutines.flow.StateFlow
import com.spiralsoft.servare.domain.model.EstadoConectividad
import com.spiralsoft.servare.domain.model.EstadoSesion

const val LONGITUD_MINIMA_CONTRASENA = 8

// Registrarse e iniciar sesión no pasan por la cola
interface SesionRepository {
    val estado: StateFlow<EstadoSesion>

    // claveRegistro es la misma en todos los reintentos de un mismo registro, así el servidor
    // reconoce el reintento en vez de rechazarlo como duplicado
    suspend fun registrar(correo: String, contrasena: String, claveRegistro: String): ResultadoRegistro

    suspend fun iniciarSesion(correo: String, contrasena: String): ResultadoInicioSesion

    // Cambios que el servidor aún no confirma y que cerrar sesión perdería. null si no se pudo comprobar
    suspend fun contarCambiosSinEnviar(): Int?

    // Funciona sin conexión. Borra los datos del dispositivo, incluida la cola, y después la sesión:
    // quien lo pide debe haber avisado de los cambios sin enviar
    suspend fun cerrarSesion(): ResultadoCierreSesion
}

sealed interface ResultadoCierreSesion {
    data object Cerrada : ResultadoCierreSesion
    // Nada se perdió a medias: la sesión sigue abierta y se puede reintentar
    data object ErrorAlmacenamiento : ResultadoCierreSesion
}

sealed interface ResultadoRegistro {
    data object Registrado : ResultadoRegistro
    data object CorreoInvalido : ResultadoRegistro
    data object ContrasenaCorta : ResultadoRegistro
    data object CorreoYaRegistrado : ResultadoRegistro
    // No se lanzó la solicitud (o se perdió la conexión durante ella): estado en el que quedó el monitor
    data class SinConexion(val estado: EstadoConectividad) : ResultadoRegistro
    data object SinRespuesta : ResultadoRegistro      // timeout
    data object Rechazado : ResultadoRegistro         // el servidor no aceptó los datos
    data object ErrorServidor : ResultadoRegistro
    // La cuenta existe, pero la sesión no se pudo guardar en el dispositivo. Reintentar es seguro
    data object ErrorAlmacenamiento : ResultadoRegistro
}

sealed interface ResultadoInicioSesion {
    data object Autenticado : ResultadoInicioSesion
    data object CorreoInvalido : ResultadoInicioSesion
    data object FaltaContrasena : ResultadoInicioSesion
    // El servidor no dice si falló el correo o la contraseña, la app tampoco
    data object CredencialesInvalidas : ResultadoInicioSesion
    // No se lanzó la solicitud (o se perdió la conexión durante ella): estado en el que quedó el monitor
    data class SinConexion(val estado: EstadoConectividad) : ResultadoInicioSesion
    data object SinRespuesta : ResultadoInicioSesion  // timeout
    data object Rechazado : ResultadoInicioSesion     // el servidor no aceptó los datos
    data object ErrorServidor : ResultadoInicioSesion
    // Las credenciales son correctas, pero la sesión no se pudo guardar en el dispositivo
    data object ErrorAlmacenamiento : ResultadoInicioSesion
}
