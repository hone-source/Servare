package com.spiralsoft.servare.ui.viewModel

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import com.spiralsoft.servare.data.notificacion.ServicioNotificacionEstado
import com.spiralsoft.servare.domain.model.AvisoConectividad

@HiltViewModel
class ConectividadViewModel @Inject constructor(
    servicio: ServicioNotificacionEstado
) : ViewModel() {
    val aviso: StateFlow<AvisoConectividad> = servicio.conectividad
}
