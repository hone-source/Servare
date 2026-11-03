package com.spiralsoft.servare.ui.viewModel.state

import com.spiralsoft.servare.domain.model.AvisoDescargaInicial
import com.spiralsoft.servare.domain.model.AvisoOperacion
import com.spiralsoft.servare.domain.model.AvisoSesionExpirada

// Estado de la lista de cualquier tipo de registro sincronizable
data class ListaState<out T>(
    val cargando: Boolean = true,
    val registros: List<T> = emptyList(),
    val sinDescargar: AvisoDescargaInicial? = null, // lista vacía porque aún no se descargaron los registros
    val sesionExpirada: AvisoSesionExpirada? = null, // se trabaja con lo guardado, pero nada se envía
    val problemas: List<AvisoOperacion> = emptyList(), // cambios FALLIDA, CONFLICTO o BLOQUEADA, hasta que se resuelvan
    val refrescando: Boolean = false, // el usuario pidió comprobar los cambios del servidor
    val mensaje: String? = null // resultado de eliminar o de sincronizar, se muestra una vez
)
