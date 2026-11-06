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
import com.spiralsoft.servare.domain.repository.PuntoRepository
import com.spiralsoft.servare.domain.repository.ResultadoGuardadoPunto
import com.spiralsoft.servare.ui.viewModel.state.MapaState
import com.spiralsoft.servare.ui.viewModel.state.PuntoNuevo

@HiltViewModel
class MapaViewModel @Inject constructor(
    private val gestor: GestorMapaOffline,
    private val puntos: PuntoRepository
) : ViewModel() {

    val region = RegionMapa.ZACATECAS

    private val _state = MutableStateFlow(MapaState())
    val state: StateFlow<MapaState> = _state.asStateFlow()

    private var descarga: Job? = null

    init {
        viewModelScope.launch {
            val estado = runCatching { gestor.estado(region) }
                .getOrElse { EstadoRegionMapa.NoDescargada }
            _state.update { it.copy(region = estado) }
        }
        viewModelScope.launch {
            puntos.observarTodos()
                .catch { _state.update { it.copy(mensaje = "No se pudieron leer los puntos guardados.") } }
                .collect { lista -> _state.update { it.copy(puntos = lista) } }
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

    fun proponerPunto(latitud: Double, longitud: Double) =
        _state.update { it.copy(puntoNuevo = PuntoNuevo(latitud, longitud)) }

    fun cancelarPunto() = _state.update { it.copy(puntoNuevo = null) }

    fun guardarPunto(nombre: String) {
        val punto = _state.value.puntoNuevo ?: return
        viewModelScope.launch {
            when (val resultado = puntos.guardar(nombre, punto.latitud, punto.longitud)) {
                is ResultadoGuardadoPunto.Guardado ->
                    _state.update { it.copy(puntoNuevo = null, mensaje = "«${resultado.nombre}» se guardó en este dispositivo") }
                // El diálogo sigue abierto para completar el dato
                ResultadoGuardadoPunto.FaltaNombre ->
                    _state.update { it.copy(mensaje = "Escribe un nombre para el punto") }
                // El aviso se ve detrás del diálogo, así que se cierra: el usuario puede mantener pulsado otra vez
                ResultadoGuardadoPunto.SinEspacio ->
                    _state.update {
                        it.copy(puntoNuevo = null, mensaje = "No hay espacio en el dispositivo para guardar el punto. Libera espacio e inténtalo de nuevo")
                    }
                ResultadoGuardadoPunto.ErrorAlmacenamiento ->
                    _state.update { it.copy(puntoNuevo = null, mensaje = "No se pudo guardar el punto. Inténtalo de nuevo.") }
            }
        }
    }

    fun mensajeMostrado() = _state.update { it.copy(mensaje = null) }
}
