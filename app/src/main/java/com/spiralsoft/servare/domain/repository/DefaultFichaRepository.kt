package com.spiralsoft.servare.domain.repository

import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteFullException
import androidx.room.withTransaction
import com.spiralsoft.servare.data.local.AppDatabase
import com.spiralsoft.servare.data.local.dao.FichaDao
import com.spiralsoft.servare.data.local.entity.FichaEntity
import com.spiralsoft.servare.data.local.entity.toResumen
import com.spiralsoft.servare.domain.model.Ficha
import com.spiralsoft.servare.domain.model.FichaResumen
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

class DefaultFichaRepository @Inject constructor(
    private val database: AppDatabase,
    private val fichaDao: FichaDao,
    private val clock: Clock
) : FichaRepository {

    override fun observarResumenes(): Flow<List<FichaResumen>> =
        fichaDao.observeAll().map { fichas -> fichas.map { it.toResumen() } }

    override suspend fun guardar(entityId: String?, ficha: Ficha): ResultadoGuardado {
        if (ficha.nombre.isBlank()) return ResultadoGuardado.FaltaNombre

        val id = entityId ?: UUID.randomUUID().toString()
        val ahora = Instant.now(clock)
        return try {
            database.withTransaction {
                val existente = fichaDao.findById(id)
                fichaDao.upsert(
                    FichaEntity(
                        entityId = id,
                        datos = ficha,
                        createdAt = existente?.createdAt ?: ahora,
                        updatedAt = ahora
                    )
                )
                // TODO: OperationEntity PENDIENTE aquí (misma transacción); requestSync() fuera de ella
            }
            ResultadoGuardado.Guardada(id)
        } catch (e: SQLiteFullException) {
            ResultadoGuardado.SinEspacio
        } catch (e: SQLiteException) {
            ResultadoGuardado.ErrorAlmacenamiento
        }
    }

    override suspend fun obtener(entityId: String): ResultadoCarga = try {
        fichaDao.findById(entityId)?.let { ResultadoCarga.Encontrada(it.datos) } ?: ResultadoCarga.NoEncontrada
    } catch (e: SQLiteException) {
        ResultadoCarga.ErrorAlmacenamiento
    }

    override suspend fun eliminar(entityId: String): ResultadoEliminacion = try {
        val borradas = database.withTransaction {
            fichaDao.deleteById(entityId)
            // TODO: OperationEntity DELETE PENDIENTE aquí (misma transacción); requestSync() fuera de ella
        }
        if (borradas > 0) ResultadoEliminacion.Eliminada else ResultadoEliminacion.NoEncontrada
    } catch (e: SQLiteException) {
        ResultadoEliminacion.ErrorAlmacenamiento
    }
}
