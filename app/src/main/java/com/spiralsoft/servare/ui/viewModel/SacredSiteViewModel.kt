package com.spiralsoft.servare.ui.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spiralsoft.servare.data.connectivity.ConnectivityState
import com.spiralsoft.servare.domain.model.*
import com.spiralsoft.servare.domain.repository.SacredSiteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SacredSiteUiState(
    val sites: List<SacredSite> = emptyList(),
    val connectivity: ConnectivityState = ConnectivityState.SIN_RED,
    val isLoading: Boolean = true
)

sealed interface SaveEvent {
    data object Saved : SaveEvent
    data class Invalid(val missing: List<String>) : SaveEvent
}

@HiltViewModel
class SacredSiteViewModel @Inject constructor(
    private val repository: SacredSiteRepository
) : ViewModel() {

    val uiState: StateFlow<SacredSiteUiState> = repository.observeSites()
        .map { SacredSiteUiState(sites = it, isLoading = false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SacredSiteUiState())

    private val _editing = MutableStateFlow<SacredSite?>(null)
    val editing: StateFlow<SacredSite?> = _editing.asStateFlow()

    private val _events = Channel<SaveEvent>(Channel.BUFFERED)
    val events: Flow<SaveEvent> = _events.receiveAsFlow()

    fun loadForEdit(entityId: String) {
        viewModelScope.launch { _editing.value = repository.getSite(entityId) }
    }

    fun onSave(form: SacredSiteForm) {
        viewModelScope.launch {
            when (val result = repository.save(form)) {
                is SaveResult.Saved -> _events.send(SaveEvent.Saved)
                is SaveResult.Invalid -> _events.send(SaveEvent.Invalid(result.missing))
            }
        }
    }

    // D1 declara además onShareClicked y onResolveConflict: se añaden con los pasos de RDAC y conflictos.
}
