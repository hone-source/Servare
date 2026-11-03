package com.spiralsoft.servare.data.remote

// Resultado de descargar los registros del servidor, el motor decide qué hacer con cada uno
sealed interface ResultadoPull<out T> {
    data class Datos<T>(val registros: List<T>) : ResultadoPull<T>
    // 401/403: pausa la cola, igual que en el push
    data object SesionExpirada : ResultadoPull<Nothing>
    // Sin conexión con el servidor
    data object SinServidor : ResultadoPull<Nothing>
    // 5xx, timeout, respuesta ilegible: no hay operación a la que cargarlo, se vuelve a intentar en el siguiente ciclo
    data class Fallo(val motivo: String) : ResultadoPull<Nothing>
}
