package com.spiralsoft.servare.ui.viewModel.state

import com.spiralsoft.servare.domain.model.AvisoDescargaInicial
import com.spiralsoft.servare.domain.model.Prueba

data class PruebaListState(
    val cargando: Boolean = true,
    val pruebas: List<Prueba> = emptyList(),
    val sinDescargar: AvisoDescargaInicial? = null, // lista vacía porque aún no se descargaron los registros
    val mensaje: String? = null // resultado de eliminar, se muestra una vez
)
