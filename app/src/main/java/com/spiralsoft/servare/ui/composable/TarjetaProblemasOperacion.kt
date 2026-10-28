package com.spiralsoft.servare.ui.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.spiralsoft.servare.domain.model.AccionOperacion
import com.spiralsoft.servare.domain.model.AvisoOperacion

fun AccionOperacion.etiqueta() = when (this) {
    AccionOperacion.REINTENTAR -> "Reintentar"
    AccionOperacion.RECREAR -> "Volver a crear"
    AccionOperacion.CONSERVAR_MIA -> "Conservar la mía"
    AccionOperacion.ACEPTAR_SERVIDOR -> "Aceptar la del servidor"
    AccionOperacion.DESCARTAR -> "Descartar"
}

// Cambios que el servidor no pudo aceptar. Siguen guardados en el dispositivo hasta que el usuario decida.
// La altura se acota y se desplaza: con muchos avisos no tapa la lista
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TarjetaProblemasOperacion(
    avisos: List<AvisoOperacion>,
    onAccion: (AvisoOperacion, AccionOperacion) -> Unit,
    onComparar: (AvisoOperacion) -> Unit,
    modifier: Modifier = Modifier
) {
    val colores = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onErrorContainer)
    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(
            modifier = Modifier
                .heightIn(max = 280.dp)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            avisos.forEach { aviso ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(aviso.titulo, style = MaterialTheme.typography.titleSmall)
                    Text(aviso.detalle, style = MaterialTheme.typography.bodyMedium)
                    if (aviso.acciones.isNotEmpty() || aviso.comparacion != null) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (aviso.comparacion != null) {
                                TextButton(onClick = { onComparar(aviso) }, colors = colores) {
                                    Text("Ver ambas versiones")
                                }
                            }
                            aviso.acciones.forEach { accion ->
                                TextButton(onClick = { onAccion(aviso, accion) }, colors = colores) {
                                    Text(accion.etiqueta())
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
