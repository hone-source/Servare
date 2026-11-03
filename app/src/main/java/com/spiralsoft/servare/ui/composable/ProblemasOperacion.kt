package com.spiralsoft.servare.ui.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.spiralsoft.servare.domain.model.AccionOperacion
import com.spiralsoft.servare.domain.model.AvisoOperacion

// Cambios que no se pudieron sincronizar, con lo que el usuario puede hacer con cada uno.
// Lo que cambia o pierde datos se confirma diciendo qué pasará; las versiones se pueden comparar
@Composable
fun ProblemasOperacion(
    avisos: List<AvisoOperacion>,
    onResolver: (operationId: String, accion: AccionOperacion) -> Unit
) {
    // Se guarda el id y no el aviso: si la operación cambia de estado, el diálogo se cierra solo
    var confirmando by remember { mutableStateOf<Pair<String, AccionOperacion>?>(null) }
    var comparando by remember { mutableStateOf<String?>(null) }

    val avisoConfirmado = confirmando?.let { (id, accion) ->
        avisos.firstOrNull { it.operationId == id }?.let { it to accion }
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
                    onResolver(aviso.operationId, accion)
                }) { Text(accion.etiqueta()) }
            },
            dismissButton = { TextButton(onClick = { confirmando = null }) { Text("Cancelar") } }
        )
    }
    avisos.firstOrNull { it.operationId == comparando }?.let { aviso -> DialogoVersiones(aviso) { comparando = null } }

    if (avisos.isNotEmpty()) {
        TarjetaProblemasOperacion(
            avisos,
            onAccion = { aviso, accion ->
                if (accion in aviso.confirmaciones) confirmando = aviso.operationId to accion
                else onResolver(aviso.operationId, accion)
            },
            onComparar = { comparando = it.operationId }
        )
    }
}

// Los campos del registro con lo que hay en el dispositivo y en el servidor
@Composable
private fun DialogoVersiones(aviso: AvisoOperacion, onCerrar: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text(aviso.titulo) },
        text = {
            // Una ficha puede traer decenas de diferencias: el contenido se desplaza
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
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
