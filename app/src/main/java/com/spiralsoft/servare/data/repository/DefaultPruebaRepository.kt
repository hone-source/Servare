package com.spiralsoft.servare.data.repository

import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteFullException
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import com.spiralsoft.servare.data.local.AppDatabase
import com.spiralsoft.servare.data.local.dao.EliminacionDao
import com.spiralsoft.servare.data.local.dao.OperationDao
import com.spiralsoft.servare.data.local.dao.PruebaDao
import com.spiralsoft.servare.data.local.dao.SyncInicialDao
import com.spiralsoft.servare.data.local.entity.EliminacionEntity
import com.spiralsoft.servare.data.local.entity.OperationEntity
import com.spiralsoft.servare.data.local.entity.PruebaEntity
import com.spiralsoft.servare.data.remote.PruebaRemota
import com.spiralsoft.servare.data.sync.SyncEngine
import com.spiralsoft.servare.data.sync.SyncScheduler
import com.spiralsoft.servare.domain.model.AccionOperacion
import com.spiralsoft.servare.domain.model.CausaConflicto
import com.spiralsoft.servare.domain.model.EstadoOperacion
import com.spiralsoft.servare.domain.model.Prueba
import com.spiralsoft.servare.domain.model.TipoEntidad
import com.spiralsoft.servare.domain.model.TipoOperacion
import com.spiralsoft.servare.domain.repository.PruebaRepository
import com.spiralsoft.servare.domain.repository.ResultadoEliminacionPrueba
import com.spiralsoft.servare.domain.repository.ResultadoGuardadoPrueba
import com.spiralsoft.servare.domain.repository.ResultadoObtencionPrueba
import com.spiralsoft.servare.domain.repository.ResultadoRefresco
import com.spiralsoft.servare.domain.repository.ResultadoResolucion

class DefaultPruebaRepository @Inject constructor(
    private val database: AppDatabase,
    private val pruebaDao: PruebaDao,
    private val operationDao: OperationDao,
    private val eliminacionDao: EliminacionDao,
    private val syncInicialDao: SyncInicialDao,
    private val syncScheduler: SyncScheduler,
    private val syncEngine: SyncEngine,
    private val json: Json,
    private val clock: Clock
) : PruebaRepository {

    override fun observarTodas(): Flow<List<Prueba>>
        = pruebaDao.observarTodas().map { lista ->
            lista.map { it.aDominio() }
    }

    override fun observarDescargaInicial(): Flow<Boolean>
        = syncInicialDao.observarCompletada(TipoEntidad.PRUEBA)

    override suspend fun obtener(entityId: String): ResultadoObtencionPrueba = try {
        pruebaDao.findActivaById(entityId)?.let {
            ResultadoObtencionPrueba.Encontrada(it.aDominio()) }
            ?: ResultadoObtencionPrueba.NoEncontrada
    } catch (_: SQLiteException) {
        ResultadoObtencionPrueba.ErrorAlmacenamiento
    }

    override suspend fun guardar(titulo: String, texto: String, numero: Int): ResultadoGuardadoPrueba {
        if (titulo.isBlank()) return ResultadoGuardadoPrueba.FaltaTitulo

        val id = UUID.randomUUID().toString()
        val ahora = clock.millis()
        return try {
            // Outbox: el registro y su operación PENDIENTE en una sola transacción
            database.withTransaction {
                pruebaDao.insert(
                    PruebaEntity(
                        entityId = id,
                        titulo = titulo.trim(),
                        texto = texto,
                        numero = numero,
                        createdAt = ahora
                    )
                )
                operationDao.insert(
                    OperationEntity(
                        operationId = UUID.randomUUID().toString(),
                        type = TipoOperacion.CREAR,
                        entityType = TipoEntidad.PRUEBA,
                        entityId = id,
                        createdAt = ahora,
                        status = EstadoOperacion.PENDIENTE
                    )
                )
            }
            syncScheduler.requestSync()
            ResultadoGuardadoPrueba.Guardada(id)
        } catch (_: SQLiteFullException) {
            ResultadoGuardadoPrueba.SinEspacio
        } catch (_: SQLiteException) {
            ResultadoGuardadoPrueba.ErrorAlmacenamiento
        }
    }

    override suspend fun actualizar(
        entityId: String,
        titulo: String,
        texto: String,
        numero: Int
    ): ResultadoGuardadoPrueba {
        if (titulo.isBlank()) return ResultadoGuardadoPrueba.FaltaTitulo

        return try {
            // Outbox: el cambio y su operación PENDIENTE en una sola transacción
            val actualizada = database.withTransaction {
                val existente = pruebaDao.findActivaById(entityId) ?: return@withTransaction false
                pruebaDao.update(existente.copy(titulo = titulo.trim(), texto = texto, numero = numero))
                // Si ya hay una operación sin enviar (CREAR o ACTUALIZAR), enviará este contenido al leerlo
                if (operationDao.buscarAgrupable(TipoEntidad.PRUEBA, entityId) == null) {
                    val ahora = clock.millis()
                    operationDao.insert(
                        OperationEntity(
                            operationId = UUID.randomUUID().toString(),
                            type = TipoOperacion.ACTUALIZAR,
                            entityType = TipoEntidad.PRUEBA,
                            entityId = entityId,
                            createdAt = ahora,
                            status = EstadoOperacion.PENDIENTE,
                            baseVersion = existente.version
                        )
                    )
                }
                true
            }
            if (!actualizada) return ResultadoGuardadoPrueba.NoEncontrada
            syncScheduler.requestSync()
            ResultadoGuardadoPrueba.Guardada(entityId)
        } catch (_: SQLiteFullException) {
            ResultadoGuardadoPrueba.SinEspacio
        } catch (_: SQLiteException) {
            ResultadoGuardadoPrueba.ErrorAlmacenamiento
        }
    }

    override suspend fun eliminar(entityId: String): ResultadoEliminacionPrueba = try {
        // Outbox: la marca de borrado y su operación PENDIENTE en una sola transacción.
        // La prueba sigue en su tabla: si el borrado falla en el servidor, no se pierde
        val eliminada = database.withTransaction {
            val existente = pruebaDao.findActivaById(entityId) ?: return@withTransaction null
            val ahora = clock.millis()
            eliminacionDao.insert(EliminacionEntity(TipoEntidad.PRUEBA, entityId, ahora))
            operationDao.insert(
                OperationEntity(
                    operationId = UUID.randomUUID().toString(),
                    type = TipoOperacion.ELIMINAR,
                    entityType = TipoEntidad.PRUEBA,
                    entityId = entityId,
                    createdAt = ahora,
                    status = EstadoOperacion.PENDIENTE,
                    baseVersion = existente.version
                )
            )
            existente
        }
        // Al completarse el ELIMINAR, el motor borra la prueba y su eliminación en una transacción
        if (eliminada == null) ResultadoEliminacionPrueba.NoEncontrada
        else {
            syncScheduler.requestSync()
            ResultadoEliminacionPrueba.Eliminada(eliminada.titulo)
        }
    } catch (_: SQLiteException) {
        ResultadoEliminacionPrueba.ErrorAlmacenamiento
    }

    override suspend fun refrescar(): ResultadoRefresco = syncEngine.refrescar()

    override suspend fun resolver(operationId: String, accion: AccionOperacion): ResultadoResolucion = try {
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
        val servidor = op.serverSnapshot?.let { runCatching { json.decodeFromString<PruebaRemota>(it) }.getOrNull() }

        when (accion) {
            AccionOperacion.REINTENTAR -> {
                if (operationDao.reintentar(op.operationId) == 0) return false
                liberarBloqueadas(op)
            }

            // El registro ya no existe en el servidor: se vuelve a crear con una operación nueva. La vieja ya
            // recibió un 404 y no vale la pena repetir su operationId. Conserva el orden con el mismo createdAt
            AccionOperacion.RECREAR -> {
                if (op.status != EstadoOperacion.CONFLICTO || op.conflictCause != CausaConflicto.INEXISTENTE_EN_SERVIDOR ||
                    op.type != TipoOperacion.ACTUALIZAR || pruebaDao.findById(op.entityId) == null
                ) return false
                if (operationDao.descartar(op.operationId) == 0) return false
                pruebaDao.limpiarVersion(op.entityId)
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
                pruebaDao.actualizarVersion(op.entityId, version)
                // Las posteriores parten de esa misma versión, antes de salir de BLOQUEADA
                operationDao.encadenarBaseVersion(op.entityType, op.entityId, op.createdAt, version)
                liberarBloqueadas(op)
            }

            // La versión del servidor reemplaza a la local, que se abandona junto con lo que dependía de ella
            AccionOperacion.ACEPTAR_SERVIDOR -> {
                if (!porVersion || servidor == null) return false
                if (operationDao.descartar(op.operationId) == 0) return false
                descartarPosteriores(op)
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
                eliminacionDao.delete(op.entityType, op.entityId) // si había un borrado, el registro vuelve
            }

            AccionOperacion.DESCARTAR -> {
                if (operationDao.descartar(op.operationId) == 0) return false
                descartarPosteriores(op)
                val desaparece = op.type == TipoOperacion.CREAR ||
                    op.conflictCause == CausaConflicto.INEXISTENTE_EN_SERVIDOR
                if (desaparece) {
                    // Nunca llegó al servidor, o ya no existe allí: no queda a qué sincronizar. Lo decidió el usuario
                    pruebaDao.deleteById(op.entityId)
                }
                // Si no, el registro vuelve a la lista (un borrado abandonado) o lo repone el pull con la versión
                // del servidor, que ya no está protegida por operaciones sin terminar
                eliminacionDao.delete(op.entityType, op.entityId)
            }
        }
        return true
    }

    private suspend fun liberarBloqueadas(op: OperationEntity) =
        operationDao.liberarBloqueadas(op.operationId, op.entityType, op.entityId, op.createdAt)

    private suspend fun descartarPosteriores(op: OperationEntity) =
        operationDao.descartarPosteriores(op.operationId, op.entityType, op.entityId, op.createdAt)
}

private fun PruebaEntity.aDominio() = Prueba(entityId, titulo, texto, numero, createdAt)
