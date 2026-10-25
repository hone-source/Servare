package com.spiralsoft.servare.ui.viewModel.state

data class PruebaFormState(
    val editando: Boolean = false,
    val cargando: Boolean = false, // leyendo el registro a editar
    val titulo: String = "",
    val texto: String = "",
    val numero: String = "", // tal como lo escribe el usuario, se valida al guardar
    // Valores al abrir el formulario: vacíos al crear, los del registro al editar
    val tituloOriginal: String = "",
    val textoOriginal: String = "",
    val numeroOriginal: String = "",
    val errorTitulo: String? = null,
    val errorNumero: String? = null,
    val guardando: Boolean = false,
    val mensaje: String? = null, // se muestra una vez en un Snackbar
    val mensajeSalida: String? = null // al fijarse se cierra el formulario y se muestra en la lista
) {
    val hayCambios: Boolean
        get() = titulo != tituloOriginal || texto != textoOriginal || numero != numeroOriginal
}
