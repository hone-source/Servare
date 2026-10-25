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
import com.spiralsoft.servare.domain.repository.PruebaRepository
import com.spiralsoft.servare.domain.repository.ResultadoGuardadoPrueba
import com.spiralsoft.servare.domain.repository.ResultadoObtencionPrueba
import com.spiralsoft.servare.ui.viewModel.state.PruebaFormState

@HiltViewModel
class PruebaFormViewModel @Inject constructor(
    private val repository: PruebaRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    // Argumento de navegación: null al crear, el registro a editar si no
    private val entityId: String? = savedStateHandle["entityId"]

    private val _form = MutableStateFlow(PruebaFormState(editando = entityId != null, cargando = entityId != null))
    val form: StateFlow<PruebaFormState> = _form.asStateFlow()

    init {
        entityId?.let(::cargar)
    }

    private fun cargar(entityId: String) {
        viewModelScope.launch {
            val resultado = repository.obtener(entityId)
            _form.update { actual ->
                when (resultado) {
                    is ResultadoObtencionPrueba.Encontrada -> with(resultado.prueba) {
                        actual.copy(
                            cargando = false,
                            titulo = titulo,
                            texto = texto,
                            numero = numero.toString(),
                            tituloOriginal = titulo,
                            textoOriginal = texto,
                            numeroOriginal = numero.toString()
                        )
                    }
                    ResultadoObtencionPrueba.NoEncontrada ->
                        actual.copy(mensajeSalida = "Este registro ya no está en el dispositivo")
                    ResultadoObtencionPrueba.ErrorAlmacenamiento ->
                        actual.copy(mensajeSalida = "No se pudo abrir el registro. Intenta de nuevo.")
                }
            }
        }
    }

    // Cada error desaparece en cuanto el usuario corrige su campo
    fun updateTitulo(titulo: String) = _form.update {
        it.copy(titulo = titulo, errorTitulo = it.errorTitulo?.takeIf { titulo.isBlank() })
    }

    fun updateTexto(texto: String) = _form.update { it.copy(texto = texto) }

    fun updateNumero(numero: String) = _form.update {
        it.copy(numero = numero, errorNumero = it.errorNumero?.takeIf { numero.trim().toIntOrNull() == null })
    }

    fun guardar() {
        val estado = _form.value
        if (estado.guardando || estado.cargando) return

        val numero = estado.numero.trim().toIntOrNull()
        if (numero == null) {
            _form.update {
                it.copy(
                    errorNumero = if (estado.numero.isBlank()) "Escribe un número" else "Escribe un número entero",
                    mensaje = "Revisa el número"
                )
            }
            return
        }

        _form.update { it.copy(guardando = true) }
        viewModelScope.launch {
            val resultado = if (entityId == null) {
                repository.guardar(estado.titulo, estado.texto, numero)
            } else {
                repository.actualizar(entityId, estado.titulo, estado.texto, numero)
            }
            _form.update { actual ->
                when (resultado) {
                    is ResultadoGuardadoPrueba.Guardada -> actual.copy(
                        guardando = false,
                        mensajeSalida = if (entityId == null) "Registro guardado en el dispositivo"
                        else "Cambios guardados en el dispositivo"
                    )
                    ResultadoGuardadoPrueba.NoEncontrada -> actual.copy(
                        guardando = false,
                        mensajeSalida = "Este registro se eliminó del dispositivo; los cambios no se guardaron"
                    )
                    ResultadoGuardadoPrueba.FaltaTitulo -> actual.copy(
                        guardando = false,
                        errorTitulo = "Escribe el título",
                        mensaje = "Falta el título"
                    )
                    ResultadoGuardadoPrueba.SinEspacio -> actual.copy(
                        guardando = false,
                        mensaje = "No hay espacio en el dispositivo. Libera espacio e intenta de nuevo."
                    )
                    ResultadoGuardadoPrueba.ErrorAlmacenamiento -> actual.copy(
                        guardando = false,
                        mensaje = "No se pudo guardar el registro en el dispositivo. Intenta de nuevo."
                    )
                }
            }
        }
    }

    fun mensajeMostrado() = _form.update { it.copy(mensaje = null) }
}
