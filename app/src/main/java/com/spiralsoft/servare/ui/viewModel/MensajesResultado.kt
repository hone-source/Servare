package com.spiralsoft.servare.ui.viewModel

import com.spiralsoft.servare.domain.model.AccionOperacion
import com.spiralsoft.servare.domain.repository.ResultadoRefresco
import com.spiralsoft.servare.domain.repository.ResultadoResolucion
import com.spiralsoft.servare.notification.ServicioNotificacionEstado

// Lo que se le dice al usuario del resultado de resolver un cambio o de refrescar la lista.
// Es igual en todas las listas

internal fun ResultadoResolucion.mensaje(): String = when (this) {
    is ResultadoResolucion.Resuelta -> when (accion) {
        AccionOperacion.REINTENTAR -> "Se volverá a intentar el envío"
        AccionOperacion.RECREAR -> "El registro se volverá a crear en el servidor"
        AccionOperacion.CONSERVAR_MIA -> "Tu versión se enviará al servidor"
        AccionOperacion.ACEPTAR_SERVIDOR -> "Se aplicó la versión del servidor"
        AccionOperacion.DESCARTAR -> "El cambio se descartó"
    }
    ResultadoResolucion.NoDisponible -> "Este cambio ya se resolvió o cambió de estado"
    ResultadoResolucion.ErrorAlmacenamiento -> "No se pudo resolver el cambio. Intenta de nuevo."
}

internal fun ResultadoRefresco.mensaje(servicio: ServicioNotificacionEstado): String = when (this) {
    is ResultadoRefresco.Actualizada ->
        if (eliminadas.isEmpty()) "Lista actualizada"
        else servicio.textoEliminadosEnServidor(eliminadas)
    ResultadoRefresco.SinSesion -> "Inicia sesión para actualizar"
    ResultadoRefresco.SinServidor -> "No se pudo conectar con el servidor. Se actualizará al recuperar la conexión"
    ResultadoRefresco.ErrorDescarga -> "No se pudieron descargar los registros. Intenta de nuevo."
}
