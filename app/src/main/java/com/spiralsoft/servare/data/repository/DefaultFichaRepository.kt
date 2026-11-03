package com.spiralsoft.servare.data.repository

import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteFullException
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import com.spiralsoft.servare.data.local.AppDatabase
import com.spiralsoft.servare.data.local.dao.EliminacionDao
import com.spiralsoft.servare.data.local.dao.FichaDao
import com.spiralsoft.servare.data.local.dao.SyncInicialDao
import com.spiralsoft.servare.data.local.dao.aCompleta
import com.spiralsoft.servare.data.local.dao.aFicha
import com.spiralsoft.servare.data.local.entity.EliminacionEntity
import com.spiralsoft.servare.data.sync.SyncEngine
import com.spiralsoft.servare.data.sync.SyncScheduler
import com.spiralsoft.servare.domain.model.AccionOperacion
import com.spiralsoft.servare.domain.model.Ficha
import com.spiralsoft.servare.domain.model.FichaResumen
import com.spiralsoft.servare.domain.model.TipoEntidad
import com.spiralsoft.servare.domain.model.TipoOperacion
import com.spiralsoft.servare.domain.repository.FichaRepository
import com.spiralsoft.servare.domain.repository.ResultadoEliminacionFicha
import com.spiralsoft.servare.domain.repository.ResultadoGuardadoFicha
import com.spiralsoft.servare.domain.repository.ResultadoObtencionFicha
import com.spiralsoft.servare.domain.repository.ResultadoRefresco
import com.spiralsoft.servare.domain.repository.ResultadoResolucion

class DefaultFichaRepository @Inject constructor(
    private val database: AppDatabase,
    private val fichaDao: FichaDao,
    private val encolador: EncoladorOperaciones,
    private val eliminacionDao: EliminacionDao,
    private val syncInicialDao: SyncInicialDao,
    private val syncScheduler: SyncScheduler,
    private val syncEngine: SyncEngine,
    private val resolutor: ResolutorOperaciones,
    private val clock: Clock
) : FichaRepository {

    override fun observarResumenes(): Flow<List<FichaResumen>> = fichaDao.observarResumenes()

    override fun observarDescargaInicial(): Flow<Boolean> =
        syncInicialDao.observarCompletada(TipoEntidad.FICHA)

    override suspend fun obtener(entityId: String): ResultadoObtencionFicha = try {
        fichaDao.findCompletaActivaById(entityId)?.let { ResultadoObtencionFicha.Encontrada(it.aFicha()) }
            ?: ResultadoObtencionFicha.NoEncontrada
    } catch (_: SQLiteException) {
        ResultadoObtencionFicha.ErrorAlmacenamiento
    }

    override suspend fun guardar(ficha: Ficha): ResultadoGuardadoFicha {
        if (ficha.nombre.isBlank()) return ResultadoGuardadoFicha.FaltaNombre

        val id = UUID.randomUUID().toString()
        val ahora = clock.millis()
        return try {
            // Outbox: la ficha y su operación PENDIENTE en una sola transacción
            database.withTransaction {
                fichaDao.insert(ficha.limpia().aCompleta(id, ahora, version = null))
                encolador.encolar(TipoOperacion.CREAR, TipoEntidad.FICHA, id, createdAt = ahora)
            }
            syncScheduler.requestSync()
            ResultadoGuardadoFicha.Guardada(id)
        } catch (_: SQLiteFullException) {
            ResultadoGuardadoFicha.SinEspacio
        } catch (_: SQLiteException) {
            ResultadoGuardadoFicha.ErrorAlmacenamiento
        }
    }

    override suspend fun actualizar(entityId: String, ficha: Ficha): ResultadoGuardadoFicha {
        if (ficha.nombre.isBlank()) return ResultadoGuardadoFicha.FaltaNombre

        return try {
            // Outbox: el cambio y su operación PENDIENTE en una sola transacción
            val actualizada = database.withTransaction {
                val existente = fichaDao.findActivaById(entityId) ?: return@withTransaction false
                fichaDao.update(ficha.limpia().aCompleta(entityId, existente.createdAt, existente.version))
                encolador.encolarActualizacion(TipoEntidad.FICHA, entityId, existente.version)
                true
            }
            if (!actualizada) return ResultadoGuardadoFicha.NoEncontrada
            syncScheduler.requestSync()
            ResultadoGuardadoFicha.Guardada(entityId)
        } catch (_: SQLiteFullException) {
            ResultadoGuardadoFicha.SinEspacio
        } catch (_: SQLiteException) {
            ResultadoGuardadoFicha.ErrorAlmacenamiento
        }
    }

    override suspend fun eliminar(entityId: String): ResultadoEliminacionFicha = try {
        // Outbox: la marca de borrado y su operación PENDIENTE en una sola transacción.
        // La ficha sigue en su tabla: si el borrado falla en el servidor, no se pierde
        val eliminada = database.withTransaction {
            val existente = fichaDao.findActivaById(entityId) ?: return@withTransaction null
            val ahora = clock.millis()
            eliminacionDao.insert(EliminacionEntity(TipoEntidad.FICHA, entityId, ahora))
            encolador.encolar(
                TipoOperacion.ELIMINAR, TipoEntidad.FICHA, entityId, createdAt = ahora, baseVersion = existente.version
            )
            existente
        }
        // Al completarse el ELIMINAR, el motor borra la ficha y su eliminación en una transacción
        if (eliminada == null) ResultadoEliminacionFicha.NoEncontrada
        else {
            syncScheduler.requestSync()
            ResultadoEliminacionFicha.Eliminada(eliminada.nombre)
        }
    } catch (_: SQLiteException) {
        ResultadoEliminacionFicha.ErrorAlmacenamiento
    }

    override suspend fun refrescar(): ResultadoRefresco = syncEngine.refrescar()

    override suspend fun resolver(operationId: String, accion: AccionOperacion): ResultadoResolucion =
        resolutor.resolver(operationId, accion)

    // El nombre se guarda sin espacios sobrantes, como el título de Prueba
    private fun Ficha.limpia() = copy(nombre = nombre.trim())
}
