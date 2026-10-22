package com.spiralsoft.servare.data.sync

import com.spiralsoft.servare.data.local.operation.OperationType

/**
 * Resultado que el motor de sincronización entrega al servicio de notificación de estado.
 * Cada salida del procesamiento termina en uno de estos; no hay salida silenciosa.
 */
sealed interface ResultadoSync {
    /** Operaciones que el servidor confirmó en un ciclo. */
    data class Sincronizadas(val cantidad: Int) : ResultadoSync

    /** El servidor tiene otra versión; la local se conserva. */
    data class Conflicto(val nombre: String?, val tipo: OperationType) : ResultadoSync

    /** [porIntentos]: se agotaron los intentos automáticos; si no, el servidor la rechazó por [motivo]. */
    data class Fallida(
        val nombre: String?,
        val tipo: OperationType,
        val motivo: String?,
        val porIntentos: Boolean
    ) : ResultadoSync

    /** 401/403: la cola quedó en pausa con sus operaciones PENDIENTE. */
    data object SesionRequerida : ResultadoSync

    /** El pull no pudo completarse con el servidor alcanzable; se reintenta en el siguiente ciclo. */
    data object DescargaFallida : ResultadoSync

    /** El pull quitó del dispositivo fichas que ya no existen en el servidor ([nombres], una por ficha). */
    data class EliminadasEnServidor(val nombres: List<String?>) : ResultadoSync
}
