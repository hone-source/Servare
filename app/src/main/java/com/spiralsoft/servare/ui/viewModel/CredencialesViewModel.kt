package com.spiralsoft.servare.ui.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import com.spiralsoft.servare.domain.model.AvisoSinConexion
import com.spiralsoft.servare.ui.viewModel.state.CredencialesState

// Base de los ViewModels que piden correo y contraseña. La contraseña nunca va a SavedStateHandle
// ni a un Bundle. sinConexion lo explica el servicio de notificación
abstract class CredencialesViewModel(
    sinConexion: Flow<AvisoSinConexion?>
) : ViewModel() {

    protected val formulario = MutableStateFlow(CredencialesState())

    val state: StateFlow<CredencialesState> = combine(formulario, sinConexion) { form, aviso ->
        form.copy(sinConexion = aviso)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CredencialesState())

    // El usuario cambió lo escrito
    protected open fun alEditar() {}

    // Cada error desaparece en cuanto el usuario corrige su campo
    fun updateCorreo(correo: String) {
        alEditar()
        formulario.update { it.copy(correo = correo, errorCorreo = null) }
    }

    fun updateContrasena(contrasena: String) {
        alEditar()
        formulario.update { it.copy(contrasena = contrasena, errorContrasena = null) }
    }

    fun cambiarVisibilidad() = formulario.update { it.copy(contrasenaVisible = !it.contrasenaVisible) }

    fun mensajeMostrado() = formulario.update { it.copy(mensaje = null) }

    abstract fun enviar()
}
