package com.spiralsoft.servare.data.sync

import kotlinx.serialization.json.Json
import java.time.Clock
import javax.inject.Inject
import com.spiralsoft.servare.data.local.AppDatabase
import com.spiralsoft.servare.data.local.dao.EliminacionDao
import com.spiralsoft.servare.data.local.dao.OperationDao
import com.spiralsoft.servare.data.local.dao.PruebaDao
import com.spiralsoft.servare.data.local.dao.SyncInicialDao
import com.spiralsoft.servare.data.local.entity.PruebaEntity
import com.spiralsoft.servare.data.remote.PruebaRemota
import com.spiralsoft.servare.data.remote.PruebaRemoteDataSource
import com.spiralsoft.servare.data.remote.ResultadoEnvio
import com.spiralsoft.servare.data.remote.ResultadoPull
import com.spiralsoft.servare.domain.model.CampoComparado
import com.spiralsoft.servare.domain.model.TipoEntidad

class AdaptadorPrueba @Inject constructor(
    private val pruebaDao: PruebaDao,
    private val remoto: PruebaRemoteDataSource,
    private val json: Json,
    database: AppDatabase,
    operationDao: OperationDao,
    eliminacionDao: EliminacionDao,
    syncInicialDao: SyncInicialDao,
    clock: Clock
) : AdaptadorBase<PruebaEntity, PruebaRemota>(
    TipoEntidad.PRUEBA, database, operationDao, eliminacionDao, syncInicialDao, clock
) {
    override suspend fun leer(entityId: String) = pruebaDao.findById(entityId)

    override suspend fun crear(operationId: String, local: PruebaEntity): ResultadoEnvio =
        remoto.crear(operationId, local)

    override suspend fun actualizar(operationId: String, local: PruebaEntity, baseVersion: Int?): ResultadoEnvio =
        remoto.actualizar(operationId, local, baseVersion)

    override suspend fun eliminar(operationId: String, entityId: String, baseVersion: Int?): ResultadoEnvio =
        remoto.eliminar(operationId, entityId, baseVersion)

    override suspend fun listar(): ResultadoPull<PruebaRemota> = remoto.listar()

    override fun idDe(remota: PruebaRemota) = remota.entityId

    override suspend fun aplicar(remota: PruebaRemota) {
        val local = pruebaDao.findById(remota.entityId)
        val nueva = PruebaEntity(
            entityId = remota.entityId,
            titulo = remota.titulo,
            texto = remota.texto,
            numero = remota.numero,
            createdAt = remota.createdAt,
            // Sin versión del servidor se conserva la que ya se conocía
            version = remota.version ?: local?.version
        )
        if (nueva != local) pruebaDao.upsert(nueva)
    }

    override suspend fun conocidos() = pruebaDao.confirmadasPorElServidor().map { Conocido(it.entityId, it.titulo) }

    override suspend fun titulo(entityId: String) = pruebaDao.findById(entityId)?.titulo

    override suspend fun borrar(entityId: String) = pruebaDao.deleteById(entityId)

    override suspend fun actualizarVersion(entityId: String, version: Int) =
        pruebaDao.actualizarVersion(entityId, version)

    override suspend fun limpiarVersion(entityId: String) = pruebaDao.limpiarVersion(entityId)

    override fun leerServidor(entityId: String, snapshot: String): RegistroServidor? {
        val servidor = try {
            json.decodeFromString<PruebaRemota>(snapshot)
        } catch (_: IllegalArgumentException) {
            return null // JSON mal formado (SerializationException es una IllegalArgumentException)
        }
        return RegistroServidor(
            version = servidor.version,
            comparacion = {
                val local = pruebaDao.findById(entityId)
                listOf(
                    CampoComparado("Título", local?.titulo, servidor.titulo),
                    CampoComparado("Texto", local?.texto, servidor.texto),
                    CampoComparado("Número", local?.numero?.toString(), servidor.numero.toString())
                )
            },
            aplicar = {
                pruebaDao.upsert(
                    PruebaEntity(
                        entityId = entityId,
                        titulo = servidor.titulo,
                        texto = servidor.texto,
                        numero = servidor.numero,
                        createdAt = servidor.createdAt,
                        version = servidor.version
                    )
                )
            }
        )
    }
}
