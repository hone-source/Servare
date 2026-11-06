package com.spiralsoft.servare.ui.viewModel.state

import com.spiralsoft.servare.domain.model.EstadoRegionMapa
import com.spiralsoft.servare.domain.model.Punto

data class MapaState(
    val region: EstadoRegionMapa = EstadoRegionMapa.Comprobando,
    val puntos: List<Punto> = emptyList(),
    // Lugar mantenido pulsado que espera un nombre para guardarse
    val puntoNuevo: PuntoNuevo? = null,
    val mensaje: String? = null
)

data class PuntoNuevo(val latitud: Double, val longitud: Double)
