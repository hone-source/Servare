package com.spiralsoft.servare.ui.viewModel.state

import com.spiralsoft.servare.domain.model.FichaResumen

data class FichaListState(
    val cargando: Boolean = true,               // estado de carga de fichas
    val fichas: List<FichaResumen> = emptyList(),
    val cambiosPorResolver: Int = 0,            // FALLIDOS o en CONFLICTO: > 0 muestra el aviso
    val mensaje: String? = null                 // resultado de eliminar o de sincronizar, se muestra una vez
)
