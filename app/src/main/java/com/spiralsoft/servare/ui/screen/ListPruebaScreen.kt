package com.spiralsoft.servare.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.spiralsoft.servare.domain.model.AccionOperacion
import com.spiralsoft.servare.domain.model.AvisoOperacion
import com.spiralsoft.servare.domain.model.Prueba
import com.spiralsoft.servare.ui.composable.IndicadorConectividad
import com.spiralsoft.servare.ui.composable.MenuSesion
import com.spiralsoft.servare.ui.composable.TarjetaProblemasOperacion
import com.spiralsoft.servare.ui.composable.TarjetaSesionExpirada
import com.spiralsoft.servare.ui.composable.etiqueta
import com.spiralsoft.servare.ui.viewModel.PruebaListViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListPruebaScreen(
    onVolver: () -> Unit,
    onNuevo: () -> Unit,
    onEditar: (entityId: String) -> Unit,
    onIniciarSesion: () -> Unit,
    mensaje: String?,
    onMensajeMostrado: () -> Unit,
    viewModel: PruebaListViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    // Se guarda el id y no el aviso: si la operación cambia de estado, el diálogo se cierra solo
    var confirmando by remember { mutableStateOf<Pair<String, AccionOperacion>?>(null) }
    var comparando by remember { mutableStateOf<String?>(null) }

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

    val avisoConfirmado = confirmando?.let { (id, accion) ->
        state.problemas.firstOrNull { it.operationId == id }?.let { it to accion }
    }
    if (avisoConfirmado != null) {
        val (aviso, accion) = avisoConfirmado
        AlertDialog(
            onDismissRequest = { confirmando = null },
            title = { Text("${accion.etiqueta()}: ${aviso.titulo}") },
            text = { Text(aviso.confirmaciones.getValue(accion)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmando = null
                    viewModel.resolver(aviso.operationId, accion)
                }) { Text(accion.etiqueta()) }
            },
            dismissButton = { TextButton(onClick = { confirmando = null }) { Text("Cancelar") } }
        )
    }
    state.problemas.firstOrNull { it.operationId == comparando }?.let { aviso -> DialogoVersiones(aviso) { comparando = null } }

    Scaffold(
        // Los avisos de sesión expirada y de cambios sin sincronizar van con la barrat
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Registros de prueba") },
                    navigationIcon = {
                        IconButton(onClick = onVolver) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                        }
                    },
                    actions = {
                        IndicadorConectividad()
                        MenuSesion()
                    }
                )
                state.sesionExpirada?.let { TarjetaSesionExpirada(it, onIniciarSesion) }
                if (state.problemas.isNotEmpty()) {
                    TarjetaProblemasOperacion(
                        state.problemas,
                        onAccion = { aviso, accion ->
                            // Lo que cambia o pierde datos se confirma diciendo qué pasará
                            if (accion in aviso.confirmaciones) confirmando = aviso.operationId to accion
                            else viewModel.resolver(aviso.operationId, accion)
                        },
                        onComparar = { comparando = it.operationId }
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNuevo) {
                Icon(Icons.Default.Add, contentDescription = "Nuevo registro")
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        val modifier = Modifier.fillMaxSize()
        val sinDescargar = state.sinDescargar
        // El gesto solo se detecta sobre contenido desplazable: los estados vacíos también se desplazan
        val vacio = modifier.verticalScroll(rememberScrollState()).padding(32.dp)
        PullToRefreshBox(
            isRefreshing = state.refrescando,
            onRefresh = viewModel::refrescar,
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            when {
                state.cargando -> Box(modifier)
                sinDescargar != null -> Box(vacio, contentAlignment = Alignment.Center) {
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
                state.pruebas.isEmpty() -> Box(vacio, contentAlignment = Alignment.Center) {
                    Text(
                        "Aún no hay registros. Pulsa + para crear uno.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
                else -> LazyColumn(
                    modifier = modifier,
                    // Espacio inferior para que el botón "+" no tape el último registro
                    contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.pruebas, key = { it.entityId }) { prueba ->
                        TarjetaPrueba(
                            prueba,
                            onEditar = { onEditar(prueba.entityId) },
                            onEliminar = { viewModel.eliminar(prueba.entityId) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TarjetaPrueba(
    prueba: Prueba,
    onEditar: () -> Unit,
    onEliminar: () -> Unit
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(prueba.titulo, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(prueba.numero.toString(), style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = onEditar) {
                    Icon(Icons.TwoTone.Edit, contentDescription = "Editar")
                }
                IconButton(onClick = onEliminar) {
                    Icon(Icons.TwoTone.Delete, contentDescription = "Eliminar")
                }
            }
            if (prueba.texto.isNotBlank()) {
                Text(
                    prueba.texto,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// Los campos del registro con lo que hay en el dispositivo y en el servidor
@Composable
private fun DialogoVersiones(aviso: AvisoOperacion, onCerrar: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text(aviso.titulo) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                aviso.comparacion.orEmpty().forEach { campo ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(campo.nombre, style = MaterialTheme.typography.labelLarge)
                        if (campo.local == campo.servidor) {
                            Text("Igual en ambas: ${campo.local.vacioComoRaya()}")
                        } else {
                            Text("Tuya: ${campo.local.vacioComoRaya()}")
                            Text("Servidor: ${campo.servidor.vacioComoRaya()}")
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onCerrar) { Text("Cerrar") } }
    )
}

private fun String?.vacioComoRaya() = if (isNullOrBlank()) "—" else this
