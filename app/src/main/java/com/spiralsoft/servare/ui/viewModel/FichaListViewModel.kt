package com.spiralsoft.servare.ui.viewModel

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import com.spiralsoft.servare.ui.viewModel.state.FichaListState

@HiltViewModel
class FichaListViewModel @Inject constructor(
) : ViewModel() {

    // TODO: observar el repositorio de fichas
    private val _state = MutableStateFlow(FichaListState())
    val state: StateFlow<FichaListState> = _state.asStateFlow()
}
