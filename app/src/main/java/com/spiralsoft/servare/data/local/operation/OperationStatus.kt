package com.spiralsoft.servare.data.local.operation

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
