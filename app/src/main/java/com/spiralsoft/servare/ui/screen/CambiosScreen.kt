package com.spiralsoft.servare.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.spiralsoft.servare.ui.composable.TarjetaCambio
import com.spiralsoft.servare.ui.viewModel.CambiosViewModel

/** Cambios que no se pudieron sincronizar (FALLIDOS o en CONFLICTO): reintentar o descartar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CambiosScreen(
    onVolver: () -> Unit,
    viewModel: CambiosViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    // Cambio que espera confirmación para descartarse; sobrevive a la rotación
    var porDescartar by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(state.mensaje) {
        state.mensaje?.let {
            snackbar.showSnackbar(it)
            viewModel.mensajeMostrado()
        }
    }

    // Si mientras tanto se resolvió (otra decisión, la sincronización), no hay nada que confirmar
    val porConfirmar = porDescartar?.let { id -> state.cambios.find { it.cambio.operationId == id } }
    porConfirmar?.let { item ->
        AlertDialog(
            onDismissRequest = { porDescartar = null },
            title = { Text(item.confirmarDescarte.titulo) },
            text = { Text(item.confirmarDescarte.detalle) },
            confirmButton = {
                TextButton(onClick = {
                    porDescartar = null
                    viewModel.descartar(item.cambio)
                }) { Text("Descartar") }
            },
            dismissButton = {
                TextButton(onClick = { porDescartar = null }) { Text("Cancelar") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cambios por resolver") },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        if (state.cargando) {
            Box(Modifier.fillMaxSize().padding(padding))
        } else if (state.cambios.isEmpty()) {
            SinCambios(Modifier.fillMaxSize().padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.cambios, key = { it.cambio.operationId }) { item ->
                    TarjetaCambio(
                        item,
                        onReintentar = { viewModel.reintentar(item.cambio) },
                        onDescartar = { porDescartar = item.cambio.operationId }
                    )
                }
            }
        }
    }
}

@Composable
private fun SinCambios(modifier: Modifier = Modifier) {
    Box(modifier.padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "No hay cambios por resolver",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Text(
                "Lo que guardaste ya se sincronizó o está en cola para enviarse.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
