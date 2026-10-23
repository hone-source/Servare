package com.spiralsoft.servare.ui.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.spiralsoft.servare.domain.repository.FichaRepository
import com.spiralsoft.servare.domain.repository.ResultadoEliminacion
import com.spiralsoft.servare.ui.viewModel.state.FichaListState

@HiltViewModel
class FichaListViewModel @Inject constructor(
    private val repository: FichaRepository
) : ViewModel() {

    private val mensaje = MutableStateFlow<String?>(null)

    val state: StateFlow<FichaListState> = combine(repository.observarResumenes(), mensaje) { fichas, mensaje ->
        FichaListState(cargando = false, fichas = fichas, mensaje = mensaje)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FichaListState())

    fun eliminar(entityId: String) {
        viewModelScope.launch {
            mensaje.value = when (val resultado = repository.eliminar(entityId)) {
                is ResultadoEliminacion.Eliminada ->
                    "«${resultado.nombre}» se eliminó del dispositivo; se eliminará del servidor al sincronizar"
                ResultadoEliminacion.NoEncontrada -> "Esta ficha ya no estaba en el dispositivo"
                ResultadoEliminacion.ErrorAlmacenamiento -> "No se pudo eliminar la ficha. Intenta de nuevo."
            }
        }
    }

    fun mensajeMostrado() {
        mensaje.value = null
    }
}
