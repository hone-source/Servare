package com.spiralsoft.servare.ui.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.format.DateTimeFormatter
import com.spiralsoft.servare.domain.model.EstadoSincronizacion
import com.spiralsoft.servare.domain.model.FichaResumen

private val FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy")

@Composable
fun TarjetaFicha(
    ficha: FichaResumen,
    onEditar: () -> Unit,
    onBorrar: () -> Unit
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    ficha.nombre.ifBlank { "Sin nombre" },
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onEditar) {
                    Icon(Icons.TwoTone.Edit, contentDescription = "Editar")
                }
                IconButton(onClick = onBorrar) {
                    Icon(Icons.TwoTone.Delete, contentDescription = "Borrar")
                }
            }
            ficha.tipoEspacio?.let {
                Text(it.etiqueta, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
            val detalle: String = listOfNotNull(
                ficha.comunidad.ifBlank { null },
                ficha.fechaLevantamiento?.format(FORMATO_FECHA)
            ).joinToString(" · ")
            if (detalle.isNotEmpty()) {
                Text(detalle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            when (ficha.sincronizacion) {
                EstadoSincronizacion.SINCRONIZADA -> Unit
                EstadoSincronizacion.PENDIENTE -> Text(
                    "Pendiente de sincronizar",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary
                )
                EstadoSincronizacion.REQUIERE_ATENCION -> Text(
                    "No se pudo sincronizar",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

        }

    }

}
