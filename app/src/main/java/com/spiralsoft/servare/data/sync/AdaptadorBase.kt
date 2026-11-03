package com.spiralsoft.servare.data.sync

import androidx.room.withTransaction
import java.time.Clock
import com.spiralsoft.servare.data.local.AppDatabase
import com.spiralsoft.servare.data.local.dao.EliminacionDao
import com.spiralsoft.servare.data.local.dao.OperationDao
import com.spiralsoft.servare.data.local.dao.SyncInicialDao
import com.spiralsoft.servare.data.local.entity.OperationEntity
import com.spiralsoft.servare.data.local.entity.SyncInicialEntity
import com.spiralsoft.servare.data.remote.ResultadoEnvio
import com.spiralsoft.servare.data.remote.ResultadoPull
import com.spiralsoft.servare.domain.model.TipoEntidad
import com.spiralsoft.servare.domain.model.TipoOperacion

// Lo común a todos los tipos de registro: qué operación se envía y cómo, y la protección del pull.
// L es el registro local tal como se envía, R el registro remoto tal como lo devuelve el servidor
abstract class AdaptadorBase<L : Any, R : Any>(
    final override val tipo: TipoEntidad,
    private val database: AppDatabase,
    private val operationDao: OperationDao,
    private val eliminacionDao: EliminacionDao,
    private val syncInicialDao: SyncInicialDao,
    private val clock: Clock
) : AdaptadorEntidad {

    // Un registro que el servidor ya conoció, con el nombre con que se avisa si desaparece de allí
    protected class Conocido(val entityId: String, val titulo: String)

    // Registro local, incluidas las marcadas como eliminadas: un CREAR pendiente debe enviarse
    // aunque se haya borrado después
    protected abstract suspend fun leer(entityId: String): L?
    protected abstract suspend fun crear(operationId: String, local: L): ResultadoEnvio
    protected abstract suspend fun actualizar(operationId: String, local: L, baseVersion: Int?): ResultadoEnvio
    protected abstract suspend fun eliminar(operationId: String, entityId: String, baseVersion: Int?): ResultadoEnvio

    protected abstract suspend fun listar(): ResultadoPull<R>
    protected abstract fun idDe(remota: R): String

    // Guarda el registro del servidor si difiere del local. Corre dentro de la transacción del pull
    protected abstract suspend fun aplicar(remota: R)

    // Los que el servidor ya conoció: si dejan de venir en el pull, se borraron allí.
    // Los que nunca confirmó no se incluyen
    protected abstract suspend fun conocidos(): List<Conocido>

    override suspend fun existe(entityId: String) = leer(entityId) != null

    override suspend fun enviar(op: OperationEntity): ResultadoEnvio = when (op.type) {
        TipoOperacion.CREAR -> leer(op.entityId)?.let { crear(op.operationId, it) } ?: registroAusente()
        TipoOperacion.ACTUALIZAR ->
            leer(op.entityId)?.let { actualizar(op.operationId, it, op.baseVersion) } ?: registroAusente()
        TipoOperacion.ELIMINAR -> eliminar(op.operationId, op.entityId, op.baseVersion)
        TipoOperacion.ADJUNTAR -> ResultadoEnvio.Definitivo("Los adjuntos aún no se pueden enviar")
    }

    override suspend fun descargar(): Descarga = when (val pull = listar()) {
        is ResultadoPull.Datos -> Descarga(pull, aplicarPull(pull.registros))
        else -> Descarga(pull)
    }

    // Un registro con operaciones sin terminar no se sobrescribe. Leer qué está protegido y escribir
    // van en la misma transacción: un guardado del usuario que llegue en medio no se pisa.
    // El pull trae la lista completa, así que lo que el servidor ya conocía y deja de venir está borrado allí:
    // se quita del dispositivo y se devuelven sus títulos para avisar al usuario.
    // Pendiente (el servidor aún no lo ofrece): marcas de borrado, para el pull incremental
    private suspend fun aplicarPull(remotas: List<R>): List<String> = database.withTransaction {
        val protegidas = operationDao.entidadesConOperacionesSinTerminar(tipo).toSet() +
            eliminacionDao.entidadesEliminadas(tipo)
        for (remota in remotas) {
            if (idDe(remota) in protegidas) continue
            aplicar(remota)
        }
        val vigentes = remotas.mapTo(HashSet()) { idDe(it) }
        val eliminadas = conocidos().filter { it.entityId !in vigentes && it.entityId !in protegidas }
        eliminadas.forEach { borrar(it.entityId) }
        // Una lista vacía también es una descarga completa: el servidor no tiene registros
        syncInicialDao.marcarCompletada(SyncInicialEntity(tipo, clock.millis()))
        eliminadas.map { it.titulo }
    }

    private fun registroAusente() = ResultadoEnvio.Definitivo("El registro ya no está en el dispositivo")
}
