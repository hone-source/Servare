package com.spiralsoft.servare.data.local.entity

enum class OperationStatus {
    PENDIENTE,
    PROCESANDO,
    COMPLETADA,
    REINTENTABLE,
    CONFLICTO,
    FALLIDA,
    BLOQUEADA,
    DESCARTADA
}

enum class OperationType {
    CREATE,
    UPDATE,
    DELETE,
    ATTACH
}