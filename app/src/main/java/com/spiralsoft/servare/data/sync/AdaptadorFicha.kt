package com.spiralsoft.servare.data.sync

import kotlinx.serialization.json.Json
import java.time.Clock
import javax.inject.Inject
import com.spiralsoft.servare.data.local.AppDatabase
import com.spiralsoft.servare.data.local.dao.EliminacionDao
import com.spiralsoft.servare.data.local.dao.FichaCompleta
import com.spiralsoft.servare.data.local.dao.FichaDao
import com.spiralsoft.servare.data.local.dao.OperationDao
import com.spiralsoft.servare.data.local.dao.SyncInicialDao
import com.spiralsoft.servare.data.local.dao.aCompleta
import com.spiralsoft.servare.data.local.dao.aFicha
import com.spiralsoft.servare.data.local.dao.mismoContenido
import com.spiralsoft.servare.data.remote.FichaRemota
import com.spiralsoft.servare.data.remote.FichaRemoteDataSource
import com.spiralsoft.servare.data.remote.ResultadoEnvio
import com.spiralsoft.servare.data.remote.ResultadoPull
import com.spiralsoft.servare.data.remote.aFicha
import com.spiralsoft.servare.domain.model.TipoEntidad
import com.spiralsoft.servare.domain.model.compararFichas

class AdaptadorFicha @Inject constructor(
    private val fichaDao: FichaDao,
    private val remoto: FichaRemoteDataSource,
    private val json: Json,
    database: AppDatabase,
    operationDao: OperationDao,
    eliminacionDao: EliminacionDao,
    syncInicialDao: SyncInicialDao,
    clock: Clock
) : AdaptadorBase<FichaCompleta, FichaRemota>(
    TipoEntidad.FICHA, database, operationDao, eliminacionDao, syncInicialDao, clock
) {
    // Con sus tablas, para enviarla o compararla con la del servidor
    override suspend fun leer(entityId: String) = fichaDao.findCompletaById(entityId)

    override suspend fun crear(operationId: String, local: FichaCompleta): ResultadoEnvio =
        remoto.crear(operationId, local)

    override suspend fun actualizar(operationId: String, local: FichaCompleta, baseVersion: Int?): ResultadoEnvio =
        remoto.actualizar(operationId, local, baseVersion)

    override suspend fun eliminar(operationId: String, entityId: String, baseVersion: Int?): ResultadoEnvio =
        remoto.eliminar(operationId, entityId, baseVersion)

    override suspend fun listar(): ResultadoPull<FichaRemota> = remoto.listar()

    override fun idDe(remota: FichaRemota) = remota.entityId

    override suspend fun aplicar(remota: FichaRemota) {
        val local = fichaDao.findCompletaById(remota.entityId)
        // Sin versión del servidor se conserva la que ya se conocía
        val nueva = remota.datos.aFicha().aCompleta(
            remota.entityId, remota.createdAt, remota.version ?: local?.ficha?.version
        )
        if (local == null || !nueva.mismoContenido(local)) fichaDao.upsert(nueva)
    }

    // Una ficha sin nombre no tiene cómo nombrarse en el aviso
    override suspend fun conocidos() = fichaDao.confirmadasPorElServidor()
        .map { Conocido(it.entityId, it.nombre.ifBlank { "Ficha sin nombre" }) }

    override suspend fun titulo(entityId: String) = fichaDao.findById(entityId)?.nombre

    override suspend fun existe(entityId: String) = fichaDao.findById(entityId) != null

    override suspend fun borrar(entityId: String) = fichaDao.deleteById(entityId)

    override suspend fun actualizarVersion(entityId: String, version: Int) =
        fichaDao.actualizarVersion(entityId, version)

    override suspend fun limpiarVersion(entityId: String) = fichaDao.limpiarVersion(entityId)

    override fun leerServidor(entityId: String, snapshot: String): RegistroServidor? {
        val servidor = try {
            json.decodeFromString<FichaRemota>(snapshot)
        } catch (_: IllegalArgumentException) {
            return null // JSON mal formado (SerializationException es una IllegalArgumentException)
        }
        return RegistroServidor(
            version = servidor.version,
            // Si la ficha ya no está en el dispositivo se compara contra una vacía
            comparacion = {
                compararFichas(fichaDao.findCompletaById(entityId)?.aFicha(), servidor.datos.aFicha())
            },
            aplicar = {
                fichaDao.upsert(servidor.datos.aFicha().aCompleta(entityId, servidor.createdAt, servidor.version))
            }
        )
    }
}
