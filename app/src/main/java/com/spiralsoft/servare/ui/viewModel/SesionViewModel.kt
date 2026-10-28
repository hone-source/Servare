package com.spiralsoft.servare.ui.viewModel

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import com.spiralsoft.servare.domain.model.EstadoSesion
import com.spiralsoft.servare.domain.repository.SesionRepository

// Solo expone si hay sesión, la navegación decide con él qué pantalla mostrar primero
@HiltViewModel
class SesionViewModel @Inject constructor(
    repository: SesionRepository
) : ViewModel() {
    val estado: StateFlow<EstadoSesion> = repository.estado
}
