package com.spiralsoft.servare.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.spiralsoft.servare.ui.composable.formulario.CampoTexto
import com.spiralsoft.servare.ui.viewModel.PruebaFormViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormPruebaScreen(
    onVolver: () -> Unit,
    onSalir: (mensaje: String) -> Unit,
    viewModel: PruebaFormViewModel = hiltViewModel()
) {
    val form by viewModel.form.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var confirmarSalida by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(form.mensajeSalida) {
        form.mensajeSalida?.let(onSalir)
    }

    LaunchedEffect(form.mensaje) {
        form.mensaje?.let {
            snackbar.showSnackbar(it)
            viewModel.mensajeMostrado()
        }
    }

    // Lo capturado no se pierde en silencio: salir con cambios pide confirmación
    val salir = { if (form.hayCambios && form.mensajeSalida == null) confirmarSalida = true else onVolver() }
    BackHandler(onBack = salir)

    if (confirmarSalida) {
        AlertDialog(
            onDismissRequest = { confirmarSalida = false },
            title = { Text("¿Descartar cambios?") },
            text = {
                Text(
                    if (form.editando) "Los cambios no se han guardado en el dispositivo."
                    else "Este registro no se ha guardado en el dispositivo."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmarSalida = false
                    onVolver()
                }) { Text("Descartar") }
            },
            dismissButton = { TextButton(onClick = { confirmarSalida = false }) { Text("Seguir editando") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (form.editando) "Editar registro de prueba" else "Nuevo registro de prueba") },
                navigationIcon = {
                    IconButton(onClick = salir) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    TextButton(onClick = viewModel::guardar, enabled = !form.guardando && !form.cargando) { Text("Guardar") }
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
            CampoTexto("Título", form.titulo, viewModel::updateTitulo, error = form.errorTitulo)
            CampoTexto("Texto", form.texto, viewModel::updateTexto, multilinea = true)
            CampoTexto(
                "Número",
                form.numero,
                viewModel::updateNumero,
                teclado = KeyboardType.Number,
                error = form.errorNumero
            )
        }
    }
}
