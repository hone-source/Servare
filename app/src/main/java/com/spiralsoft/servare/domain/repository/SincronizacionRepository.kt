package com.spiralsoft.servare.domain.repository

import com.spiralsoft.servare.domain.model.CambioPorResolver
import kotlinx.coroutines.flow.Flow

/** Decisiones del usuario sobre los cambios que no se pudieron sincronizar. */
interface SincronizacionRepository {
    /** Cambios FALLIDOS o en CONFLICTO, en el orden en que se hicieron. */
    fun observarCambiosPorResolver(): Flow<List<CambioPorResolver>>

    /** FALLIDO: se vuelve a enviar igual. CONFLICTO: se conserva la versión del dispositivo. */
    suspend fun reintentar(operationId: String): ResultadoDecision

    /**
     * Se abandona el cambio y los que dependen de él; la ficha vuelve a la versión del servidor:
     * un registro nunca confirmado se quita del dispositivo, una eliminación devuelve la ficha
     * y una modificación se reemplaza en la siguiente descarga.
     */
    suspend fun descartar(operationId: String): ResultadoDecision
}

sealed interface ResultadoDecision {
    data object Hecha : ResultadoDecision
    /** El cambio ya no esperaba una decisión (se resolvió en otra pantalla o por otro toque). */
    data object YaResuelto : ResultadoDecision
    data object ErrorAlmacenamiento : ResultadoDecision
}
