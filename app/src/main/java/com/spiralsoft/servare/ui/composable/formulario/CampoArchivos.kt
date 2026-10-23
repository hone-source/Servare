package com.spiralsoft.servare.ui.composable.formulario

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.spiralsoft.servare.domain.model.RegistroArchivos

// "Realizadas: __ del archivo __ al __"
@Composable
fun CampoArchivos(
    titulo: String,
    registro: RegistroArchivos,
    onCambio: (RegistroArchivos) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(titulo, style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CampoTexto(
                "Cantidad", registro.cantidad, { onCambio(registro.copy(cantidad = it)) },
                Modifier.weight(1f), teclado = KeyboardType.Number
            )
            CampoTexto("Del archivo", registro.desde, { onCambio(registro.copy(desde = it)) }, Modifier.weight(1f))
            CampoTexto("Al", registro.hasta, { onCambio(registro.copy(hasta = it)) }, Modifier.weight(1f))
        }
    }
}
