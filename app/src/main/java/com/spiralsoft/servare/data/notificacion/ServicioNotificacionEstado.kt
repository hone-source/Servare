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
import com.spiralsoft.servare.domain.model.AvisoConectividad
import com.spiralsoft.servare.domain.model.EstadoConectividad
import com.spiralsoft.servare.domain.model.EstadoConectividad.CONECTADO
import com.spiralsoft.servare.domain.model.EstadoConectividad.RED_SIN_SERVIDOR
import com.spiralsoft.servare.domain.model.EstadoConectividad.SIN_RED

// Servicio de notificación de estado, traduce estados y errores a mensajes
// para el usuario. Solo observa al monitor, nunca comprueba la red por su cuenta
@Singleton
class ServicioNotificacionEstado @Inject constructor(
    connectivityMonitor: ConnectivityMonitor
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val conectividad: StateFlow<AvisoConectividad> = connectivityMonitor.estado
        .map(::traducir)
        .stateIn(scope, SharingStarted.Eagerly, traducir(connectivityMonitor.estado.value))

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
