package com.spiralsoft.servare.domain.model

// Por qué una acción que requiere servidor (registrarse, iniciar sesión) no se puede hacer ahora
data class AvisoSinConexion(
    val titulo: String,
    val detalle: String
)
