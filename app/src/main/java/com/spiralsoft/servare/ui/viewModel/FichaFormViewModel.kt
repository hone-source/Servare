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
import kotlinx.serialization.json.Json
import javax.inject.Inject
import com.spiralsoft.servare.domain.model.Ficha
import com.spiralsoft.servare.domain.repository.FichaRepository
import com.spiralsoft.servare.domain.repository.ResultadoGuardado
import com.spiralsoft.servare.ui.viewModel.state.FichaFormState

@HiltViewModel
class FichaFormViewModel @Inject constructor(
    private val repository: FichaRepository,
    private val savedStateHandle: SavedStateHandle,
    private val json: Json
) : ViewModel() {

    // Argumento de la ruta "form?entityId={entityId}"; null = ficha nueva
    private val entityIdEditar: String? = savedStateHandle["entityId"]

    private val _form = MutableStateFlow(
        FichaFormState(entityId = entityIdEditar, cargando = entityIdEditar != null)
    )
    val form: StateFlow<FichaFormState> = _form.asStateFlow()


    fun updateFicha(cambio: (Ficha) -> Ficha) {
        _form.update { estado ->
            val ficha = cambio(estado.ficha)
            // El error desaparece en cuanto el usuario escribe el nombre
            estado.copy(ficha = ficha, errorNombre = estado.errorNombre?.takeIf { ficha.nombre.isBlank() })
        }
    }

    fun guardar() {
        _form.value.let { if (it.guardando || it.cargando || it.errorCarga != null) return }
        _form.update { it.copy(guardando = true) }

        viewModelScope.launch {
            val estado = _form.value
            val resultado = repository.guardar(estado.entityId, estado.ficha)
            _form.update { actual ->
                when (resultado) {
                    is ResultadoGuardado.Guardada -> actual.copy(
                        entityId = resultado.entityId,
                        fichaOriginal = estado.ficha,
                        guardando = false,
                        guardada = true
                    )
                    ResultadoGuardado.FaltaNombre -> actual.copy(
                        guardando = false,
                        errorNombre = "Escribe el nombre del lugar",
                        mensaje = "Falta el nombre del lugar"
                    )
                    ResultadoGuardado.SinEspacio -> actual.copy(
                        guardando = false,
                        mensaje = "No hay espacio en el dispositivo. Libera espacio e intenta de nuevo."
                    )
                    ResultadoGuardado.ErrorAlmacenamiento -> actual.copy(
                        guardando = false,
                        mensaje = "No se pudo guardar la ficha en el dispositivo. Intenta de nuevo."
                    )
                }
            }
        }
    }

    fun mensajeMostrado() = _form.update { it.copy(mensaje = null) }

}
