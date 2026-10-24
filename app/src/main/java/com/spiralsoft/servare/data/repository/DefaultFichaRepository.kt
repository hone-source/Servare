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
import com.spiralsoft.servare.data.local.dao.FichaDao
import com.spiralsoft.servare.data.local.dao.OperationDao
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
                operationDao.insert(
                    OperationEntity(
                        operationId = UUID.randomUUID().toString(),
                        type = if (existente == null) TipoOperacion.CREAR else TipoOperacion.ACTUALIZAR,
                        entityType = TipoEntidad.FICHA,
                        entityId = id,
                        data = datos,
                        createdAt = ahora,
                        status = EstadoOperacion.PENDIENTE,
                        baseVersion = existente?.version
                    )
                )
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
        fichaDao.findById(entityId)
            ?.let { ResultadoCarga.Encontrada(json.decodeFromString<Ficha>(it.datos)) }
            ?: ResultadoCarga.NoEncontrada
    } catch (_: SQLiteException) {
        ResultadoCarga.ErrorAlmacenamiento
    } catch (_: SerializationException) {
        ResultadoCarga.ErrorAlmacenamiento
    }

    override suspend fun eliminar(entityId: String): ResultadoEliminacion = try {
        // Outbox: el borrado y su operación PENDIENTE en una sola transacción
        val eliminada = database.withTransaction {
            val existente = fichaDao.findById(entityId) ?: return@withTransaction null
            fichaDao.deleteById(entityId)
            operationDao.insert(
                OperationEntity(
                    operationId = UUID.randomUUID().toString(),
                    type = TipoOperacion.ELIMINAR,
                    entityType = TipoEntidad.FICHA,
                    entityId = entityId,
                    // La operación conserva la última versión de la ficha: si el borrado falla, no se pierde
                    data = existente.datos,
                    createdAt = clock.millis(),
                    status = EstadoOperacion.PENDIENTE,
                    baseVersion = existente.version
                )
            )
            existente
        }
        // TODO requestSync() cuando exista el motor de sincronización (fuera de la transacción)
        eliminada?.let { ResultadoEliminacion.Eliminada(it.nombre) } ?: ResultadoEliminacion.NoEncontrada
    } catch (_: SQLiteException) {
        ResultadoEliminacion.ErrorAlmacenamiento
    }
}
