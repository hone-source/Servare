package com.spiralsoft.servare.data.remote

// Resultado de descargar los registros del servidor, el motor decide qué hacer con cada uno
sealed interface ResultadoPull {
    data class Datos(val pruebas: List<PruebaRemota>) : ResultadoPull
    // 401/403: pausa la cola, igual que en el push
    data object SesionExpirada : ResultadoPull
    // Sin conexión con el servidor
    data object SinServidor : ResultadoPull
    // 5xx, timeout, respuesta ilegible: no hay operación a la que cargarlo, se vuelve a intentar en el siguiente ciclo
    data class Fallo(val motivo: String) : ResultadoPull
}
