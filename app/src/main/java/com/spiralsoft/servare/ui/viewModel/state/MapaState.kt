package com.spiralsoft.servare.ui.viewModel.state

import com.spiralsoft.servare.domain.model.AvisoDescargaInicial
import com.spiralsoft.servare.domain.model.AvisoOperacion
import com.spiralsoft.servare.domain.model.AvisoSesionExpirada
import com.spiralsoft.servare.domain.model.EstadoRegionMapa
import com.spiralsoft.servare.domain.model.Punto

data class MapaState(
    val region: EstadoRegionMapa = EstadoRegionMapa.Comprobando,
    val puntos: List<Punto> = emptyList(),
    // Lugar mantenido pulsado que espera un nombre para guardarse
    val puntoNuevo: PuntoNuevo? = null,
    // Diálogo abierto sobre un punto guardado
    val dialogo: DialogoPunto? = null,
    // Aún no se descargaron los puntos del servidor
    val descargaPendiente: AvisoDescargaInicial? = null,
    val sesionExpirada: AvisoSesionExpirada? = null, // se trabaja con lo guardado, pero nada se envía
    val problemas: List<AvisoOperacion> = emptyList(), // cambios FALLIDA, CONFLICTO o BLOQUEADA, hasta que se resuelvan
    val refrescando: Boolean = false, // el usuario pidió comprobar los cambios del servidor
    val mensaje: String? = null
)

data class PuntoNuevo(val latitud: Double, val longitud: Double)

// Solo el id: la pantalla busca el punto en la lista, así siempre muestra sus datos vigentes
sealed interface DialogoPunto {
    val entityId: String

    data class Detalle(override val entityId: String) : DialogoPunto
    data class Editar(override val entityId: String) : DialogoPunto
    data class ConfirmarEliminar(override val entityId: String) : DialogoPunto
}
