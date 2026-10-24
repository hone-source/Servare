package com.spiralsoft.servare.ui.viewModel.state

data class PruebaFormState(
    val titulo: String = "",
    val texto: String = "",
    val numero: String = "", // tal como lo escribe el usuario, se valida al guardar
    val errorTitulo: String? = null,
    val errorNumero: String? = null,
    val guardando: Boolean = false,
    val guardada: Boolean = false,
    val mensaje: String? = null // se muestra una vez en un Snackbar
) {
    val hayCambios: Boolean get() = titulo.isNotEmpty() || texto.isNotEmpty() || numero.isNotEmpty()
}
