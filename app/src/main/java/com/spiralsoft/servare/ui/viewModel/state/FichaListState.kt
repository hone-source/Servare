package com.spiralsoft.servare.ui.viewModel.state

import com.spiralsoft.servare.data.model.FichaResumen
import com.spiralsoft.servare.notification.MensajeEstado

data class FichaListState(
    val cargando: Boolean = true,       // evita mostrar "no hay fichas" antes de leer Room
    val fichas: List<FichaResumen> = emptyList(),
    val avisoConexion: MensajeEstado? = null,   // null = CONECTADO, nada que avisar
    val mensaje: String? = null                 // resultado de eliminar, se muestra una vez
)
