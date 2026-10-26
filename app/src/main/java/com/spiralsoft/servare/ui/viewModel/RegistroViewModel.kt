package com.spiralsoft.servare.ui.viewModel

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import com.spiralsoft.servare.data.notificacion.ServicioNotificacionEstado
import com.spiralsoft.servare.domain.repository.LONGITUD_MINIMA_CONTRASENA
import com.spiralsoft.servare.domain.repository.ResultadoRegistro
import com.spiralsoft.servare.domain.repository.SesionRepository
import com.spiralsoft.servare.ui.viewModel.state.MensajeSesion
import com.spiralsoft.servare.ui.viewModel.state.MensajesSesion

@HiltViewModel
class RegistroViewModel @Inject constructor(
    private val repository: SesionRepository,
    servicio: ServicioNotificacionEstado
) : CredencialesViewModel(servicio.registroNoDisponible) {

    // Igual en todos los reintentos del mismo registro: si el servidor ya creó la cuenta pero la
    // respuesta no llegó, reconoce el reintento en vez de rechazarlo como correo duplicado.
    // Cambia en cuanto el usuario edita sus datos: ya es otro registro
    private var claveRegistro = UUID.randomUUID().toString()

    override fun alEditar() {
        claveRegistro = UUID.randomUUID().toString()
    }

    override fun enviar() {
        val actual = state.value
        if (!actual.puedeEnviar) return

        formulario.update { it.copy(enviando = true) }
        viewModelScope.launch {
            val resultado = repository.registrar(actual.correo, actual.contrasena, claveRegistro)
            formulario.update { form ->
                when (resultado) {
                    // La navegación la hace el estado de la sesión; se deja enviando para no repetir el envío
                    ResultadoRegistro.Registrado -> form
                    ResultadoRegistro.CorreoInvalido -> form.copy(
                        enviando = false,
                        errorCorreo = "Escribe un correo válido"
                    )
                    ResultadoRegistro.ContrasenaCorta -> form.copy(
                        enviando = false,
                        errorContrasena = "Mínimo $LONGITUD_MINIMA_CONTRASENA caracteres"
                    )
                    ResultadoRegistro.CorreoYaRegistrado -> form.copy(
                        enviando = false,
                        errorCorreo = "Este correo ya está registrado"
                    )
                    is ResultadoRegistro.SinConexion -> form.copy(enviando = false, mensaje = MensajesSesion.sinConexion)
                    ResultadoRegistro.SinRespuesta -> form.copy(enviando = false, mensaje = MensajesSesion.sinRespuesta)
                    ResultadoRegistro.Rechazado -> form.copy(enviando = false, mensaje = MensajesSesion.rechazado)
                    ResultadoRegistro.ErrorServidor -> form.copy(enviando = false, mensaje = MensajesSesion.errorServidor)
                    ResultadoRegistro.ErrorAlmacenamiento -> form.copy(
                        enviando = false,
                        mensaje = MensajeSesion(
                            "La cuenta se creó, pero no se pudo guardar la sesión en el dispositivo. Intenta de nuevo.",
                            reintentable = true
                        )
                    )
                }
            }
        }
    }
}
