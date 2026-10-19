package com.spiralsoft.servare.ui.viewModel.state

import java.time.LocalDate

data class FichaListState(
    val fichas: List<FichaResumen> = emptyList()
)

// Lo minimo para identificar una ficha en la lista
data class FichaResumen(
    val entityId: String,
    val nombre: String,
    val tipoEspacio: TipoEspacio?,
    val comunidad: String,
    val fechaLevantamiento: LocalDate?,
    val pendienteSincronizar: Boolean   // RF-08
)
