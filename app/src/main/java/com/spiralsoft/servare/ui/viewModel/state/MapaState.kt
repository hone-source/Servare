package com.spiralsoft.servare.ui.viewModel.state

import com.spiralsoft.servare.domain.model.EstadoRegionMapa

data class MapaState(
    val region: EstadoRegionMapa = EstadoRegionMapa.Comprobando,
    val mensaje: String? = null
)
