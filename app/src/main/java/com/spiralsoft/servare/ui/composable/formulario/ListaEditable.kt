package com.spiralsoft.servare.ui.composable.formulario

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// Tabla de la ficha (poligonal, rituales...): una tarjeta por fila
@Composable
fun <T> ListaEditable(
    titulo: String,
    elementos: List<T>,
    nuevo: () -> T,
    onCambio: (List<T>) -> Unit,
    contenido: @Composable (elemento: T, onCambioElemento: (T) -> Unit) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(titulo, style = MaterialTheme.typography.labelLarge)
        elementos.forEachIndexed { indice, elemento ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${indice + 1}",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { onCambio(elementos.filterIndexed { i, _ -> i != indice }) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Quitar")
                        }
                    }
                    contenido(elemento) { cambiado ->
                        onCambio(elementos.mapIndexed { i, actual -> if (i == indice) cambiado else actual })
                    }
                }
            }
        }
        OutlinedButton(onClick = { onCambio(elementos + nuevo()) }) {
            Icon(Icons.Default.Add, contentDescription = null)
            Text("Agregar", Modifier.padding(start = 8.dp))
        }
    }
}
