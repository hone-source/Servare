package com.spiralsoft.servare.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.spiralsoft.servare.ui.composable.AvisoConexion
import com.spiralsoft.servare.ui.composable.ListaVacia
import com.spiralsoft.servare.ui.composable.TarjetaFicha
import com.spiralsoft.servare.ui.viewModel.FichaListViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListFichaScreen(
    onNuevaFicha: () -> Unit,
    onEditarFicha: (entityId: String) -> Unit,
    mensaje: String?,
    onMensajeMostrado: () -> Unit,
    viewModel: FichaListViewModel = hiltViewModel()
) {

    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    // Ficha que espera confirmación para eliminarse; sobrevive a la rotación
    var porEliminar by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(mensaje) {
        mensaje?.let {
            snackbar.showSnackbar(it)
            onMensajeMostrado()
        }
    }

    LaunchedEffect(state.mensaje) {
        state.mensaje?.let {
            snackbar.showSnackbar(it)
            viewModel.mensajeMostrado()
        }
    }

    val onEliminarFicha: (String) -> Unit = { entityId ->
        porEliminar = null
        viewModel.eliminar(entityId)
    }

    porEliminar?.let { entityId ->
        val nombre = state.fichas.find { it.entityId == entityId }?.nombre?.ifBlank { null } ?: "Sin nombre"
        AlertDialog(
            onDismissRequest = { porEliminar = null },
            title = { Text("¿Eliminar la ficha?") },
            text = { Text("La ficha “$nombre” se eliminará del dispositivo. Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = { onEliminarFicha(entityId) }) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { porEliminar = null }) { Text("Cancelar") }
            }
        )
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Fichas de levantamiento") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onNuevaFicha) {
                Icon(Icons.Default.Add, contentDescription = "Nueva ficha")
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            state.avisoConexion?.let { AvisoConexion(it) }

            if (state.cargando) {
                Box(Modifier.fillMaxSize())
            } else if (state.fichas.isEmpty()) {
                ListaVacia(Modifier.fillMaxSize())
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    // Espacio inferior para que el botón "+" no tape la última ficha
                    contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.fichas, key = { it.entityId }) { ficha ->
                        TarjetaFicha(
                            ficha,
                            onEditar = {
                                onEditarFicha(ficha.entityId)
                            },
                            onBorrar = {
                                porEliminar = ficha.entityId
                            }
                        )
                    }
                }
            }
        }
    }
}
