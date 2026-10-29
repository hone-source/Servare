package com.spiralsoft.servare.ui.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.spiralsoft.servare.domain.model.AvisoSesionExpirada

// La sesión expiró, se puede seguir trabajando,
// pero nada se envía hasta iniciar sesión de nuevo
@Composable
fun TarjetaSesionExpirada(
    aviso: AvisoSesionExpirada,
    onIniciarSesion: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(aviso.titulo, style = MaterialTheme.typography.titleSmall)
            Text(aviso.detalle, style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onIniciarSesion) { Text("Iniciar sesión") }
        }
    }
}
