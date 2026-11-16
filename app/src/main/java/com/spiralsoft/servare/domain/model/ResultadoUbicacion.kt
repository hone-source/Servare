package com.spiralsoft.servare.domain.model

sealed interface ResultadoUbicacion {
    data class Obtenida(val latitud: Double, val longitud: Double, val precisionMetros: Float?) : ResultadoUbicacion
    data object SinPermiso : ResultadoUbicacion
    // Los servicios de ubicación del dispositivo (o el proveedor que el permiso permite) están apagados
    data object UbicacionApagada : ResultadoUbicacion
    data object SinPosicion : ResultadoUbicacion
}
