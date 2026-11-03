package com.spiralsoft.servare.ui.viewModel

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.spiralsoft.servare.domain.model.FichaResumen
import com.spiralsoft.servare.domain.repository.FichaRepository
import com.spiralsoft.servare.notification.ServicioNotificacionEstado

@HiltViewModel
class FichaListViewModel @Inject constructor(
    private val repository: FichaRepository,
    servicio: ServicioNotificacionEstado
) : ListaSincronizableViewModel<FichaResumen>(repository, servicio, repository.observarResumenes()) {

    fun eliminar(entityId: String) {
        viewModelScope.launch {
            mensaje.value = repository.eliminar(entityId).mensaje()
        }
    }
}
