package com.spiralsoft.servare.ui.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.spiralsoft.servare.domain.model.correoActual
import com.spiralsoft.servare.domain.repository.ResultadoCierreSesion
import com.spiralsoft.servare.domain.repository.SesionRepository
import com.spiralsoft.servare.ui.viewModel.state.CierreSesionState
import com.spiralsoft.servare.ui.viewModel.state.ConfirmacionCierre

@HiltViewModel
class CierreSesionViewModel @Inject constructor(
    private val repository: SesionRepository
) : ViewModel() {

    val correo: StateFlow<String?> = repository.estado
        .map { it.correoActual }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            repository.estado.value.correoActual
        )

    private val _state = MutableStateFlow(CierreSesionState())
    val state: StateFlow<CierreSesionState> = _state.asStateFlow()

    // Cerrar sesión borra los datos del dispositivo: antes se cuentan los cambios que se perderían
    fun solicitar() {
        val actual = _state.value
        if (actual.confirmacion != null || actual.cerrando) return
        viewModelScope.launch {
            val cambios = repository.contarCambiosSinEnviar()
            _state.update { it.copy(confirmacion = ConfirmacionCierre(cambios)) }
        }
    }

    fun cancelar() {
        if (_state.value.cerrando) return
        _state.value = CierreSesionState()
    }

    fun confirmar() {
        if (_state.value.cerrando) return
        _state.update { it.copy(cerrando = true, error = null) }
        viewModelScope.launch {
            when (repository.cerrarSesion()) {
                // La navegación la hace el estado de la sesión, se deja cerrando para no repetirlo
                ResultadoCierreSesion.Cerrada -> Unit
                ResultadoCierreSesion.ErrorAlmacenamiento -> _state.update {
                    it.copy(cerrando = false, error = "No se pudo cerrar la sesión. Intenta de nuevo.")
                }
            }
        }
    }
}
