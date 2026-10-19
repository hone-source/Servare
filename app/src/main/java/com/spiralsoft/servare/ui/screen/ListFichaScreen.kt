package com.spiralsoft.servare.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import java.time.format.DateTimeFormatter
import com.spiralsoft.servare.ui.viewModel.FichaListViewModel
import com.spiralsoft.servare.ui.viewModel.state.FichaResumen

private val FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListFichaScreen(
    onNuevaFicha: () -> Unit,
    viewModel: FichaListViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Fichas de levantamiento") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onNuevaFicha) {
                Icon(Icons.Default.Add, contentDescription = "Nueva ficha")
            }
        }
    ) { padding ->
        if (state.fichas.isEmpty()) {
            ListaVacia(Modifier.fillMaxSize().padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                // Espacio inferior para que el botón "+" no tape la última ficha
                contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.fichas, key = { it.entityId }) { ficha -> TarjetaFicha(ficha) }
            }
        }
    }
}

@Composable
private fun ListaVacia(modifier: Modifier = Modifier) {
    Box(modifier.padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Aún no hay fichas guardadas",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Text(
                "Pulsa + para registrar una nueva",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun TarjetaFicha(ficha: FichaResumen) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                ficha.nombre.ifBlank { "Sin nombre" },
                style = MaterialTheme.typography.titleMedium
            )
            ficha.tipoEspacio?.let {
                Text(it.etiqueta, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
            val detalle = listOfNotNull(
                ficha.comunidad.ifBlank { null },
                ficha.fechaLevantamiento?.format(FORMATO_FECHA)
            ).joinToString(" · ")
            if (detalle.isNotEmpty()) {
                Text(detalle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (ficha.pendienteSincronizar) {
                Text(
                    "Pendiente de sincronizar",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }
    }
}
