package com.spiralsoft.servare.ui.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.spiralsoft.servare.ui.composable.formulario.CampoContrasena
import com.spiralsoft.servare.ui.composable.formulario.CampoTexto
import com.spiralsoft.servare.ui.viewModel.CredencialesViewModel

// Pantalla de correo y contraseña que comparten el registro y el inicio de sesión.
// Al terminar bien, la navegación pasa sola a la app porque cambia el estado de la sesión.
// Sin onVolver no hay botón de volver
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaCredenciales(
    viewModel: CredencialesViewModel,
    titulo: String,
    introduccion: String,
    textoBoton: String,
    modifier: Modifier = Modifier,
    ayudaContrasena: String? = null,
    correoFijo: Boolean = false,
    onVolver: (() -> Unit)? = null
) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.mensaje) {
        state.mensaje?.let { mensaje ->
            val resultado = snackbar.showSnackbar(
                message = mensaje.texto,
                actionLabel = if (mensaje.reintentable) "Reintentar" else null,
                duration = if (mensaje.reintentable) SnackbarDuration.Long else SnackbarDuration.Short
            )
            viewModel.mensajeMostrado()
            if (resultado == SnackbarResult.ActionPerformed) viewModel.enviar()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(titulo) },
                navigationIcon = {
                    onVolver?.let {
                        IconButton(onClick = it) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                introduccion,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            state.sinConexion?.let { aviso ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(aviso.titulo, style = MaterialTheme.typography.titleSmall)
                        Text(aviso.detalle, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            CampoTexto(
                "Correo electrónico",
                state.correo,
                viewModel::updateCorreo,
                teclado = KeyboardType.Email,
                error = state.errorCorreo,
                capitalizacion = KeyboardCapitalization.None,
                habilitado = !correoFijo
            )
            CampoContrasena(
                "Contraseña",
                state.contrasena,
                viewModel::updateContrasena,
                visible = state.contrasenaVisible,
                onCambiarVisibilidad = viewModel::cambiarVisibilidad,
                ayuda = ayudaContrasena,
                error = state.errorContrasena,
                onEnviar = viewModel::enviar
            )

            Button(
                onClick = viewModel::enviar,
                enabled = state.puedeEnviar,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.enviando) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(textoBoton)
                }
            }
        }
    }
}
