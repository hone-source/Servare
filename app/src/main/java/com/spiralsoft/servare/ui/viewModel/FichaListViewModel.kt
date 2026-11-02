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
import com.spiralsoft.servare.domain.model.AccionOperacion
import com.spiralsoft.servare.domain.repository.FichaRepository
import com.spiralsoft.servare.domain.repository.ResultadoEliminacionFicha
import com.spiralsoft.servare.notification.ServicioNotificacionEstado
import com.spiralsoft.servare.ui.viewModel.state.FichaListState

@HiltViewModel
class FichaListViewModel @Inject constructor(
    private val repository: FichaRepository,
    private val servicio: ServicioNotificacionEstado
) : ViewModel() {

    private val mensaje = MutableStateFlow<String?>(null)
    private val refrescando = MutableStateFlow(false)

    init {
        // Resultado de la sincronización: confirmaciones y fallos
        viewModelScope.launch {
            servicio.resultados.collect { mensaje.value = it.mensaje }
        }
    }

    val state: StateFlow<FichaListState> = combine(
        repository.observarResumenes(),
        repository.observarDescargaInicial(),
        servicio.descargaInicial,
        servicio.sesionExpirada,
        mensaje
    ) { fichas, descargaCompleta, avisoDescarga, sesionExpirada, mensaje ->
        FichaListState(
            cargando = false,
            fichas = fichas,
            // Con datos locales se muestran siempre, el aviso solo explica una lista vacía
            sinDescargar = avisoDescarga.takeIf { fichas.isEmpty() && !descargaCompleta },
            sesionExpirada = sesionExpirada,
            mensaje = mensaje
        )
    }.combine(servicio.problemasOperacion) { state, problemas ->
        state.copy(problemas = problemas)
    }.combine(refrescando) { state, refrescando ->
        state.copy(refrescando = refrescando)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FichaListState())

    fun eliminar(entityId: String) {
        viewModelScope.launch {
            mensaje.value = when (val resultado = repository.eliminar(entityId)) {
                is ResultadoEliminacionFicha.Eliminada ->
                    "«${resultado.nombre.ifBlank { "Sin nombre" }}» se eliminó del dispositivo. " +
                        "Se eliminará del servidor al sincronizar"
                ResultadoEliminacionFicha.NoEncontrada -> "Esta ficha ya no estaba en el dispositivo"
                ResultadoEliminacionFicha.ErrorAlmacenamiento -> "No se pudo eliminar la ficha. Intenta de nuevo."
            }
        }
    }

    fun refrescar() {
        if (!refrescando.compareAndSet(expect = false, update = true)) return
        viewModelScope.launch {
            try {
                mensaje.value = repository.refrescar().mensaje(servicio)
            } finally {
                refrescando.value = false
            }
        }
    }

    fun resolver(operationId: String, accion: AccionOperacion) {
        viewModelScope.launch {
            mensaje.value = repository.resolver(operationId, accion).mensaje()
        }
    }

    fun mensajeMostrado() {
        mensaje.value = null
    }
}
