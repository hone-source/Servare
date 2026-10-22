package com.spiralsoft.servare.ui.viewModel.state

import com.spiralsoft.servare.domain.model.CambioPorResolver
import com.spiralsoft.servare.notification.MensajeEstado

data class CambiosState(
    val cargando: Boolean = true,
    val cambios: List<CambioUi> = emptyList(),
    val mensaje: String? = null         // resultado de reintentar o descartar, se muestra una vez
)

data class CambioUi(
    val cambio: CambioPorResolver,
    val aviso: MensajeEstado,               // qué pasó y qué se puede hacer
    val confirmarDescarte: MensajeEstado,   // consecuencias de descartar
    val procesando: Boolean                 // hay una decisión en curso: botones desactivados
)
