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

// Por qué una operación quedó en CONFLICTO, de eso depende lo que el usuario puede hacer
enum class CausaConflicto {
    VERSION_DISTINTA,           // el servidor tiene una versión distinta de baseVersion (409)
    INEXISTENTE_EN_SERVIDOR     // el registro ya no existe en el servidor (404/410)
}

enum class TipoOperacion {
    CREAR,
    ACTUALIZAR,
    ELIMINAR,
    ADJUNTAR
}
