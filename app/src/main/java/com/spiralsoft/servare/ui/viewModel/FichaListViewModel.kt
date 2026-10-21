package com.spiralsoft.servare.ui.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.spiralsoft.servare.data.connectivity.ConnectivityState
import com.spiralsoft.servare.data.repository.ConectividadRepository
import com.spiralsoft.servare.data.repository.FichaRepository
import com.spiralsoft.servare.data.repository.ResultadoEliminacion
import com.spiralsoft.servare.notification.StatusNotifier
import com.spiralsoft.servare.ui.viewModel.state.FichaListState

@HiltViewModel
class FichaListViewModel @Inject constructor(
    private val repository: FichaRepository,
    conectividad: ConectividadRepository,
    notifier: StatusNotifier
) : ViewModel() {

    // Al abrir la app el estado pasa por SIN_RED y RED_SIN_SERVIDOR mientras se confirma el servidor;
    // se espera un momento antes de avisar para no mostrar una franja que dura una fracción de segundo
    @OptIn(FlowPreview::class)
    private val avisoConexion = conectividad.estado
        .debounce { if (it == ConnectivityState.CONECTADO) 0L else 2_000L }
        .map(notifier::mensajeConectividad)
        .onStart { emit(null) }

    // Resultado de eliminar (se muestra una vez)
    private val mensaje = MutableStateFlow<String?>(null)

    val state: StateFlow<FichaListState> =
        combine(repository.observarResumenes(), avisoConexion, mensaje) { fichas, aviso, mensaje ->
            FichaListState(cargando = false, fichas = fichas, avisoConexion = aviso, mensaje = mensaje)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FichaListState())

    fun eliminar(entityId: String) {
        viewModelScope.launch {
            mensaje.value = when (repository.eliminar(entityId)) {
                ResultadoEliminacion.Eliminada -> "Ficha eliminada"
                ResultadoEliminacion.NoEncontrada -> "Esta ficha ya no estaba en el dispositivo."
                ResultadoEliminacion.ErrorAlmacenamiento ->
                    "No se pudo eliminar la ficha del dispositivo. Intenta de nuevo."
            }
        }
    }

    fun mensajeMostrado() {
        mensaje.value = null
    }
}
