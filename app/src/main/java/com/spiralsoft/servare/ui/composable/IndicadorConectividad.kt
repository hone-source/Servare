package com.spiralsoft.servare.ui.composable

import androidx.compose.animation.Crossfade
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlinx.coroutines.launch
import com.spiralsoft.servare.domain.model.AvisoConectividad
import com.spiralsoft.servare.domain.model.EstadoConectividad.CONECTADO
import com.spiralsoft.servare.domain.model.EstadoConectividad.RED_SIN_SERVIDOR
import com.spiralsoft.servare.domain.model.EstadoConectividad.SIN_RED
import com.spiralsoft.servare.ui.viewModel.ConectividadViewModel

// Un icono por estado de conectividad; al pulsarlo explica qué significa
@Composable
fun IndicadorConectividad(viewModel: ConectividadViewModel = hiltViewModel()) {
    val aviso by viewModel.aviso.collectAsState()
    IndicadorConectividad(aviso)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IndicadorConectividad(aviso: AvisoConectividad) {
    val tooltip = rememberTooltipState(isPersistent = true)
    val scope = rememberCoroutineScope()

    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
        tooltip = { RichTooltip(title = { Text(aviso.titulo) }) { Text(aviso.detalle) } },
        state = tooltip
    ) {
        IconButton(onClick = { scope.launch { tooltip.show() } }) {
            Crossfade(aviso, label = "conectividad") { aviso ->
                when (aviso.estado) {
                    SIN_RED -> Icon(
                        Icons.Outlined.WifiOff,
                        contentDescription = aviso.titulo,
                        tint = MaterialTheme.colorScheme.error
                    )
                    RED_SIN_SERVIDOR -> Icon(
                        Icons.Outlined.CloudOff,
                        contentDescription = aviso.titulo,
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                    CONECTADO -> Icon(
                        Icons.Outlined.CloudDone,
                        contentDescription = aviso.titulo,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
