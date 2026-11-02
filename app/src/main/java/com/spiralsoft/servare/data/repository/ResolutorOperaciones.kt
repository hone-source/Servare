package com.spiralsoft.servare.data.repository

import android.database.sqlite.SQLiteException
import androidx.room.withTransaction
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import com.spiralsoft.servare.data.local.AppDatabase
import com.spiralsoft.servare.data.local.dao.EliminacionDao
import com.spiralsoft.servare.data.local.dao.FichaDao
import com.spiralsoft.servare.data.local.dao.OperationDao
import com.spiralsoft.servare.data.local.dao.PruebaDao
import com.spiralsoft.servare.data.local.entity.OperationEntity
import com.spiralsoft.servare.data.local.entity.PruebaEntity
import com.spiralsoft.servare.data.local.entity.aEntidad
import com.spiralsoft.servare.data.remote.FichaRemota
import com.spiralsoft.servare.data.remote.PruebaRemota
import com.spiralsoft.servare.data.sync.SyncScheduler
import com.spiralsoft.servare.domain.model.AccionOperacion
import com.spiralsoft.servare.domain.model.CausaConflicto
import com.spiralsoft.servare.domain.model.EstadoOperacion
import com.spiralsoft.servare.domain.model.TipoEntidad
import com.spiralsoft.servare.domain.model.TipoOperacion
import com.spiralsoft.servare.domain.repository.ResultadoResolucion

// Lo que el usuario decide sobre una operación FALLIDA o en CONFLICTO (docs/2.2.md, transiciones).
// Es igual para todos los tipos de registro: solo cambia la tabla a la que se aplica
class ResolutorOperaciones @Inject constructor(
    private val database: AppDatabase,
    private val operationDao: OperationDao,
    private val eliminacionDao: EliminacionDao,
    private val pruebaDao: PruebaDao,
    private val fichaDao: FichaDao,
    private val syncScheduler: SyncScheduler,
    private val json: Json
) {
    suspend fun resolver(operationId: String, accion: AccionOperacion): ResultadoResolucion = try {
        // Cada acción es una sola transacción: el estado se comprueba dentro, así que el motor no se cruza
        val resuelta = database.withTransaction { aplicar(operationId, accion) }
        if (resuelta) {
            syncScheduler.requestSync()
            ResultadoResolucion.Resuelta(accion)
        } else ResultadoResolucion.NoDisponible
    } catch (_: SQLiteException) {
        ResultadoResolucion.ErrorAlmacenamiento
    }

    // false si la operación no existe o ya no está en un estado que admita la acción
    private suspend fun aplicar(operationId: String, accion: AccionOperacion): Boolean {
        val op = operationDao.buscar(operationId) ?: return false
        val porVersion = op.status == EstadoOperacion.CONFLICTO && op.conflictCause != CausaConflicto.INEXISTENTE_EN_SERVIDOR
        val servidor = leerServidor(op)

        when (accion) {
            AccionOperacion.REINTENTAR -> {
                if (operationDao.reintentar(op.operationId) == 0) return false
                liberarBloqueadas(op)
            }

            // El registro ya no existe en el servidor: se vuelve a crear con una operación nueva. La vieja ya
            // recibió un 404 y no vale la pena repetir su operationId. Conserva el orden con el mismo createdAt
            AccionOperacion.RECREAR -> {
                if (op.status != EstadoOperacion.CONFLICTO || op.conflictCause != CausaConflicto.INEXISTENTE_EN_SERVIDOR ||
                    op.type != TipoOperacion.ACTUALIZAR || !existeLocal(op)
                ) return false
                if (operationDao.descartar(op.operationId) == 0) return false
                limpiarVersion(op)
                operationDao.insert(
                    OperationEntity(
                        operationId = UUID.randomUUID().toString(),
                        type = TipoOperacion.CREAR,
                        entityType = op.entityType,
                        entityId = op.entityId,
                        createdAt = op.createdAt,
                        status = EstadoOperacion.PENDIENTE
                    )
                )
                liberarBloqueadas(op)
            }

            // Sigue adelante con la suya, ahora sobre la versión que tiene el servidor
            AccionOperacion.CONSERVAR_MIA -> {
                val version = servidor?.version
                if (!porVersion || version == null) return false
                if (operationDao.reanudarSobreVersion(op.operationId, version) == 0) return false
                actualizarVersion(op, version)
                // Las posteriores parten de esa misma versión, antes de salir de BLOQUEADA
                operationDao.encadenarBaseVersion(op.entityType, op.entityId, op.createdAt, version)
                liberarBloqueadas(op)
            }

            // La versión del servidor reemplaza a la local, que se abandona junto con lo que dependía de ella
            AccionOperacion.ACEPTAR_SERVIDOR -> {
                if (!porVersion || servidor == null) return false
                if (operationDao.descartar(op.operationId) == 0) return false
                descartarPosteriores(op)
                servidor.aplicar()
                eliminacionDao.delete(op.entityType, op.entityId) // si había un borrado, el registro vuelve
            }

            AccionOperacion.DESCARTAR -> {
                if (operationDao.descartar(op.operationId) == 0) return false
                descartarPosteriores(op)
                val desaparece = op.type == TipoOperacion.CREAR ||
                    op.conflictCause == CausaConflicto.INEXISTENTE_EN_SERVIDOR
                if (desaparece) {
                    // Nunca llegó al servidor, o ya no existe allí: no queda a qué sincronizar. Lo decidió el usuario
                    borrarLocal(op)
                }
                // Si no, el registro vuelve a la lista (un borrado abandonado) o lo repone el pull con la versión
                // del servidor, que ya no está protegida por operaciones sin terminar
                eliminacionDao.delete(op.entityType, op.entityId)
            }
        }
        return true
    }

    // El registro del servidor que trajo el 409, null si no lo trajo o no se puede leer
    private class RegistroServidor(val version: Int?, val aplicar: suspend () -> Unit)

    private fun leerServidor(op: OperationEntity): RegistroServidor? = op.serverSnapshot?.let { snapshot ->
        runCatching {
            when (op.entityType) {
                TipoEntidad.PRUEBA -> json.decodeFromString<PruebaRemota>(snapshot).let { servidor ->
                    RegistroServidor(servidor.version) {
                        pruebaDao.upsert(
                            PruebaEntity(
                                entityId = op.entityId,
                                titulo = servidor.titulo,
                                texto = servidor.texto,
                                numero = servidor.numero,
                                createdAt = servidor.createdAt,
                                version = servidor.version
                            )
                        )
                    }
                }
                TipoEntidad.FICHA -> json.decodeFromString<FichaRemota>(snapshot).let { servidor ->
                    RegistroServidor(servidor.version) {
                        fichaDao.upsert(
                            servidor.datos.aEntidad(op.entityId, servidor.createdAt, servidor.version, json)
                        )
                    }
                }
            }
        }.getOrNull()
    }

    private suspend fun existeLocal(op: OperationEntity) = when (op.entityType) {
        TipoEntidad.PRUEBA -> pruebaDao.findById(op.entityId) != null
        TipoEntidad.FICHA -> fichaDao.findById(op.entityId) != null
    }

    private suspend fun limpiarVersion(op: OperationEntity) = when (op.entityType) {
        TipoEntidad.PRUEBA -> pruebaDao.limpiarVersion(op.entityId)
        TipoEntidad.FICHA -> fichaDao.limpiarVersion(op.entityId)
    }

    private suspend fun actualizarVersion(op: OperationEntity, version: Int) = when (op.entityType) {
        TipoEntidad.PRUEBA -> pruebaDao.actualizarVersion(op.entityId, version)
        TipoEntidad.FICHA -> fichaDao.actualizarVersion(op.entityId, version)
    }

    private suspend fun borrarLocal(op: OperationEntity) = when (op.entityType) {
        TipoEntidad.PRUEBA -> pruebaDao.deleteById(op.entityId)
        TipoEntidad.FICHA -> fichaDao.deleteById(op.entityId)
    }

    private suspend fun liberarBloqueadas(op: OperationEntity) =
        operationDao.liberarBloqueadas(op.operationId, op.entityType, op.entityId, op.createdAt)

    private suspend fun descartarPosteriores(op: OperationEntity) =
        operationDao.descartarPosteriores(op.operationId, op.entityType, op.entityId, op.createdAt)
}
