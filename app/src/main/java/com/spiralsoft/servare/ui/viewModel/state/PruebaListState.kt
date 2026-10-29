package com.spiralsoft.servare.ui.viewModel.state

import com.spiralsoft.servare.domain.model.AvisoDescargaInicial
import com.spiralsoft.servare.domain.model.AvisoOperacion
import com.spiralsoft.servare.domain.model.AvisoSesionExpirada
import com.spiralsoft.servare.domain.model.Prueba

data class PruebaListState(
    val cargando: Boolean = true,
    val pruebas: List<Prueba> = emptyList(),
    val sinDescargar: AvisoDescargaInicial? = null, // lista vacía porque aún no se descargaron los registros
    val sesionExpirada: AvisoSesionExpirada? = null, // se trabaja con lo guardado, pero nada se envía
    val problemas: List<AvisoOperacion> = emptyList(), // cambios FALLIDA, CONFLICTO o BLOQUEADA, hasta que se resuelvan
    val refrescando: Boolean = false, // el usuario pidió comprobar los cambios del servidor
    val mensaje: String? = null // resultado de eliminar o de sincronizar, se muestra una vez
)
