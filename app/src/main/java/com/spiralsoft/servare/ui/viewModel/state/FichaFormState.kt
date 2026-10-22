package com.spiralsoft.servare.ui.viewModel.state

import com.spiralsoft.servare.domain.model.Ficha

data class FichaFormState(
    val entityId: String? = null, // null hasta el primer guardado
    val ficha: Ficha = Ficha(),
    val cargando: Boolean = false, // leyendo la ficha a editar
    val errorCarga: String? = null, // la ficha a editar no pudo leerse
    val errorNombre: String? = null,
    val guardando: Boolean = false,
    val guardada: Boolean = false,
    val mensaje: String? = null // se muestra una vez en un Snackbar
)

fun <T> List<T>.reemplazar(index: Int, item: T): List<T> =
    toMutableList().also { it[index] = item }

fun <T> List<T>.quitar(index: Int): List<T> =
    filterIndexed { i, _ -> i != index }
