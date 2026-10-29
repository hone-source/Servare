package com.spiralsoft.servare.ui.viewModel.state

data class CierreSesionState(
    val confirmacion: ConfirmacionCierre? = null, // diálogo abierto, null si no hay
    val cerrando: Boolean = false,
    val error: String? = null // se muestra dentro del diálogo, que sigue abierto para reintentar
)

// cambiosSinEnviar: null si no se pudo comprobar
data class ConfirmacionCierre(val cambiosSinEnviar: Int?)
