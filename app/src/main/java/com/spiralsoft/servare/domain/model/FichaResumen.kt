package com.spiralsoft.servare.domain.model

// Lo que la lista muestra de cada ficha, sin leer el JSON completo
data class FichaResumen(
    val entityId: String,
    val nombre: String,
    val tipoEspacio: TipoEspacio?,
    val comunidad: String,
    val fechaLevantamiento: String?,
    val pendiente: Boolean // tiene cambios que el servidor aún no confirma
)
