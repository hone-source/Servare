package com.spiralsoft.servare.domain.model

// Lo que se le muestra al usuario cuando la lista está vacía porque aún no se descargaron los registros
data class AvisoDescargaInicial(
    val titulo: String,
    val detalle: String
)
