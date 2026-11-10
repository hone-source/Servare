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
import com.spiralsoft.servare.domain.repository.ResultadoEliminacionPunto
import com.spiralsoft.servare.domain.repository.ResultadoGuardadoPunto
import com.spiralsoft.servare.ui.viewModel.state.DialogoPunto
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
                // Las coordenadas salen del mapa, así que no deberían salirse de rango,
                // si ocurre, se cierra con aviso
                ResultadoGuardadoPunto.CoordenadasInvalidas, ResultadoGuardadoPunto.NoEncontrado ->
                    _state.update { it.copy(puntoNuevo = null, mensaje = "No se pudo guardar el punto. Inténtalo de nuevo.") }
            }
        }
    }

    fun verPunto(entityId: String) = _state.update { it.copy(dialogo = DialogoPunto.Detalle(entityId)) }

    fun editarPunto(entityId: String) = _state.update { it.copy(dialogo = DialogoPunto.Editar(entityId)) }

    fun pedirEliminarPunto(entityId: String) =
        _state.update { it.copy(dialogo = DialogoPunto.ConfirmarEliminar(entityId)) }

    fun cerrarDialogo() = _state.update { it.copy(dialogo = null) }

    fun actualizarPunto(entityId: String, nombre: String, latitud: Double, longitud: Double) {
        viewModelScope.launch {
            when (val resultado = puntos.actualizar(entityId, nombre, latitud, longitud)) {
                is ResultadoGuardadoPunto.Guardado ->
                    _state.update { it.copy(dialogo = null, mensaje = "«${resultado.nombre}» se actualizó en este dispositivo") }
                // El diálogo sigue abierto para corregir el dato
                ResultadoGuardadoPunto.FaltaNombre ->
                    _state.update { it.copy(mensaje = "Escribe un nombre para el punto") }
                ResultadoGuardadoPunto.CoordenadasInvalidas ->
                    _state.update { it.copy(mensaje = "La latitud va de −90 a 90 y la longitud de −180 a 180") }
                ResultadoGuardadoPunto.NoEncontrado ->
                    _state.update { it.copy(dialogo = null, mensaje = "El punto ya no existe en este dispositivo") }
                ResultadoGuardadoPunto.SinEspacio ->
                    _state.update {
                        it.copy(dialogo = null, mensaje = "No hay espacio en el dispositivo para guardar los cambios. Libera espacio e inténtalo de nuevo")
                    }
                ResultadoGuardadoPunto.ErrorAlmacenamiento ->
                    _state.update { it.copy(dialogo = null, mensaje = "No se pudieron guardar los cambios del punto. Inténtalo de nuevo.") }
            }
        }
    }

    fun eliminarPunto(entityId: String) {
        viewModelScope.launch {
            val mensaje = when (val resultado = puntos.eliminar(entityId)) {
                is ResultadoEliminacionPunto.Eliminado -> "«${resultado.nombre}» se eliminó de este dispositivo"
                ResultadoEliminacionPunto.NoEncontrado -> "El punto ya no existe en este dispositivo"
                ResultadoEliminacionPunto.ErrorAlmacenamiento -> "No se pudo eliminar el punto. Inténtalo de nuevo."
            }
            _state.update { it.copy(dialogo = null, mensaje = mensaje) }
        }
    }

    fun mensajeMostrado() = _state.update { it.copy(mensaje = null) }
}
