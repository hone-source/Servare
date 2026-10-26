package com.spiralsoft.servare.data.notificacion

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton
import com.spiralsoft.servare.data.connectivity.ConnectivityMonitor
import com.spiralsoft.servare.data.sesion.GestorSesion
import com.spiralsoft.servare.domain.model.AvisoConectividad
import com.spiralsoft.servare.domain.model.AvisoDescargaInicial
import com.spiralsoft.servare.domain.model.AvisoSesionExpirada
import com.spiralsoft.servare.domain.model.AvisoSinConexion
import com.spiralsoft.servare.domain.model.EstadoSesion
import com.spiralsoft.servare.domain.model.EstadoConectividad
import com.spiralsoft.servare.domain.model.EstadoConectividad.CONECTADO
import com.spiralsoft.servare.domain.model.EstadoConectividad.RED_SIN_SERVIDOR
import com.spiralsoft.servare.domain.model.EstadoConectividad.SIN_RED

// Servicio de notificación de estado, traduce estados y errores a mensajes
// para el usuario. Solo observa al monitor, nunca comprueba la red por su cuenta
@Singleton
class ServicioNotificacionEstado @Inject constructor(
    private val connectivityMonitor: ConnectivityMonitor,
    gestorSesion: GestorSesion
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val conectividad: StateFlow<AvisoConectividad> = connectivityMonitor.estado
        .map(::traducir)
        .stateIn(scope, SharingStarted.Eagerly, traducir(connectivityMonitor.estado.value))

    // La causa depende del estado de red; la app lo reintenta sola en cada caso
    val descargaInicial: StateFlow<AvisoDescargaInicial> = connectivityMonitor.estado
        .map(::traducirDescargaInicial)
        .stateIn(scope, SharingStarted.Eagerly, traducirDescargaInicial(connectivityMonitor.estado.value))

    // null con la sesión en orden. Con la sesión expirada la app sigue funcionando con lo guardado,
    // pero la cola está pausada, el aviso lo dice y pide iniciar sesión
    val sesionExpirada: StateFlow<AvisoSesionExpirada?> = gestorSesion.estado
        .map(::traducirSesion)
        .stateIn(scope, SharingStarted.Eagerly, traducirSesion(gestorSesion.estado.value))

    private fun traducirSesion(estado: EstadoSesion): AvisoSesionExpirada? =
        (estado as? EstadoSesion.Expirada)?.let {
            AvisoSesionExpirada(
                titulo = "Tu sesión expiró",
                detalle = "Puedes seguir trabajando: tus cambios se guardan en el dispositivo, " +
                    "pero no se enviarán hasta que inicies sesión de nuevo con ${it.correo}."
            )
        }

    // Registrarse e iniciar sesión requieren servidor: null cuando se puede, el aviso con la causa cuando no
    val registroNoDisponible: StateFlow<AvisoSinConexion?> =
        requiereConexion(accion = "Crear la cuenta", verbo = "registrarte")

    val inicioSesionNoDisponible: StateFlow<AvisoSinConexion?> =
        requiereConexion(accion = "Iniciar sesión", verbo = "iniciar sesión")

    private fun requiereConexion(accion: String, verbo: String): StateFlow<AvisoSinConexion?> =
        connectivityMonitor.estado
            .map { traducirRequiereConexion(it, accion, verbo) }
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                traducirRequiereConexion(connectivityMonitor.estado.value, accion, verbo)
            )

    private fun traducirRequiereConexion(estado: EstadoConectividad, accion: String, verbo: String) =
        when (estado) {
            SIN_RED -> AvisoSinConexion(
                titulo = "$accion requiere conexión",
                detalle = "El dispositivo no tiene red. Conéctate a internet para $verbo."
            )
            RED_SIN_SERVIDOR -> AvisoSinConexion(
                titulo = "$accion requiere conexión",
                detalle = "Hay red, pero el servidor no responde. Podrás $verbo en cuanto responda."
            )
            CONECTADO -> null
        }

    private fun traducirDescargaInicial(estado: EstadoConectividad) = AvisoDescargaInicial(
        titulo = "Aún no se descargaron los registros",
        detalle = when (estado) {
            SIN_RED -> "El dispositivo no tiene red. Se descargarán al recuperar la conexión."
            RED_SIN_SERVIDOR -> "Hay red, pero el servidor no responde. Se descargarán cuando responda."
            CONECTADO -> "Se están solicitando al servidor. Si no aparecen, se volverá a intentar automáticamente."
        }
    )

    private fun traducir(estado: EstadoConectividad) = when (estado) {
        SIN_RED -> AvisoConectividad(
            estado,
            titulo = "Sin conexión",
            detalle = "El dispositivo no tiene red. Puedes seguir trabajando: " +
                "tus cambios se guardan en el dispositivo y se enviarán al recuperar la conexión."
        )
        RED_SIN_SERVIDOR -> AvisoConectividad(
            estado,
            titulo = "Servidor no disponible",
            detalle = "Hay red, pero el servidor no responde. Tus cambios se guardan en el " +
                "dispositivo; se volverá a intentar automáticamente."
        )
        CONECTADO -> AvisoConectividad(
            estado,
            titulo = "Conectado",
            detalle = "El servidor responde. Tus cambios se sincronizan."
        )
    }
}
