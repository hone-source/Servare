package com.spiralsoft.servare.ui.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.spiralsoft.servare.data.mapa.GestorMapaOffline
import com.spiralsoft.servare.domain.model.EstadoRegionMapa
import com.spiralsoft.servare.domain.model.RegionMapa
import com.spiralsoft.servare.ui.viewModel.state.MapaState

@HiltViewModel
class MapaViewModel @Inject constructor(
    private val gestor: GestorMapaOffline
) : ViewModel() {

    val region = RegionMapa.TLAXCALA

    private val _state = MutableStateFlow(MapaState())
    val state: StateFlow<MapaState> = _state.asStateFlow()

    private var descarga: Job? = null

    init {
        viewModelScope.launch {
            val estado = runCatching { gestor.estado(region) }
                .getOrElse { EstadoRegionMapa.NoDescargada }
            _state.update { it.copy(region = estado) }
        }
    }

    fun descargar() {
        if (descarga?.isActive == true) return
        _state.update { it.copy(region = EstadoRegionMapa.Descargando(0, 0)) }
        descarga = viewModelScope.launch {
            gestor.descargar(region)
                .catch { error ->
                    if (error is CancellationException) throw error
                    // Lo ya descargado se conserva: se puede continuar desde ahí
                    val estado = runCatching { gestor.estado(region) }.getOrElse { EstadoRegionMapa.NoDescargada }
                    _state.update {
                        it.copy(
                            region = estado,
                            mensaje = "No se pudo descargar ${region.nombre}. Revisa tu conexión e inténtalo de nuevo"
                        )
                    }
                }
                .collect { estado -> _state.update { it.copy(region = estado) } }
        }
    }

    fun eliminar() {
        descarga?.cancel()
        viewModelScope.launch {
            val mensaje = runCatching { gestor.eliminar(region) }.fold(
                onSuccess = { "Se quitó el mapa de ${region.nombre} de este dispositivo." },
                onFailure = { "No se pudo quitar el mapa de ${region.nombre}. Inténtalo de nuevo." }
            )
            val estado = runCatching { gestor.estado(region) }.getOrElse { EstadoRegionMapa.NoDescargada }
            _state.update { it.copy(region = estado, mensaje = mensaje) }
        }
    }

    fun mensajeMostrado() = _state.update { it.copy(mensaje = null) }
}
