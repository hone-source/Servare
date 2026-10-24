package com.spiralsoft.servare.data.repository

import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteFullException
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import com.spiralsoft.servare.data.local.AppDatabase
import com.spiralsoft.servare.data.local.dao.EliminacionDao
import com.spiralsoft.servare.data.local.dao.FichaDao
import com.spiralsoft.servare.data.local.dao.OperationDao
import com.spiralsoft.servare.data.local.entity.EliminacionEntity
import com.spiralsoft.servare.data.local.entity.FichaEntity
import com.spiralsoft.servare.data.local.entity.OperationEntity
import com.spiralsoft.servare.domain.model.EstadoOperacion
import com.spiralsoft.servare.domain.model.Ficha
import com.spiralsoft.servare.domain.model.FichaResumen
import com.spiralsoft.servare.domain.model.TipoEntidad
import com.spiralsoft.servare.domain.model.TipoOperacion
import com.spiralsoft.servare.domain.repository.FichaRepository
import com.spiralsoft.servare.domain.repository.ResultadoCarga
import com.spiralsoft.servare.domain.repository.ResultadoEliminacion
import com.spiralsoft.servare.domain.repository.ResultadoGuardado

class DefaultFichaRepository @Inject constructor(
    private val database: AppDatabase,
    private val fichaDao: FichaDao,
    private val operationDao: OperationDao,
    private val eliminacionDao: EliminacionDao,
    private val json: Json,
    private val clock: Clock
) : FichaRepository {

    override fun observarResumenes(): Flow<List<FichaResumen>> = fichaDao.observarResumenes()

    override suspend fun guardar(entityId: String?, ficha: Ficha): ResultadoGuardado {
        if (ficha.nombre.isBlank()) return ResultadoGuardado.FaltaNombre

        val id = entityId ?: UUID.randomUUID().toString()
        val ahora = clock.millis()
        val datos = json.encodeToString(ficha)
        return try {
            // Outbox: el cambio y su operación PENDIENTE en una sola transacción
            database.withTransaction {
                val existente = fichaDao.findById(id)
                fichaDao.upsert(
                    FichaEntity(
                        entityId = id,
                        datos = datos,
                        nombre = ficha.nombre,
                        comunidad = ficha.comunidad,
                        tipoEspacio = ficha.tipoEspacio,
                        fechaLevantamiento = ficha.fechaLevantamiento,
                        createdAt = existente?.createdAt ?: ahora,
                        updatedAt = ahora,
                        version = existente?.version
                    )
                )
                // Si ya hay una operación sin enviar, esa enviará el estado actual de la ficha
                if (operationDao.buscarAgrupable(TipoEntidad.FICHA, id) == null) {
                    operationDao.insert(
                        OperationEntity(
                            operationId = UUID.randomUUID().toString(),
                            type = if (existente == null) TipoOperacion.CREAR else TipoOperacion.ACTUALIZAR,
                            entityType = TipoEntidad.FICHA,
                            entityId = id,
                            createdAt = ahora,
                            status = EstadoOperacion.PENDIENTE,
                            baseVersion = existente?.version
                        )
                    )
                }
            }
            // TODO requestSync() cuando exista el motor de sincronización (fuera de la transacción)
            ResultadoGuardado.Guardada(id)
        } catch (_: SQLiteFullException) {
            ResultadoGuardado.SinEspacio
        } catch (_: SQLiteException) {
            ResultadoGuardado.ErrorAlmacenamiento
        }
    }

    override suspend fun obtener(entityId: String): ResultadoCarga = try {
        fichaDao.findActivaById(entityId)
            ?.let { ResultadoCarga.Encontrada(json.decodeFromString<Ficha>(it.datos)) }
            ?: ResultadoCarga.NoEncontrada
    } catch (_: SQLiteException) {
        ResultadoCarga.ErrorAlmacenamiento
    } catch (_: SerializationException) {
        ResultadoCarga.ErrorAlmacenamiento
    }

    override suspend fun eliminar(entityId: String): ResultadoEliminacion = try {
        // Outbox: la marca de borrado y su operación PENDIENTE en una sola transacción.
        // La ficha sigue en su tabla: si el borrado falla en el servidor, no se pierde
        val eliminada = database.withTransaction {
            val existente = fichaDao.findActivaById(entityId) ?: return@withTransaction null
            val ahora = clock.millis()
            eliminacionDao.insert(EliminacionEntity(TipoEntidad.FICHA, entityId, ahora))
            operationDao.insert(
                OperationEntity(
                    operationId = UUID.randomUUID().toString(),
                    type = TipoOperacion.ELIMINAR,
                    entityType = TipoEntidad.FICHA,
                    entityId = entityId,
                    createdAt = ahora,
                    status = EstadoOperacion.PENDIENTE,
                    baseVersion = existente.version
                )
            )
            existente
        }
        // TODO al completar el ELIMINAR, el motor borra la ficha (deleteById) y su eliminación en una transacción
        // TODO requestSync() cuando exista el motor de sincronización (fuera de la transacción)
        eliminada?.let { ResultadoEliminacion.Eliminada(it.nombre) } ?: ResultadoEliminacion.NoEncontrada
    } catch (_: SQLiteException) {
        ResultadoEliminacion.ErrorAlmacenamiento
    }
}
