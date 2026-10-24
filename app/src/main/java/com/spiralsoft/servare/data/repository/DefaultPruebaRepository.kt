package com.spiralsoft.servare.data.repository

import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteFullException
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import com.spiralsoft.servare.data.local.AppDatabase
import com.spiralsoft.servare.data.local.dao.EliminacionDao
import com.spiralsoft.servare.data.local.dao.OperationDao
import com.spiralsoft.servare.data.local.dao.PruebaDao
import com.spiralsoft.servare.data.local.entity.EliminacionEntity
import com.spiralsoft.servare.data.local.entity.OperationEntity
import com.spiralsoft.servare.data.local.entity.PruebaEntity
import com.spiralsoft.servare.domain.model.EstadoOperacion
import com.spiralsoft.servare.domain.model.Prueba
import com.spiralsoft.servare.domain.model.TipoEntidad
import com.spiralsoft.servare.domain.model.TipoOperacion
import com.spiralsoft.servare.domain.repository.PruebaRepository
import com.spiralsoft.servare.domain.repository.ResultadoEliminacionPrueba
import com.spiralsoft.servare.domain.repository.ResultadoGuardadoPrueba

class DefaultPruebaRepository @Inject constructor(
    private val database: AppDatabase,
    private val pruebaDao: PruebaDao,
    private val operationDao: OperationDao,
    private val eliminacionDao: EliminacionDao,
    private val clock: Clock
) : PruebaRepository {

    override fun observarTodas(): Flow<List<Prueba>> = pruebaDao.observarTodas().map { lista ->
        lista.map { Prueba(it.entityId, it.titulo, it.texto, it.numero, it.createdAt) }
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
            // TODO requestSync() cuando exista el motor de sincronización (fuera de la transacción)
            ResultadoGuardadoPrueba.Guardada(id)
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
        // TODO al completar el ELIMINAR, el motor borra la prueba y su eliminación en una transacción
        // TODO requestSync() cuando exista el motor de sincronización (fuera de la transacción)
        eliminada?.let { ResultadoEliminacionPrueba.Eliminada(it.titulo) } ?: ResultadoEliminacionPrueba.NoEncontrada
    } catch (_: SQLiteException) {
        ResultadoEliminacionPrueba.ErrorAlmacenamiento
    }
}
