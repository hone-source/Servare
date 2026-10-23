package com.spiralsoft.servare.ui.composable.formulario

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy")
private val FORMATO_HORA = DateTimeFormatter.ofPattern("HH:mm")

// La fecha se guarda como texto ISO (yyyy-MM-dd) y se muestra como dd/MM/yyyy
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampoFecha(
    etiqueta: String,
    valor: String?,
    onCambio: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var abierto by rememberSaveable { mutableStateOf(false) }
    val fecha = valor?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    CampoSoloLectura(
        etiqueta = etiqueta,
        texto = fecha?.format(FORMATO_FECHA) ?: valor.orEmpty(),
        icono = Icons.Default.DateRange,
        descripcionIcono = "Elegir fecha",
        onAbrir = { abierto = true },
        onBorrar = if (valor.isNullOrEmpty()) null else ({ onCambio(null) }),
        modifier = modifier
    )

    if (abierto) {
        // DatePicker trabaja con milisegundos en UTC
        val estado = rememberDatePickerState(
            initialSelectedDateMillis = fecha?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { abierto = false },
            confirmButton = {
                TextButton(onClick = {
                    estado.selectedDateMillis?.let {
                        onCambio(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString())
                    }
                    abierto = false
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { abierto = false }) { Text("Cancelar") } }
        ) {
            DatePicker(state = estado)
        }
    }
}

// La hora se guarda como HH:mm
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampoHora(
    etiqueta: String,
    valor: String?,
    onCambio: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var abierto by rememberSaveable { mutableStateOf(false) }
    val hora = valor?.let { runCatching { LocalTime.parse(it) }.getOrNull() }

    CampoSoloLectura(
        etiqueta = etiqueta,
        texto = valor.orEmpty(),
        icono = Icons.Default.Schedule,
        descripcionIcono = "Elegir hora",
        onAbrir = { abierto = true },
        onBorrar = if (valor.isNullOrEmpty()) null else ({ onCambio(null) }),
        modifier = modifier
    )

    if (abierto) {
        val estado = rememberTimePickerState(
            initialHour = hora?.hour ?: 0,
            initialMinute = hora?.minute ?: 0,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { abierto = false },
            confirmButton = {
                TextButton(onClick = {
                    onCambio(LocalTime.of(estado.hour, estado.minute).format(FORMATO_HORA))
                    abierto = false
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { abierto = false }) { Text("Cancelar") } },
            text = { TimePicker(state = estado) }
        )
    }
}

// Campo que no se escribe: al tocarlo abre el selector
@Composable
private fun CampoSoloLectura(
    etiqueta: String,
    texto: String,
    icono: ImageVector,
    descripcionIcono: String,
    onAbrir: () -> Unit,
    onBorrar: (() -> Unit)?,
    modifier: Modifier
) {
    val interaccion = remember { MutableInteractionSource() }
    LaunchedEffect(interaccion) {
        interaccion.interactions.collect { if (it is PressInteraction.Release) onAbrir() }
    }

    OutlinedTextField(
        value = texto,
        onValueChange = {},
        readOnly = true,
        label = { Text(etiqueta) },
        interactionSource = interaccion,
        modifier = modifier.fillMaxWidth(),
        trailingIcon = {
            Row {
                if (onBorrar != null) {
                    IconButton(onClick = onBorrar) { Icon(Icons.Default.Clear, contentDescription = "Borrar") }
                }
                IconButton(onClick = onAbrir) { Icon(icono, contentDescription = descripcionIcono) }
            }
        }
    )
}
