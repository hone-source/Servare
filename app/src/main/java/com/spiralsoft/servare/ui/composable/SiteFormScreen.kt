package com.spiralsoft.servare.ui.composable

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.spiralsoft.servare.domain.model.SacredSiteForm
import com.spiralsoft.servare.ui.viewModel.SacredSiteViewModel
import com.spiralsoft.servare.ui.viewModel.SaveEvent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiteFormScreen(
    entityId: String?,
    onDone: () -> Unit,
    viewModel: SacredSiteViewModel = hiltViewModel()
) {
    LaunchedEffect(entityId) { entityId?.let(viewModel::loadForEdit) }
    val editing by viewModel.editing.collectAsStateWithLifecycle()

    // La clave reinicia los campos cuando termina de cargar el registro a editar
    val key = editing?.entityId
    var name by rememberSaveable(key) { mutableStateOf(editing?.name ?: "") }
    var description by rememberSaveable(key) { mutableStateOf(editing?.description ?: "") }
    var type by rememberSaveable(key) { mutableStateOf(editing?.type ?: "") }
    var lat by rememberSaveable(key) { mutableStateOf(editing?.latitude?.toString() ?: "") }
    var lon by rememberSaveable(key) { mutableStateOf(editing?.longitude?.toString() ?: "") }
    var missing by remember { mutableStateOf(emptyList<String>()) }
    var coordsError by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                SaveEvent.Saved -> onDone()
                is SaveEvent.Invalid -> missing = event.missing
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(if (entityId == null) "Nuevo lugar" else "Editar lugar") })
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                name, { name = it }, label = { Text("Nombre *") },
                isError = "name" in missing, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                type, { type = it }, label = { Text("Tipo *") },
                isError = "type" in missing, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                description, { description = it }, label = { Text("Descripción") },
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    lat, { lat = it }, label = { Text("Latitud") }, isError = coordsError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    lon, { lon = it }, label = { Text("Longitud") }, isError = coordsError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
            }
            if (coordsError) {
                Text("Latitud (−90 a 90) y longitud (−180 a 180) deben ser números válidos.",
                    color = MaterialTheme.colorScheme.error)
            }
            Button(
                onClick = {
                    val la = lat.toDoubleOrNull()
                    val lo = lon.toDoubleOrNull()
                    coordsError = la == null || lo == null || la !in -90.0..90.0 || lo !in -180.0..180.0
                    if (!coordsError) {
                        viewModel.onSave(
                            SacredSiteForm(entityId, name.trim(), description.trim(), type.trim(), la!!, lo!!)
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Guardar") }
        }
    }
}
