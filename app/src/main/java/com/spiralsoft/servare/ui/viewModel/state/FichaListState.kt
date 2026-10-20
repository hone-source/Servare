package com.spiralsoft.servare.ui.viewModel.state

import com.spiralsoft.servare.data.model.FichaResumen

data class FichaListState(
    val cargando: Boolean = true,       // evita mostrar "no hay fichas" antes de leer Room
    val fichas: List<FichaResumen> = emptyList()
)
