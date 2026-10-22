package com.spiralsoft.servare.domain.repository

import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteFullException
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.json.Json
import java.time.Clock
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import com.spiralsoft.servare.data.local.AppDatabase
import com.spiralsoft.servare.data.local.dao.FichaDao
import com.spiralsoft.servare.data.local.entity.FichaEntity
import com.spiralsoft.servare.data.local.entity.OperationEntity
import com.spiralsoft.servare.data.local.entity.toResumen
import com.spiralsoft.servare.data.local.operation.OperationType
import com.spiralsoft.servare.data.queue.OperationQueue
import com.spiralsoft.servare.data.sync.SyncScheduler
import com.spiralsoft.servare.domain.model.EstadoSincronizacion
import com.spiralsoft.servare.domain.model.Ficha
import com.spiralsoft.servare.domain.model.FichaResumen

class DefaultFichaRepository @Inject constructor(
    private val database: AppDatabase,
    private val fichaDao: FichaDao,
    private val queue: OperationQueue,
    private val scheduler: SyncScheduler,
    private val json: Json,
    private val clock: Clock
) : FichaRepository {

    override fun observarResumenes(): Flow<List<FichaResumen>> = combine(
        fichaDao.observeAll(),
        queue.observeUnsyncedEntityIds(),
        queue.observeEntityIdsNeedingAttention()
    ) { fichas, sinSincronizar, requierenAtencion ->
        fichas.map {
            it.toResumen(
                when (it.entityId) {
                    in requierenAtencion -> EstadoSincronizacion.REQUIERE_ATENCION
                    in sinSincronizar -> EstadoSincronizacion.PENDIENTE
                    else -> EstadoSincronizacion.SINCRONIZADA
                }
            )
        }
    }

    override suspend fun guardar(entityId: String?, ficha: Ficha): ResultadoGuardado {
        if (ficha.nombre.isBlank()) return ResultadoGuardado.FaltaNombre

        val id = entityId ?: UUID.randomUUID().toString()
        val ahora = Instant.now(clock)
        return try {
            // Outbox: el cambio y su operación PENDIENTE en una sola transacción
            database.withTransaction {
                val existente = fichaDao.findById(id)
                fichaDao.upsert(
                    FichaEntity(
                        entityId = id,
                        datos = ficha,
                        createdAt = existente?.createdAt ?: ahora,
                        updatedAt = ahora,
                        version = existente?.version
                    )
                )
                val tipo = if (existente == null) OperationType.CREATE else OperationType.UPDATE
                // Parte de la última versión conocida; si hay operaciones antes, la cola la encadena
                queue.enqueueReplacingRejected(tipo, id, json.encodeToString(ficha), existente?.version)
            }
            scheduler.requestSync()
            ResultadoGuardado.Guardada(id)
        } catch (_: SQLiteFullException) {
            ResultadoGuardado.SinEspacio
        } catch (_: SQLiteException) {
            ResultadoGuardado.ErrorAlmacenamiento
        }
    }

    override suspend fun obtener(entityId: String): ResultadoCarga = try {
        fichaDao.findById(entityId)?.let { ResultadoCarga.Encontrada(it.datos) } ?: ResultadoCarga.NoEncontrada
    } catch (_: SQLiteException) {
        ResultadoCarga.ErrorAlmacenamiento
    }

    override suspend fun eliminar(entityId: String): ResultadoEliminacion = try {
        val eliminada = database.withTransaction {
            val existente = fichaDao.findById(entityId) ?: return@withTransaction false
            fichaDao.deleteById(entityId)
            // La operación conserva la última versión de la ficha: si el borrado falla, no se pierde
            queue.enqueue(OperationType.DELETE, entityId, json.encodeToString(existente.datos), existente.version)
            true
        }
        if (eliminada) {
            scheduler.requestSync()
            ResultadoEliminacion.Eliminada
        } else {
            ResultadoEliminacion.NoEncontrada
        }
    } catch (_: SQLiteException) {
        ResultadoEliminacion.ErrorAlmacenamiento
    }

    override suspend fun aplicarRemota(entityId: String, ficha: Ficha, version: Int?) {
        database.withTransaction {
            // Push antes de pull: lo que aún está en la cola manda sobre lo que trae el servidor
            if (queue.hasUnfinished(entityId)) return@withTransaction
            val existente = fichaDao.findById(entityId)
            if (existente?.datos == ficha && existente.version == version) return@withTransaction
            val ahora = Instant.now(clock)
            fichaDao.upsert(
                FichaEntity(
                    entityId = entityId,
                    datos = ficha,
                    createdAt = existente?.createdAt ?: ahora,
                    updatedAt = ahora,
                    version = version
                )
            )
        }
    }

    override suspend fun idsLocales(): Set<String> = fichaDao.findAllIds().toSet()

    override suspend fun quitarEliminadasEnServidor(entityIds: Collection<String>): List<String?> =
        database.withTransaction {
            buildList {
                for (id in entityIds) {
                    // Igual que en aplicarRemota: lo que aún está en la cola manda sobre el servidor
                    if (queue.hasUnfinished(id)) continue
                    val existente = fichaDao.findById(id) ?: continue
                    fichaDao.deleteById(id)
                    add(existente.datos.nombre.ifBlank { null })
                }
            }
        }

    override suspend fun confirmarEnvio(op: OperationEntity, version: Int?) {
        database.withTransaction {
            queue.complete(op, version)
            if (version != null) fichaDao.updateVersion(op.entityId, version)
        }
    }

}
