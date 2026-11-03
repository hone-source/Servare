package com.spiralsoft.servare.ui.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.spiralsoft.servare.domain.model.AccionOperacion
import com.spiralsoft.servare.domain.repository.RepositorioSincronizable
import com.spiralsoft.servare.notification.ServicioNotificacionEstado
import com.spiralsoft.servare.ui.viewModel.state.ListaState

// Lo común a la lista de cualquier tipo de registro:
// avisos de sincronización, refresco manual y resolución de cambios con problema.
// Cada tipo aporta qué lista observa y cómo se elimina un registro
abstract class ListaSincronizableViewModel<T>(
    private val repositorio: RepositorioSincronizable,
    private val servicio: ServicioNotificacionEstado,
    registros: Flow<List<T>>
) : ViewModel() {

    protected val mensaje = MutableStateFlow<String?>(null)
    private val refrescando = MutableStateFlow(false)

    init {
        // Resultado de la sincronización: confirmaciones y fallos
        viewModelScope.launch {
            servicio.resultados.collect { mensaje.value = it.mensaje }
        }
    }

    val state: StateFlow<ListaState<T>> = combine(
        registros,
        repositorio.observarDescargaInicial(),
        servicio.descargaInicial,
        servicio.sesionExpirada,
        mensaje
    ) { lista, descargaCompleta, avisoDescarga, sesionExpirada, mensaje ->
        ListaState(
            cargando = false,
            registros = lista,
            // Con datos locales se muestran siempre, el aviso solo explica una lista vacía
            sinDescargar = avisoDescarga.takeIf { lista.isEmpty() && !descargaCompleta },
            sesionExpirada = sesionExpirada,
            mensaje = mensaje
        )
    }.combine(servicio.problemasOperacion) { state, problemas ->
        state.copy(problemas = problemas)
    }.combine(refrescando) { state, refrescando ->
        state.copy(refrescando = refrescando)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListaState())

    fun refrescar() {
        if (!refrescando.compareAndSet(expect = false, update = true)) return
        viewModelScope.launch {
            try {
                mensaje.value = repositorio.refrescar().mensaje(servicio)
            } finally {
                refrescando.value = false
            }
        }
    }

    fun resolver(operationId: String, accion: AccionOperacion) {
        viewModelScope.launch {
            mensaje.value = repositorio.resolver(operationId, accion).mensaje()
        }
    }

    fun mensajeMostrado() {
        mensaje.value = null
    }
}
