package com.spiralsoft.servare.ui.composable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spiralsoft.servare.data.connectivity.ConnectivityState
import com.spiralsoft.servare.ui.viewModel.SacredSiteViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiteListScreen(
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    viewModel: SacredSiteViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Lugares sagrados") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = "Agregar lugar")
            }
        }
    ) { padding ->

        Column(Modifier.padding(padding)) {
            ConnectivityBanner(state.connectivity)
            when {
                state.isLoading -> Box(Modifier.fillMaxSize().padding(padding)) {
                    CircularProgressIndicator(Modifier.padding(24.dp))
                }
                state.sites.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
                    Text("Aún no hay registros. Toca + para crear el primero; se guarda en el dispositivo aunque no tengas conexión.")
                }
                else -> LazyColumn(
                    Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.sites, key = { it.entityId }) { site ->
                        Card(Modifier.fillMaxWidth().clickable { onEdit(site.entityId) }) {
                            Column(Modifier.padding(16.dp)) {
                                Text(site.name, style = MaterialTheme.typography.titleMedium)
                                Text(site.type, style = MaterialTheme.typography.bodySmall)
                                if (site.description.isNotBlank()) Text(site.description)
                                if (site.pendingSync) {
                                    Spacer(Modifier.height(8.dp))
                                    AssistChip(onClick = {}, label = { Text("Pendiente de sincronizar") })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectivityBanner(state: ConnectivityState) {
    // Textos provisionales: RF-12 los centraliza después en StatusMessageMapper (D4)
    val text = when (state) {
        ConnectivityState.CONECTADO -> return
        ConnectivityState.SIN_RED ->
            "Sin conexión. Tus cambios se guardan en el dispositivo."
        ConnectivityState.RED_SIN_SERVIDOR ->
            "Hay red, pero no se alcanza el servidor. Tus cambios se guardan y se enviarán después."
    }
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Text(text, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
    }
}
