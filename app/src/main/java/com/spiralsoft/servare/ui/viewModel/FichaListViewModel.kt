package com.spiralsoft.servare.ui.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import com.spiralsoft.servare.data.repository.FichaRepository
import com.spiralsoft.servare.ui.viewModel.state.FichaListState

@HiltViewModel
class FichaListViewModel @Inject constructor(
    repository: FichaRepository
) : ViewModel() {

    val state: StateFlow<FichaListState> = repository.observarResumenes()
        .map { FichaListState(cargando = false, fichas = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FichaListState())
}
