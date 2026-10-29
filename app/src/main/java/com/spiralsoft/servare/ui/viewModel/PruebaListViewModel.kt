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
import com.spiralsoft.servare.notification.ServicioNotificacionEstado
import com.spiralsoft.servare.domain.repository.PruebaRepository
import com.spiralsoft.servare.domain.repository.ResultadoEliminacionPrueba
import com.spiralsoft.servare.domain.repository.ResultadoRefresco
import com.spiralsoft.servare.domain.repository.ResultadoResolucion
import com.spiralsoft.servare.ui.viewModel.state.PruebaListState

@HiltViewModel
class PruebaListViewModel @Inject constructor(
    private val repository: PruebaRepository,
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
    }.combine(servicio.problemasOperacion) { state, problemas ->
        state.copy(problemas = problemas)
    }.combine(refrescando) { state, refrescando ->
        state.copy(refrescando = refrescando)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PruebaListState())

    fun eliminar(entityId: String) {
        viewModelScope.launch {
            mensaje.value = when (val resultado = repository.eliminar(entityId)) {
                is ResultadoEliminacionPrueba.Eliminada ->
                    "«${resultado.titulo}» se eliminó del dispositivo. Se eliminará del servidor al sincronizar"
                ResultadoEliminacionPrueba.NoEncontrada -> "Este registro ya no estaba en el dispositivo"
                ResultadoEliminacionPrueba.ErrorAlmacenamiento -> "No se pudo eliminar el registro. Intenta de nuevo."
            }
        }
    }

    fun refrescar() {
        if (!refrescando.compareAndSet(expect = false, update = true)) return
        viewModelScope.launch {
            try {
                mensaje.value = when (val resultado = repository.refrescar()) {
                    is ResultadoRefresco.Actualizada ->
                        if (resultado.eliminadas.isEmpty()) "Lista actualizada"
                        else servicio.textoEliminadosEnServidor(resultado.eliminadas)
                    ResultadoRefresco.SinSesion -> "Inicia sesión para actualizar"
                    ResultadoRefresco.SinServidor ->
                        "No se pudo conectar con el servidor. Se actualizará al recuperar la conexión"
                    ResultadoRefresco.ErrorDescarga -> "No se pudieron descargar los registros. Intenta de nuevo."
                }
            } finally {
                refrescando.value = false
            }
        }
    }

    fun resolver(operationId: String, accion: AccionOperacion) {
        viewModelScope.launch {
            mensaje.value = when (val resultado = repository.resolver(operationId, accion)) {
                is ResultadoResolucion.Resuelta -> when (resultado.accion) {
                    AccionOperacion.REINTENTAR -> "Se volverá a intentar el envío"
                    AccionOperacion.RECREAR -> "El registro se volverá a crear en el servidor"
                    AccionOperacion.CONSERVAR_MIA -> "Tu versión se enviará al servidor"
                    AccionOperacion.ACEPTAR_SERVIDOR -> "Se aplicó la versión del servidor"
                    AccionOperacion.DESCARTAR -> "El cambio se descartó"
                }
                ResultadoResolucion.NoDisponible -> "Este cambio ya se resolvió o cambió de estado"
                ResultadoResolucion.ErrorAlmacenamiento -> "No se pudo resolver el cambio. Intenta de nuevo."
            }
        }
    }

    fun mensajeMostrado() {
        mensaje.value = null
    }
}
