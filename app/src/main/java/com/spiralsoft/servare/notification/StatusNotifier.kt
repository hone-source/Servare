package com.spiralsoft.servare.notification

import com.spiralsoft.servare.data.connectivity.ConnectivityState
import javax.inject.Inject

/** Mensaje para el usuario: */
data class MensajeEstado(
    val titulo: String,
    val detalle: String
)

/**
 * Servicio de notificación de estado: traduce estados y errores a mensajes.
 * El ViewModel obtiene aquí el mensaje según el tipo de estado o fallo; nunca un error genérico.
 */
interface StatusNotifier {
    /** null = no hay nada que avisar (CONECTADO). */
    fun mensajeConectividad(estado: ConnectivityState): MensajeEstado?
}

class DefaultStatusNotifier @Inject constructor() : StatusNotifier {

    override fun mensajeConectividad(estado: ConnectivityState): MensajeEstado? = when (estado) {
        ConnectivityState.CONECTADO -> null
        ConnectivityState.SIN_RED -> MensajeEstado(
            titulo = "Sin conexión",
            detalle = "Trabajas con los datos guardados en el dispositivo. " +
                "Tus cambios se conservan y se enviarán cuando vuelva la conexión."
        )
        ConnectivityState.RED_SIN_SERVIDOR -> MensajeEstado(
            titulo = "Servidor no disponible",
            detalle = "Hay red, pero el servidor no responde. Trabajas con los datos del dispositivo " +
                "y se volverá a intentar automáticamente."
        )
    }
}
