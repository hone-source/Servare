package com.spiralsoft.servare.domain.model

// Lo que el usuario puede hacer con una operación que no terminó bien (docs/2.2.md, transiciones)
enum class AccionOperacion {
    REINTENTAR,         // FALLIDA → PENDIENTE
    RECREAR,            // CONFLICTO por registro inexistente → vuelve a crearse en el servidor
    CONSERVAR_MIA,      // CONFLICTO por versión → PENDIENTE sobre la versión del servidor
    ACEPTAR_SERVIDOR,   // CONFLICTO por versión → DESCARTADA, el registro toma la versión del servidor
    DESCARTAR           // abandona el cambio → DESCARTADA
}
