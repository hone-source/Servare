package com.spiralsoft.servare.ui.viewModel

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.spiralsoft.servare.domain.model.Prueba
import com.spiralsoft.servare.domain.repository.PruebaRepository
import com.spiralsoft.servare.notification.ServicioNotificacionEstado

@HiltViewModel
class PruebaListViewModel @Inject constructor(
    private val repository: PruebaRepository,
    servicio: ServicioNotificacionEstado
) : ListaSincronizableViewModel<Prueba>(repository, servicio, repository.observarTodas()) {

    fun eliminar(entityId: String) {
        viewModelScope.launch {
            mensaje.value = repository.eliminar(entityId).mensaje()
        }
    }
}
