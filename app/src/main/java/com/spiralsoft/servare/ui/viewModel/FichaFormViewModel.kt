package com.spiralsoft.servare.ui.viewModel

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import com.spiralsoft.servare.ui.viewModel.state.FichaFormState

@HiltViewModel
class FichaFormViewModel @Inject constructor(
) : ViewModel() {

    private val _form = MutableStateFlow(FichaFormState())
    val form: StateFlow<FichaFormState> = _form.asStateFlow()

    fun updateForm(cambio: (FichaFormState) -> FichaFormState) = _form.update(cambio)
}
