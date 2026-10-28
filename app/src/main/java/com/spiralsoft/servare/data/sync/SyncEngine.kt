package com.spiralsoft.servare.data.sync

import androidx.room.withTransaction
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import com.spiralsoft.servare.data.connectivity.ConnectivityMonitor
import com.spiralsoft.servare.data.local.AppDatabase
import com.spiralsoft.servare.data.local.dao.EliminacionDao
import com.spiralsoft.servare.data.local.dao.OperacionConProblema
import com.spiralsoft.servare.data.local.dao.OperationDao
import com.spiralsoft.servare.data.local.dao.PruebaDao
import com.spiralsoft.servare.data.local.dao.SyncInicialDao
import com.spiralsoft.servare.data.local.entity.OperationEntity
import com.spiralsoft.servare.data.local.entity.PruebaEntity
import com.spiralsoft.servare.data.local.entity.SyncInicialEntity
import com.spiralsoft.servare.data.remote.PruebaRemota
import com.spiralsoft.servare.data.remote.PruebaRemoteDataSource
import com.spiralsoft.servare.data.remote.ResultadoEnvio
import com.spiralsoft.servare.data.remote.ResultadoPull
import com.spiralsoft.servare.data.sesion.GestorSesion
import com.spiralsoft.servare.notification.ServicioNotificacionEstado
import com.spiralsoft.servare.domain.model.EstadoOperacion
import com.spiralsoft.servare.domain.model.EstadoSesion
import com.spiralsoft.servare.domain.model.TipoEntidad
import com.spiralsoft.servare.domain.model.TipoOperacion

sealed interface ResultadoCiclo {
    // reintentoEnMs: cuándo vence el siguiente REINTENTABLE, si queda alguno
    // errorPull: motivo si el pull falló sin ser culpa de ninguna operación
    data class Terminado(val reintentoEnMs: Long?, val errorPull: String? = null) : ResultadoCiclo
    data object SinServidor : ResultadoCiclo      // el monitor lanzará otro ciclo al recuperarse
    data object SesionExpirada : ResultadoCiclo   // cola pausada, las operaciones siguen PENDIENTE
}

// Motor de sincronización: vacía la cola (push) respetando orden y dependencias
// y después descarga los registros del servidor (pull)
@Singleton
class SyncEngine @Inject constructor(
    private val database: AppDatabase,
    private val operationDao: OperationDao,
    private val pruebaDao: PruebaDao,
    private val eliminacionDao: EliminacionDao,
    private val syncInicialDao: SyncInicialDao,
    private val remoto: PruebaRemoteDataSource,
    private val monitor: ConnectivityMonitor,
    private val gestorSesion: GestorSesion,
    private val notificaciones: ServicioNotificacionEstado,
    private val clock: Clock
) {
    // Segunda barrera del single-flight: el ciclo normal y el de reintento usan colas de trabajo distintas
    private val candado = Mutex()

    private enum class Siguiente { CONTINUAR, CONFIRMADA, DETENER_SIN_SERVIDOR, DETENER_SESION }

    // Ningún ciclo corre ni empieza mientras dura el bloque. Lo usa el cierre de sesión para borrar
    // los datos locales sin que un ciclo en vuelo los vuelva a llenar
    suspend fun <T> pausado(bloque: suspend () -> T): T = candado.withLock { bloque() }

    suspend fun ejecutarCiclo(): ResultadoCiclo = candado.withLock {
        // Sin sesión no se sincroniza: la cola queda como está hasta que el usuario inicie sesión
        // (al hacerlo, el gestor de sesión pide un ciclo)
        if (gestorSesion.estado.value !is EstadoSesion.ConSesion) return ResultadoCiclo.SesionExpirada

        if (!monitor.confirmarAlcanzabilidad()) return ResultadoCiclo.SinServidor

        // Interrumpidas a mitad de envío: se reenvían con el mismo operationId
        operationDao.recuperarHuerfanas()
        operationDao.liberarReintentables(clock.millis())

        // Resultado de cada operación finalizada: se avisa al terminar la cola, aunque el ciclo se corte a la mitad.
        // Además de este aviso, las FALLIDA y CONFLICTO quedan en la cola y el servicio las lee de ahí
        var confirmadas = 0
        val conProblema = mutableListOf<OperacionConProblema>()
        try {
            while (true) {
                val operacion = operationDao.siguienteElegible() ?: break
                when (procesar(operacion, conProblema)) {
                    Siguiente.CONTINUAR -> Unit
                    Siguiente.CONFIRMADA -> confirmadas++
                    Siguiente.DETENER_SIN_SERVIDOR -> return ResultadoCiclo.SinServidor
                    Siguiente.DETENER_SESION -> {
                        gestorSesion.expirar()
                        return ResultadoCiclo.SesionExpirada
                    }
                }
            }
        } finally {
            notificaciones.operacionesFinalizadas(confirmadas, conProblema)
        }

        // Push antes de pull: el servidor ya conoce los cambios locales cuando envía los suyos
        var errorPull: String? = null
        when (val pull = remoto.listar()) {
            is ResultadoPull.Datos -> aplicarPull(pull.pruebas)
            ResultadoPull.SesionExpirada -> {
                gestorSesion.expirar()
                return ResultadoCiclo.SesionExpirada
            }
            ResultadoPull.SinServidor -> {
                monitor.reportarServidorNoAlcanzable()
                return ResultadoCiclo.SinServidor
            }
            is ResultadoPull.Fallo -> errorPull = pull.motivo
        }

        val reintentoOperaciones = operationDao.proximoIntento()?.let { it - clock.millis() }
        // Mientras la descarga inicial no termine la lista está vacía sin explicación definitiva:
        // si falló sin ser por la red, no se espera a la siguiente acción del usuario
        val reintentoDescarga = ESPERA_DESCARGA_INICIAL_MS
            .takeIf { errorPull != null && !syncInicialDao.estaCompletada(TipoEntidad.PRUEBA) }
        ResultadoCiclo.Terminado(listOfNotNull(reintentoOperaciones, reintentoDescarga).minOrNull(), errorPull)
    }

    // Un registro con operaciones sin terminar no se sobrescribe. Leer qué está protegido y escribir
    // van en la misma transacción: un guardado del usuario que llegue en medio no se pisa.
    // Pendiente (el servidor aún no lo ofrece): versión y marcas de borrado, para el pull incremental,
    // detectar conflictos con versión distinta y quitar lo borrado en el servidor
    private suspend fun aplicarPull(remotas: List<PruebaRemota>) = database.withTransaction {
        val protegidas = operationDao.entidadesConOperacionesSinTerminar(TipoEntidad.PRUEBA).toSet() +
            eliminacionDao.entidadesEliminadas(TipoEntidad.PRUEBA)
        for ((entityId, titulo, texto, numero, createdAt, version) in remotas) {
            if (entityId in protegidas) continue
            val local = pruebaDao.findById(entityId)
            val nueva = PruebaEntity(
                entityId = entityId,
                titulo = titulo,
                texto = texto,
                numero = numero,
                createdAt = createdAt,
                // Sin versión del servidor se conserva la que ya se conocía
                version = version ?: local?.version
            )
            if (nueva != local) pruebaDao.upsert(nueva)
        }
        // Una lista vacía también es una descarga completa: el servidor no tiene registros
        syncInicialDao.marcarCompletada(SyncInicialEntity(TipoEntidad.PRUEBA, clock.millis()))
    }

    private suspend fun procesar(op: OperationEntity, conProblema: MutableList<OperacionConProblema>): Siguiente {
        operationDao.marcarProcesando(op.operationId, clock.millis())
        when (val resultado = enviar(op)) {
            is ResultadoEnvio.Confirmada -> {
                confirmar(op, resultado.version)
                return Siguiente.CONFIRMADA
            }

            ResultadoEnvio.Incierto -> {
                // Se perdió la conexión: no cuenta como intento
                operationDao.marcarIncierta(op.operationId)
                monitor.reportarServidorNoAlcanzable()
                return Siguiente.DETENER_SIN_SERVIDOR
            }

            is ResultadoEnvio.Temporal -> {
                val intentos = op.attemptCount + 1
                if (intentos >= MAX_INTENTOS) {
                    val motivo = "${resultado.motivo}. Se agotaron los $MAX_INTENTOS intentos"
                    fallar(op, intentos, motivo)
                    conProblema += problema(op, EstadoOperacion.FALLIDA, motivo)
                } else {
                    operationDao.marcarReintentable(
                        op.operationId, intentos, clock.millis() + espera(intentos), resultado.motivo
                    )
                }
                if (resultado.sinRespuesta) {
                    monitor.reportarServidorNoAlcanzable()
                    return Siguiente.DETENER_SIN_SERVIDOR
                }
            }

            is ResultadoEnvio.Conflicto -> {
                database.withTransaction {
                    operationDao.marcarConflicto(op.operationId, resultado.motivo)
                    bloquearDependientes(op)
                }
                conProblema += problema(op, EstadoOperacion.CONFLICTO, resultado.motivo)
            }

            is ResultadoEnvio.Definitivo -> {
                fallar(op, op.attemptCount, resultado.motivo)
                conProblema += problema(op, EstadoOperacion.FALLIDA, resultado.motivo)
            }

            ResultadoEnvio.SesionExpirada -> {
                // No es culpa de la operación: queda como estaba y se pausa toda la cola
                operationDao.marcarPendiente(op.operationId)
                return Siguiente.DETENER_SESION
            }
        }
        return Siguiente.CONTINUAR
    }

    // El contenido se lee de la tabla en el momento del envío: siempre va lo más reciente
    private suspend fun enviar(op: OperationEntity): ResultadoEnvio = when (op.entityType) {
        TipoEntidad.PRUEBA -> when (op.type) {
            TipoOperacion.CREAR -> pruebaDao.findById(op.entityId)
                ?.let { remoto.crear(op.operationId, it) } ?: registroAusente()
            TipoOperacion.ACTUALIZAR -> pruebaDao.findById(op.entityId)
                ?.let { remoto.actualizar(op.operationId, it, op.baseVersion) } ?: registroAusente()
            TipoOperacion.ELIMINAR -> remoto.eliminar(op.operationId, op.entityId, op.baseVersion)
            TipoOperacion.ADJUNTAR -> ResultadoEnvio.Definitivo("Los adjuntos aún no se pueden enviar")
        }
    }

    // Lo que el servicio de notificación necesita para nombrar el registro afectado
    private suspend fun problema(op: OperationEntity, estado: EstadoOperacion, motivo: String) =
        OperacionConProblema(
            operationId = op.operationId,
            type = op.type,
            status = estado,
            entityType = op.entityType,
            entityId = op.entityId,
            lastError = motivo,
            titulo = when (op.entityType) {
                TipoEntidad.PRUEBA -> pruebaDao.findById(op.entityId)?.titulo
            }
        )

    private fun registroAusente() = ResultadoEnvio.Definitivo("El registro ya no está en el dispositivo")

    // Completada, nueva versión y encadenado del baseVersion de las siguientes, en una transacción
    private suspend fun confirmar(op: OperationEntity, version: Int?) = database.withTransaction {
        operationDao.marcarCompletada(op.operationId)
        when (op.entityType) {
            TipoEntidad.PRUEBA ->
                if (op.type == TipoOperacion.ELIMINAR) {
                    // El servidor confirmó el borrado: ahora sí se elimina el registro
                    pruebaDao.deleteById(op.entityId)
                    eliminacionDao.delete(op.entityType, op.entityId)
                } else if (version != null) {
                    pruebaDao.actualizarVersion(op.entityId, version)
                    operationDao.encadenarBaseVersion(op.entityType, op.entityId, op.createdAt, version)
                }
        }
    }

    private suspend fun fallar(op: OperationEntity, intentos: Int, motivo: String) = database.withTransaction {
        operationDao.marcarFallida(op.operationId, intentos, motivo)
        bloquearDependientes(op)
    }

    private suspend fun bloquearDependientes(op: OperationEntity) =
        operationDao.bloquearDependientes(op.operationId, op.entityType, op.entityId, op.createdAt)

    private companion object {
        const val MAX_INTENTOS = 5
        const val ESPERA_BASE_MS = 10_000L
        const val ESPERA_MAX_MS = 5 * 60 * 60 * 1000L
        const val ESPERA_DESCARGA_INICIAL_MS = 60_000L

        // min(5 h, 10 s × 2^(n−1))
        fun espera(intento: Int): Long =
            (ESPERA_BASE_MS shl (intento - 1).coerceAtMost(20)).coerceAtMost(ESPERA_MAX_MS)
    }
}
