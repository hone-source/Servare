package com.spiralsoft.servare.domain.repository

import kotlinx.coroutines.flow.Flow
import com.spiralsoft.servare.domain.model.AccionOperacion

// Lo que cada repositorio de registros sincronizables ofrece, sea cual sea el tipo de registro
interface RepositorioSincronizable {

    // true cuando ya se descargaron los registros del servidor al menos una vez.
    // Distingue "vacío porque no hay datos" de "vacío porque aún no se descargaron"
    fun observarDescargaInicial(): Flow<Boolean>

    // El usuario decide qué hacer con una operación FALLIDA o en CONFLICTO
    suspend fun resolver(operationId: String, accion: AccionOperacion): ResultadoResolucion

    // El usuario pide comprobar los cambios del servidor.
    // Solo descarga: no envía la cola
    suspend fun refrescar(): ResultadoRefresco

}

sealed interface ResultadoRefresco {

    // eliminadas: títulos de los registros que se quitaron por estar borrados en el servidor
    data class Actualizada(val eliminadas: List<String> = emptyList()) : ResultadoRefresco
    data object SinSesion : ResultadoRefresco
    data object SinServidor : ResultadoRefresco
    data object ErrorDescarga : ResultadoRefresco
}

sealed interface ResultadoResolucion {
    data class Resuelta(val accion: AccionOperacion) : ResultadoResolucion
    // La operación ya no está en el estado que se mostró (se resolvió o la tomó el motor)
    data object NoDisponible : ResultadoResolucion
    data object ErrorAlmacenamiento : ResultadoResolucion
}
