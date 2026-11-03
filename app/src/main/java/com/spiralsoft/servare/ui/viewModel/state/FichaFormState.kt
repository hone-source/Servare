package com.spiralsoft.servare.ui.viewModel.state

import com.spiralsoft.servare.domain.model.Ficha

data class FichaFormState(
    val entityId: String? = null, // null al crear una ficha nueva
    val ficha: Ficha = Ficha(),
    val fichaOriginal: Ficha = Ficha(), // la que se cargó al abrir; base de hayCambios
    val cargando: Boolean = false, // leyendo la ficha a editar
    val errorCarga: String? = null, // la ficha a editar no pudo leerse
    val errorNombre: String? = null,
    val guardando: Boolean = false,
    val mensaje: String? = null, // se muestra una vez en un Snackbar
    val mensajeSalida: String? = null // al fijarse se cierra el formulario y se muestra en la lista
) {
    val hayCambios: Boolean get() = ficha != fichaOriginal
}
