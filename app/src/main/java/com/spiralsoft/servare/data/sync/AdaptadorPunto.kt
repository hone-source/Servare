package com.spiralsoft.servare.data.sync

import kotlinx.serialization.json.Json
import java.time.Clock
import java.util.Locale
import javax.inject.Inject
import com.spiralsoft.servare.data.local.AppDatabase
import com.spiralsoft.servare.data.local.dao.EliminacionDao
import com.spiralsoft.servare.data.local.dao.OperationDao
import com.spiralsoft.servare.data.local.dao.PuntoDao
import com.spiralsoft.servare.data.local.dao.SyncInicialDao
import com.spiralsoft.servare.data.local.entity.PuntoEntity
import com.spiralsoft.servare.data.remote.PuntoRemoteDataSource
import com.spiralsoft.servare.data.remote.PuntoRemoto
import com.spiralsoft.servare.data.remote.ResultadoEnvio
import com.spiralsoft.servare.data.remote.ResultadoPull
import com.spiralsoft.servare.domain.model.CampoComparado
import com.spiralsoft.servare.domain.model.TipoEntidad

class AdaptadorPunto @Inject constructor(
    private val puntoDao: PuntoDao,
    private val remoto: PuntoRemoteDataSource,
    private val json: Json,
    database: AppDatabase,
    operationDao: OperationDao,
    eliminacionDao: EliminacionDao,
    syncInicialDao: SyncInicialDao,
    clock: Clock
) : AdaptadorBase<PuntoEntity, PuntoRemoto>(
    TipoEntidad.PUNTO, database, operationDao, eliminacionDao, syncInicialDao, clock
) {
    override suspend fun leer(entityId: String) = puntoDao.findById(entityId)

    override suspend fun crear(operationId: String, local: PuntoEntity): ResultadoEnvio =
        remoto.crear(operationId, local)

    override suspend fun actualizar(operationId: String, local: PuntoEntity, baseVersion: Int?): ResultadoEnvio =
        remoto.actualizar(operationId, local, baseVersion)

    override suspend fun eliminar(operationId: String, entityId: String, baseVersion: Int?): ResultadoEnvio =
        remoto.eliminar(operationId, entityId, baseVersion)

    override suspend fun listar(): ResultadoPull<PuntoRemoto> = remoto.listar()

    override fun idDe(remota: PuntoRemoto) = remota.entityId

    override suspend fun aplicar(remota: PuntoRemoto) {
        val local = puntoDao.findById(remota.entityId)
        val nuevo = PuntoEntity(
            entityId = remota.entityId,
            nombre = remota.nombre,
            latitud = remota.latitud,
            longitud = remota.longitud,
            createdAt = remota.createdAt,
            // Sin versión del servidor se conserva la que ya se conocía
            version = remota.version ?: local?.version
        )
        if (nuevo != local) puntoDao.upsert(nuevo)
    }

    override suspend fun conocidos() = puntoDao.confirmadosPorElServidor().map { Conocido(it.entityId, it.nombre) }

    override suspend fun titulo(entityId: String) = puntoDao.findById(entityId)?.nombre

    override suspend fun borrar(entityId: String) = puntoDao.deleteById(entityId)

    override suspend fun actualizarVersion(entityId: String, version: Int) =
        puntoDao.actualizarVersion(entityId, version)

    override suspend fun limpiarVersion(entityId: String) = puntoDao.limpiarVersion(entityId)

    override fun leerServidor(entityId: String, snapshot: String): RegistroServidor? {
        val servidor = try {
            json.decodeFromString<PuntoRemoto>(snapshot)
        } catch (_: IllegalArgumentException) {
            return null // JSON mal formado (SerializationException es una IllegalArgumentException)
        }
        return RegistroServidor(
            version = servidor.version,
            comparacion = {
                val local = puntoDao.findById(entityId)
                listOf(
                    CampoComparado("Nombre", local?.nombre, servidor.nombre),
                    CampoComparado("Latitud", local?.latitud?.aTexto(), servidor.latitud.aTexto()),
                    CampoComparado("Longitud", local?.longitud?.aTexto(), servidor.longitud.aTexto())
                )
            },
            aplicar = {
                puntoDao.upsert(
                    PuntoEntity(
                        entityId = entityId,
                        nombre = servidor.nombre,
                        latitud = servidor.latitud,
                        longitud = servidor.longitud,
                        createdAt = servidor.createdAt,
                        version = servidor.version
                    )
                )
            }
        )
    }
}

// Seis decimales (unos 10 cm), como se muestran al editar el punto
private fun Double.aTexto() = "%.6f".format(Locale.US, this)
