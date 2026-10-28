package com.spiralsoft.servare.ui.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.spiralsoft.servare.ui.viewModel.CierreSesionViewModel

// Menú de la cuenta: muestra con qué correo se entró y permite cerrar la sesión.
// Cerrar la sesión borra los datos del dispositivo, así que se pide confirmación
// avisando de lo que se perdería
@Composable
fun MenuSesion(viewModel: CierreSesionViewModel = hiltViewModel()) {
    val correo by viewModel.correo.collectAsState()
    val state by viewModel.state.collectAsState()
    var abierto by rememberSaveable { mutableStateOf(false) }

    Box {
        IconButton(onClick = { abierto = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = "Cuenta")
        }
        DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
            correo?.let {
                DropdownMenuItem(
                    text = { Text(it, style = MaterialTheme.typography.bodySmall) },
                    onClick = {},
                    enabled = false
                )
            }
            DropdownMenuItem(
                text = { Text("Cerrar sesión") },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null) },
                onClick = {
                    abierto = false
                    viewModel.solicitar()
                }
            )
        }
    }

    state.confirmacion?.let { confirmacion ->
        val enRiesgo = confirmacion.cambiosSinEnviar != 0
        AlertDialog(
            onDismissRequest = viewModel::cancelar,
            title = { Text("¿Cerrar sesión?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(descripcion(confirmacion.cambiosSinEnviar))
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = viewModel::confirmar,
                    enabled = !state.cerrando,
                    colors = if (enRiesgo) ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    else ButtonDefaults.textButtonColors()
                ) {
                    if (state.cerrando) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text(if (state.error != null) "Reintentar" else "Cerrar sesión")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelar, enabled = !state.cerrando) { Text("Cancelar") }
            }
        )
    }
}

private const val DATOS_LOCALES =
    "Se quitarán de este dispositivo los registros guardados; volverás a descargarlos al iniciar sesión."

private fun descripcion(cambiosSinEnviar: Int?): String = when {
    cambiosSinEnviar == null ->
        "No se pudo comprobar si hay cambios sin enviar al servidor. Si los hay, se perderán. $DATOS_LOCALES"
    cambiosSinEnviar == 0 -> "Se cerrará tu sesión. $DATOS_LOCALES"
    else -> {
        val cambios = if (cambiosSinEnviar == 1) "1 cambio sin enviar" else "$cambiosSinEnviar cambios sin enviar"
        "Tienes $cambios al servidor. Si cierras sesión se perderán y no se podrán recuperar. " +
            "Puedes esperar a que se envíen antes de continuar. $DATOS_LOCALES"
    }
}
