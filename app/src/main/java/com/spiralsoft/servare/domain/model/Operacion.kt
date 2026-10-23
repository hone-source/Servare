package com.spiralsoft.servare.domain.model

// Estados de una operación de la cola (docs/2.2.md)
enum class EstadoOperacion {
    PENDIENTE,
    PROCESANDO,
    COMPLETADA,
    REINTENTABLE,
    CONFLICTO,
    FALLIDA,
    BLOQUEADA,
    DESCARTADA
}

enum class TipoOperacion {
    CREAR,
    ACTUALIZAR,
    ELIMINAR,
    ADJUNTAR
}
