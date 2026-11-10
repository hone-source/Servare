package com.spiralsoft.servare.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.spiralsoft.servare.ui.composable.IndicadorConectividad
import com.spiralsoft.servare.ui.composable.ListaVacia
import com.spiralsoft.servare.ui.composable.MenuSesion
import com.spiralsoft.servare.ui.composable.ProblemasOperacion
import com.spiralsoft.servare.ui.composable.TarjetaFicha
import com.spiralsoft.servare.ui.composable.TarjetaSesionExpirada
import com.spiralsoft.servare.ui.viewModel.FichaListViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListFichaScreen(
    onNueva: () -> Unit,
    onEditar: (entityId: String) -> Unit,
    onIniciarSesion: () -> Unit,
    mensaje: String?,
    onMensajeMostrado: () -> Unit,
    onPruebas: (() -> Unit)? = null,
    onMapa: (() -> Unit)? = null,
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
            viewModel.mensajeMostrado()
        }
    }

    Scaffold(
        // Los avisos de sesión expirada y de cambios sin sincronizar van con la barra
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Fichas") },
                    actions = {
                        IndicadorConectividad()
                        MenuSesion(onPruebas, onMapa)
                    }
                )
                state.sesionExpirada?.let { TarjetaSesionExpirada(it, onIniciarSesion) }
                ProblemasOperacion(state.problemas, viewModel::resolver)
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNueva) {
                Icon(Icons.Default.Add, contentDescription = "Nueva ficha")
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        val modifier = Modifier.fillMaxSize()
        val sinDescargar = state.sinDescargar
        // El gesto solo se detecta sobre contenido desplazable: los estados vacíos también se desplazan
        val vacio = modifier.verticalScroll(rememberScrollState())
        PullToRefreshBox(
            isRefreshing = state.refrescando,
            onRefresh = viewModel::refrescar,
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            when {
                state.cargando -> Box(modifier)
                sinDescargar != null -> Box(vacio.padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Outlined.CloudDownload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            sinDescargar.titulo,
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            sinDescargar.detalle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                state.registros.isEmpty() -> ListaVacia(vacio)
                else -> LazyColumn(
                    modifier = modifier,
                    // Espacio inferior para que el botón "+" no tape la última ficha
                    contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.registros, key = { it.entityId }) { ficha ->
                        TarjetaFicha(
                            ficha,
                            onEditar = { onEditar(ficha.entityId) },
                            onEliminar = { viewModel.eliminar(ficha.entityId) }
                        )
                    }
                }
            }
        }
    }
}
