package com.spiralsoft.servare.ui.viewModel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.spiralsoft.servare.domain.model.Ficha
import com.spiralsoft.servare.domain.repository.FichaRepository
import com.spiralsoft.servare.domain.repository.ResultadoGuardadoFicha
import com.spiralsoft.servare.domain.repository.ResultadoObtencionFicha
import com.spiralsoft.servare.ui.viewModel.state.FichaFormState

@HiltViewModel
class FichaFormViewModel @Inject constructor(
    private val repository: FichaRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    // Argumento de navegación: null al crear, la ficha a editar si no
    private val entityId: String? = savedStateHandle["entityId"]

    private val _form = MutableStateFlow(FichaFormState(entityId = entityId, cargando = entityId != null))
    val form: StateFlow<FichaFormState> = _form.asStateFlow()

    init {
        entityId?.let(::cargar)
    }

    private fun cargar(entityId: String) {
        viewModelScope.launch {
            val resultado = repository.obtener(entityId)
            _form.update { actual ->
                when (resultado) {
                    is ResultadoObtencionFicha.Encontrada -> actual.copy(
                        cargando = false,
                        ficha = resultado.ficha,
                        fichaOriginal = resultado.ficha
                    )
                    ResultadoObtencionFicha.NoEncontrada -> actual.copy(
                        cargando = false,
                        errorCarga = "Esta ficha ya no está en el dispositivo"
                    )
                    ResultadoObtencionFicha.ErrorAlmacenamiento -> actual.copy(
                        cargando = false,
                        errorCarga = "No se pudo abrir la ficha. Intenta de nuevo."
                    )
                }
            }
        }
    }

    fun updateFicha(cambio: (Ficha) -> Ficha) {
        _form.update { estado ->
            val ficha = cambio(estado.ficha)
            // El error desaparece en cuanto el usuario escribe el nombre
            estado.copy(ficha = ficha, errorNombre = estado.errorNombre?.takeIf { ficha.nombre.isBlank() })
        }
    }

    fun guardar() {
        val estado = _form.value
        if (estado.guardando || estado.cargando || estado.errorCarga != null) return

        _form.update { it.copy(guardando = true) }
        viewModelScope.launch {
            val resultado = if (entityId == null) {
                repository.guardar(estado.ficha)
            } else {
                repository.actualizar(entityId, estado.ficha)
            }
            _form.update { actual ->
                when (resultado) {
                    is ResultadoGuardadoFicha.Guardada -> actual.copy(
                        guardando = false,
                        mensajeSalida = if (entityId == null) "Ficha guardada en el dispositivo"
                        else "Cambios guardados en el dispositivo"
                    )
                    ResultadoGuardadoFicha.NoEncontrada -> actual.copy(
                        guardando = false,
                        mensajeSalida = "Esta ficha se eliminó del dispositivo; los cambios no se guardaron"
                    )
                    ResultadoGuardadoFicha.FaltaNombre -> actual.copy(
                        guardando = false,
                        errorNombre = "Escribe el nombre del lugar",
                        mensaje = "Falta el nombre del lugar"
                    )
                    ResultadoGuardadoFicha.SinEspacio -> actual.copy(
                        guardando = false,
                        mensaje = "No hay espacio en el dispositivo. Libera espacio e intenta de nuevo."
                    )
                    ResultadoGuardadoFicha.ErrorAlmacenamiento -> actual.copy(
                        guardando = false,
                        mensaje = "No se pudo guardar la ficha en el dispositivo. Intenta de nuevo."
                    )
                }
            }
        }
    }

    fun mensajeMostrado() = _form.update { it.copy(mensaje = null) }
}
