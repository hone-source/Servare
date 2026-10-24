package com.spiralsoft.servare.ui.viewModel

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
import com.spiralsoft.servare.ui.viewModel.state.PruebaFormState

@HiltViewModel
class PruebaFormViewModel @Inject constructor(
    private val repository: PruebaRepository
) : ViewModel() {

    private val _form = MutableStateFlow(PruebaFormState())
    val form: StateFlow<PruebaFormState> = _form.asStateFlow()

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
        if (estado.guardando) return

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
            val resultado = repository.guardar(estado.titulo, estado.texto, numero)
            _form.update { actual ->
                when (resultado) {
                    is ResultadoGuardadoPrueba.Guardada -> actual.copy(guardando = false, guardada = true)
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
