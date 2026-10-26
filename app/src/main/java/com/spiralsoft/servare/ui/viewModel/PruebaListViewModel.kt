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
import com.spiralsoft.servare.data.notificacion.ServicioNotificacionEstado
import com.spiralsoft.servare.domain.repository.PruebaRepository
import com.spiralsoft.servare.domain.repository.ResultadoEliminacionPrueba
import com.spiralsoft.servare.ui.viewModel.state.PruebaListState

@HiltViewModel
class PruebaListViewModel @Inject constructor(
    private val repository: PruebaRepository,
    servicio: ServicioNotificacionEstado
) : ViewModel() {

    private val mensaje = MutableStateFlow<String?>(null)

    val state: StateFlow<PruebaListState> = combine(
        repository.observarTodas(),
        repository.observarDescargaInicial(),
        servicio.descargaInicial,
        servicio.sesionExpirada,
        mensaje
    ) { pruebas, descargaCompleta, avisoDescarga, sesionExpirada, mensaje ->
        PruebaListState(
            cargando = false,
            pruebas = pruebas,
            // Con datos locales se muestran siempre, el aviso solo explica una lista vacía
            sinDescargar = avisoDescarga.takeIf { pruebas.isEmpty() && !descargaCompleta },
            sesionExpirada = sesionExpirada,
            mensaje = mensaje
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PruebaListState())

    fun eliminar(entityId: String) {
        viewModelScope.launch {
            mensaje.value = when (val resultado = repository.eliminar(entityId)) {
                is ResultadoEliminacionPrueba.Eliminada ->
                    "«${resultado.titulo}» se eliminó del dispositivo; se eliminará del servidor al sincronizar"
                ResultadoEliminacionPrueba.NoEncontrada -> "Este registro ya no estaba en el dispositivo"
                ResultadoEliminacionPrueba.ErrorAlmacenamiento -> "No se pudo eliminar el registro. Intenta de nuevo."
            }
        }
    }

    fun mensajeMostrado() {
        mensaje.value = null
    }
}
