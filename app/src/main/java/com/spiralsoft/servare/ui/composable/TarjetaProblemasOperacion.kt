package com.spiralsoft.servare.ui.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.spiralsoft.servare.domain.model.AvisoOperacion

// Cambios que el servidor no pudo aceptar. Siguen guardados en el dispositivo hasta que el usuario decida.
// La altura se acota y se desplaza: con muchos avisos no tapa la lista
@Composable
fun TarjetaProblemasOperacion(
    avisos: List<AvisoOperacion>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(
            modifier = Modifier
                .heightIn(max = 240.dp)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            avisos.forEach { aviso ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(aviso.titulo, style = MaterialTheme.typography.titleSmall)
                    Text(aviso.detalle, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
