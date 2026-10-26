package com.spiralsoft.servare.ui.screen

import androidx.compose.runtime.Composable
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.spiralsoft.servare.domain.repository.LONGITUD_MINIMA_CONTRASENA
import com.spiralsoft.servare.ui.composable.PantallaCredenciales
import com.spiralsoft.servare.ui.viewModel.RegistroViewModel

// Primera pantalla sin sesión. Sin botón de volver.
// Registrarse es obligatorio para entrar
@Composable
fun RegistroScreen(
    onIniciarSesion: () -> Unit,
    viewModel: RegistroViewModel = hiltViewModel()
) {
    PantallaCredenciales(
        viewModel = viewModel,
        titulo = "Crear cuenta",
        introduccion = "Para usar Servare necesitas una cuenta. Crearla requiere conexión con el servidor.",
        textoBoton = "Crear cuenta",
        textoEnlace = "¿Ya tienes cuenta? Inicia sesión",
        onEnlace = onIniciarSesion,
        ayudaContrasena = "Mínimo $LONGITUD_MINIMA_CONTRASENA caracteres"
    )
}
