package com.spiralsoft.servare.ui.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.spiralsoft.servare.domain.model.MotivoProblema
import com.spiralsoft.servare.ui.viewModel.state.CambioUi

private val FORMATO_FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

/** Cambio que no se sincronizó, con sus dos salidas: reintentar (o conservar) y descartar. */
@Composable
fun TarjetaCambio(
    item: CambioUi,
    onReintentar: () -> Unit,
    onDescartar: () -> Unit
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Text(item.aviso.titulo, style = MaterialTheme.typography.titleMedium)
            }
            Text(item.aviso.detalle, style = MaterialTheme.typography.bodyMedium)
            Text(
                "Cambio del " + item.cambio.creadoEl.atZone(ZoneId.systemDefault()).format(FORMATO_FECHA_HORA),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                TextButton(onClick = onDescartar, enabled = !item.procesando) {
                    Text("Descartar")
                }
                FilledTonalButton(onClick = onReintentar, enabled = !item.procesando) {
                    Text(if (item.cambio.motivo == MotivoProblema.CONFLICTO) "Conservar mi versión" else "Reintentar")
                }
            }
        }
    }
}
