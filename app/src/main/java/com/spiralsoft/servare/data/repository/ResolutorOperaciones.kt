package com.spiralsoft.servare.data.repository

import android.database.sqlite.SQLiteException
import androidx.room.withTransaction
import java.time.Clock
import javax.inject.Inject
import com.spiralsoft.servare.data.local.AppDatabase
import com.spiralsoft.servare.data.local.dao.EliminacionDao
import com.spiralsoft.servare.data.local.dao.OperationDao
import com.spiralsoft.servare.data.local.entity.OperationEntity
import com.spiralsoft.servare.data.sync.Adaptadores
import com.spiralsoft.servare.data.sync.RegistroServidor
import com.spiralsoft.servare.data.sync.SyncScheduler
import com.spiralsoft.servare.domain.model.AccionOperacion
import com.spiralsoft.servare.domain.model.CausaConflicto
import com.spiralsoft.servare.domain.model.EstadoOperacion
import com.spiralsoft.servare.domain.model.TipoOperacion
import com.spiralsoft.servare.domain.repository.ResultadoResolucion

// Lo que el usuario decide sobre una operación FALLIDA o en CONFLICTO (docs/2.2.md, transiciones).
// Es igual para todos los tipos de registro: lo propio de cada uno lo resuelve su adaptador
class ResolutorOperaciones @Inject constructor(
    private val database: AppDatabase,
    private val operationDao: OperationDao,
    private val encolador: EncoladorOperaciones,
    private val eliminacionDao: EliminacionDao,
    private val adaptadores: Adaptadores,
    private val syncScheduler: SyncScheduler,
    private val clock: Clock
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
        val ahora = clock.millis()

        when (accion) {
            AccionOperacion.REINTENTAR -> {
                if (operationDao.reintentar(op.operationId) == 0) return false
                liberarBloqueadas(op)
            }

            // El registro ya no existe en el servidor: se vuelve a crear con una operación nueva. La vieja ya
            // recibió un 404 y no vale la pena repetir su operationId. Conserva el orden con el mismo createdAt
            AccionOperacion.RECREAR -> {
                if (op.status != EstadoOperacion.CONFLICTO || op.conflictCause != CausaConflicto.INEXISTENTE_EN_SERVIDOR ||
                    op.type != TipoOperacion.ACTUALIZAR || !adaptadores[op.entityType].existe(op.entityId)
                ) return false
                if (operationDao.descartar(op.operationId, ahora) == 0) return false
                adaptadores[op.entityType].limpiarVersion(op.entityId)
                encolador.encolar(TipoOperacion.CREAR, op.entityType, op.entityId, createdAt = op.createdAt)
                liberarBloqueadas(op)
            }

            // Sigue adelante con la suya, ahora sobre la versión que tiene el servidor
            AccionOperacion.CONSERVAR_MIA -> {
                val version = servidor?.version
                if (!porVersion || version == null) return false
                if (operationDao.reanudarSobreVersion(op.operationId, version) == 0) return false
                adaptadores[op.entityType].actualizarVersion(op.entityId, version)
                // Las posteriores parten de esa misma versión, antes de salir de BLOQUEADA
                operationDao.encadenarBaseVersion(op.entityType, op.entityId, op.createdAt, version)
                liberarBloqueadas(op)
            }

            // La versión del servidor reemplaza a la local, que se abandona junto con lo que dependía de ella
            AccionOperacion.ACEPTAR_SERVIDOR -> {
                if (!porVersion || servidor == null) return false
                if (operationDao.descartar(op.operationId, ahora) == 0) return false
                descartarPosteriores(op, ahora)
                servidor.aplicar()
                eliminacionDao.delete(op.entityType, op.entityId) // si había un borrado, el registro vuelve
            }

            AccionOperacion.DESCARTAR -> {
                if (operationDao.descartar(op.operationId, ahora) == 0) return false
                descartarPosteriores(op, ahora)
                val desaparece = op.type == TipoOperacion.CREAR ||
                    op.conflictCause == CausaConflicto.INEXISTENTE_EN_SERVIDOR
                if (desaparece) {
                    // Nunca llegó al servidor, o ya no existe allí: no queda a qué sincronizar. Lo decidió el usuario
                    adaptadores[op.entityType].borrar(op.entityId)
                }
                // Si no, el registro vuelve a la lista (un borrado abandonado) o lo repone el pull con la versión
                // del servidor, que ya no está protegida por operaciones sin terminar
                eliminacionDao.delete(op.entityType, op.entityId)
            }
        }
        return true
    }

    // El registro del servidor que trajo el 409, null si no lo trajo o no se puede leer
    private fun leerServidor(op: OperationEntity): RegistroServidor? =
        op.serverSnapshot?.let { adaptadores[op.entityType].leerServidor(op.entityId, it) }

    private suspend fun liberarBloqueadas(op: OperationEntity) =
        operationDao.liberarBloqueadas(op.operationId, op.entityType, op.entityId, op.createdAt)

    private suspend fun descartarPosteriores(op: OperationEntity, ahora: Long) =
        operationDao.descartarPosteriores(op.operationId, op.entityType, op.entityId, op.createdAt, ahora)
}
