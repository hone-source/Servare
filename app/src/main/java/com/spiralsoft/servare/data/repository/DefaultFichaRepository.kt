package com.spiralsoft.servare.data.repository

import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteFullException
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.time.Clock
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import com.spiralsoft.servare.data.local.AppDatabase
import com.spiralsoft.servare.data.local.dao.FichaDao
import com.spiralsoft.servare.data.local.entity.FichaEntity
import com.spiralsoft.servare.data.local.entity.toResumen
import com.spiralsoft.servare.domain.model.Ficha
import com.spiralsoft.servare.domain.model.FichaResumen
import com.spiralsoft.servare.domain.repository.FichaRepository
import com.spiralsoft.servare.domain.repository.ResultadoCarga
import com.spiralsoft.servare.domain.repository.ResultadoEliminacion
import com.spiralsoft.servare.domain.repository.ResultadoGuardado

class DefaultFichaRepository @Inject constructor(
    private val database: AppDatabase,
    private val fichaDao: FichaDao,
    private val json: Json,
    private val clock: Clock
) : FichaRepository {

    override fun observarResumenes(): Flow<List<FichaResumen>> =
        fichaDao.observeAll().map { fichas -> fichas.map { it.toResumen() } }

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
            }
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
            true
        }
        if (eliminada) ResultadoEliminacion.Eliminada else ResultadoEliminacion.NoEncontrada
    } catch (_: SQLiteException) {
        ResultadoEliminacion.ErrorAlmacenamiento
    }

    override suspend fun aplicarRemota(entityId: String, ficha: Ficha, version: Int?) {
        database.withTransaction {
            // Push antes de pull: lo que aún está en la cola manda sobre lo que trae el servidor
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
                    val existente = fichaDao.findById(id) ?: continue
                    fichaDao.deleteById(id)
                    add(existente.datos.nombre.ifBlank { null })
                }
            }
        }

}
