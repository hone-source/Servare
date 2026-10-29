package com.spiralsoft.servare.domain.model

// Lo que se le dice al usuario cuando su sesión expiró
data class AvisoSesionExpirada(
    val titulo: String,
    val detalle: String
)
