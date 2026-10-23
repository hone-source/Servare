package com.spiralsoft.servare.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
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

    LaunchedEffect(mensaje) {
        mensaje?.let {
            snackbar.showSnackbar(it)
            onMensajeMostrado()
        }
    }

    LaunchedEffect(state.mensaje) {
        state.mensaje?.let {
            snackbar.showSnackbar(it)
        }
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
                        )
                    }
                }
            }
        }
    }
}
