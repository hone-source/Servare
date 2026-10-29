package com.spiralsoft.servare.domain.repository

import kotlinx.coroutines.flow.Flow
import com.spiralsoft.servare.domain.model.AccionOperacion
import com.spiralsoft.servare.domain.model.Prueba

interface PruebaRepository {
    fun observarTodas(): Flow<List<Prueba>>

    // true cuando ya se descargaron los registros del servidor al menos una vez.
    // Distingue "vacío porque no hay datos" de "vacío porque aún no se descargaron"
    fun observarDescargaInicial(): Flow<Boolean>

    suspend fun obtener(entityId: String): ResultadoObtencionPrueba

    suspend fun guardar(titulo: String, texto: String, numero: Int): ResultadoGuardadoPrueba

    suspend fun actualizar(entityId: String, titulo: String, texto: String, numero: Int): ResultadoGuardadoPrueba

    suspend fun eliminar(entityId: String): ResultadoEliminacionPrueba

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

sealed interface ResultadoGuardadoPrueba {
    data class Guardada(val entityId: String) : ResultadoGuardadoPrueba
    data object FaltaTitulo : ResultadoGuardadoPrueba
    data object NoEncontrada : ResultadoGuardadoPrueba // solo al actualizar
    data object SinEspacio : ResultadoGuardadoPrueba
    data object ErrorAlmacenamiento : ResultadoGuardadoPrueba
}

sealed interface ResultadoObtencionPrueba {
    data class Encontrada(val prueba: Prueba) : ResultadoObtencionPrueba
    data object NoEncontrada : ResultadoObtencionPrueba
    data object ErrorAlmacenamiento : ResultadoObtencionPrueba
}

sealed interface ResultadoEliminacionPrueba {
    data class Eliminada(val titulo: String) : ResultadoEliminacionPrueba
    data object NoEncontrada : ResultadoEliminacionPrueba
    data object ErrorAlmacenamiento : ResultadoEliminacionPrueba
}
