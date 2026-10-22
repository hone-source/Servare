package com.spiralsoft.servare.ui.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.spiralsoft.servare.data.local.operation.OperationType
import com.spiralsoft.servare.domain.model.CambioPorResolver
import com.spiralsoft.servare.domain.model.MotivoProblema
import com.spiralsoft.servare.domain.repository.ResultadoDecision
import com.spiralsoft.servare.domain.repository.SincronizacionRepository
import com.spiralsoft.servare.notification.StatusNotifier
import com.spiralsoft.servare.ui.viewModel.state.CambioUi
import com.spiralsoft.servare.ui.viewModel.state.CambiosState

@HiltViewModel
class CambiosViewModel @Inject constructor(
    private val repository: SincronizacionRepository,
    private val notifier: StatusNotifier
) : ViewModel() {

    // Resultado de la última decisión (se muestra una vez)
    private val mensaje = MutableStateFlow<String?>(null)

    // Cambios con una decisión en curso; un segundo toque no la repite
    private val procesando = MutableStateFlow<Set<String>>(emptySet())

    val state: StateFlow<CambiosState> =
        combine(repository.observarCambiosPorResolver(), procesando, mensaje) { cambios, enCurso, mensaje ->
            CambiosState(
                cargando = false,
                cambios = cambios.map {
                    CambioUi(
                        cambio = it,
                        aviso = notifier.mensajeCambio(it),
                        confirmarDescarte = notifier.confirmacionDescarte(it),
                        procesando = it.operationId in enCurso
                    )
                },
                mensaje = mensaje
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CambiosState())

    fun reintentar(cambio: CambioPorResolver) = decidir(cambio) {
        when (repository.reintentar(cambio.operationId)) {
            ResultadoDecision.Hecha ->
                if (cambio.motivo == MotivoProblema.CONFLICTO) {
                    "Se conservará tu versión. Se enviará en cuanto haya conexión con el servidor."
                } else {
                    "El cambio volvió a la cola. Se enviará en cuanto haya conexión con el servidor."
                }
            ResultadoDecision.YaResuelto -> YA_RESUELTO
            ResultadoDecision.ErrorAlmacenamiento -> ERROR_ALMACENAMIENTO
        }
    }

    fun descartar(cambio: CambioPorResolver) = decidir(cambio) {
        when (repository.descartar(cambio.operationId)) {
            ResultadoDecision.Hecha -> when (cambio.tipo) {
                OperationType.CREATE -> "Cambio descartado. La ficha se quitó del dispositivo."
                OperationType.DELETE -> "Cambio descartado. La ficha volvió a la lista."
                else -> "Cambio descartado. Quedará la versión del servidor."
            }
            ResultadoDecision.YaResuelto -> YA_RESUELTO
            ResultadoDecision.ErrorAlmacenamiento -> ERROR_ALMACENAMIENTO
        }
    }

    fun mensajeMostrado() {
        mensaje.value = null
    }

    private fun decidir(cambio: CambioPorResolver, accion: suspend () -> String) {
        val id = cambio.operationId
        if (id in procesando.value) return
        procesando.update { it + id }
        viewModelScope.launch {
            mensaje.value = accion()
            procesando.update { it - id }
        }
    }

    private companion object {
        const val YA_RESUELTO = "Este cambio ya se había resuelto."
        const val ERROR_ALMACENAMIENTO = "No se pudo actualizar el cambio en el dispositivo. Intenta de nuevo."
    }
}
