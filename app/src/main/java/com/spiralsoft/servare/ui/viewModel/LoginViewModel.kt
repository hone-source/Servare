package com.spiralsoft.servare.ui.viewModel

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.spiralsoft.servare.notification.ServicioNotificacionEstado
import com.spiralsoft.servare.domain.model.EstadoSesion
import com.spiralsoft.servare.domain.repository.ResultadoInicioSesion
import com.spiralsoft.servare.domain.repository.SesionRepository
import com.spiralsoft.servare.ui.viewModel.state.MensajeSesion
import com.spiralsoft.servare.ui.viewModel.state.MensajesSesion

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val repository: SesionRepository,
    servicio: ServicioNotificacionEstado
) : CredencialesViewModel(servicio.inicioSesionNoDisponible) {

    // Con la sesión expirada solo se puede volver a entrar con la misma cuenta
    // la cola guarda sus cambios
    val correoFijo: String? = (repository.estado.value as? EstadoSesion.Expirada)?.correo

    init {
        correoFijo?.let { fijo -> formulario.update { it.copy(correo = fijo) } }
    }

    // "Correo o contraseña incorrectos" no señala un campo
    // Se quita al corregir cualquiera de los dos
    override fun alEditar() {
        formulario.update { it.copy(errorContrasena = null) }
    }

    override fun enviar() {
        val actual = state.value
        if (!actual.puedeEnviar) return

        formulario.update { it.copy(enviando = true) }
        viewModelScope.launch {
            val resultado = repository.iniciarSesion(actual.correo, actual.contrasena)
            formulario.update { form ->
                when (resultado) {
                    // La navegación la hace el estado de la sesión
                    // se deja enviando para no repetir el envío
                    ResultadoInicioSesion.Autenticado -> form
                    ResultadoInicioSesion.CorreoInvalido -> form.copy(
                        enviando = false,
                        errorCorreo = "Escribe un correo válido"
                    )
                    ResultadoInicioSesion.FaltaContrasena -> form.copy(
                        enviando = false,
                        errorContrasena = "Escribe tu contraseña"
                    )
                    ResultadoInicioSesion.CredencialesInvalidas -> form.copy(
                        enviando = false,
                        errorContrasena = "Correo o contraseña incorrectos"
                    )
                    is ResultadoInicioSesion.SinConexion -> form.copy(enviando = false, mensaje = MensajesSesion.sinConexion)
                    ResultadoInicioSesion.SinRespuesta -> form.copy(enviando = false, mensaje = MensajesSesion.sinRespuesta)
                    ResultadoInicioSesion.Rechazado -> form.copy(enviando = false, mensaje = MensajesSesion.rechazado)
                    ResultadoInicioSesion.ErrorServidor -> form.copy(enviando = false, mensaje = MensajesSesion.errorServidor)
                    ResultadoInicioSesion.ErrorAlmacenamiento -> form.copy(
                        enviando = false,
                        mensaje = MensajeSesion(
                            "Tus datos son correctos, pero no se pudo guardar la sesión en el dispositivo. Intenta de nuevo.",
                            reintentable = true
                        )
                    )
                }
            }
        }
    }
}
