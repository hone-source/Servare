package com.spiralsoft.servare.ui.screen

import androidx.compose.runtime.Composable
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.spiralsoft.servare.ui.composable.PantallaCredenciales
import com.spiralsoft.servare.ui.viewModel.LoginViewModel

// Dos usos.
// Desde el inicio, para quien ya tiene cuenta (sesión cerrada, app reinstalada).
// Desde la lista, con la sesión expirada: vuelve a entrar con la misma cuenta, sin perder lo guardado
@Composable
fun LoginScreen(
    onVolver: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val reautenticando = viewModel.correoFijo != null
    PantallaCredenciales(
        viewModel = viewModel,
        titulo = "Iniciar sesión",
        introduccion = if (reautenticando) {
            "Tu sesión expiró. Inicia sesión de nuevo para seguir sincronizando; " +
                "tus cambios guardados en este dispositivo se conservan. Requiere conexión con el servidor."
        } else {
            "Entra con tu cuenta de Servare. Iniciar sesión requiere conexión con el servidor."
        },
        textoBoton = "Iniciar sesión",
        correoFijo = reautenticando,
        onVolver = onVolver
    )
}
