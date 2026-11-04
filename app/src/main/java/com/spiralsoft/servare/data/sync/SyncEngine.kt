package com.spiralsoft.servare.data.sync

import android.database.sqlite.SQLiteException
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
import com.spiralsoft.servare.data.local.dao.SyncInicialDao
import com.spiralsoft.servare.data.local.entity.OperationEntity
import com.spiralsoft.servare.data.remote.ResultadoEnvio
import com.spiralsoft.servare.data.remote.ResultadoPull
import com.spiralsoft.servare.data.sesion.GestorSesion
import com.spiralsoft.servare.notification.ServicioNotificacionEstado
import com.spiralsoft.servare.domain.model.CausaConflicto
import com.spiralsoft.servare.domain.model.EstadoOperacion
import com.spiralsoft.servare.domain.model.EstadoSesion
import com.spiralsoft.servare.domain.model.TipoEntidad
import com.spiralsoft.servare.domain.model.TipoOperacion
import com.spiralsoft.servare.domain.repository.ResultadoRefresco

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
    private val eliminacionDao: EliminacionDao,
    private val syncInicialDao: SyncInicialDao,
    private val adaptadores: Adaptadores,
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

        purgarTerminadas()

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
        val descarga = descargar()
        val errorPull = when (val pull = descarga.pull) {
            is ResultadoPull.Datos<*> -> null
            ResultadoPull.SesionExpirada -> return ResultadoCiclo.SesionExpirada
            ResultadoPull.SinServidor -> return ResultadoCiclo.SinServidor
            is ResultadoPull.Fallo -> pull.motivo
        }
        notificaciones.eliminadosEnServidor(descarga.eliminadas)

        val reintentoOperaciones = operationDao.proximoIntento()?.let { it - clock.millis() }
        // Mientras la descarga inicial no termine la lista está vacía sin explicación definitiva:
        // si falló sin ser por la red, no se espera a la siguiente acción del usuario
        val reintentoDescarga = ESPERA_DESCARGA_INICIAL_MS
            .takeIf { errorPull != null && !descargaInicialCompletada() }
        ResultadoCiclo.Terminado(listOfNotNull(reintentoOperaciones, reintentoDescarga).minOrNull(), errorPull)
    }

    // Retención de la cola: es mantenimiento local, no depende del servidor.
    // Si falla no impide sincronizar, se reintenta en el siguiente ciclo
    private suspend fun purgarTerminadas() {
        try {
            operationDao.purgarTerminadasAntesDe(clock.millis() - RETENCION_MS)
        } catch (_: SQLiteException) {
        }
    }

    // Refresco manual del usuario: solo pull, sin enviar la cola.
    // Es seguro sin push previo porque un registro con operaciones sin terminar no se sobrescribe.
    // Corre bajo el mismo candado que el ciclo, nunca a la vez
    suspend fun refrescar(): ResultadoRefresco = candado.withLock {
        if (gestorSesion.estado.value !is EstadoSesion.ConSesion) return ResultadoRefresco.SinSesion
        if (!monitor.confirmarAlcanzabilidad()) return ResultadoRefresco.SinServidor
        val descarga = descargar()
        when (descarga.pull) {
            is ResultadoPull.Datos<*> -> ResultadoRefresco.Actualizada(descarga.eliminadas)
            ResultadoPull.SesionExpirada -> ResultadoRefresco.SinSesion
            ResultadoPull.SinServidor -> ResultadoRefresco.SinServidor
            is ResultadoPull.Fallo -> ResultadoRefresco.ErrorDescarga
        }
    }

    // Descarga y aplica los registros del servidor, de cada tipo de entidad en orden.
    // Las consecuencias de perder la sesión o el servidor se aplican aquí,
    // quien llama solo decide cómo informarlas
    private suspend fun descargar(): Descarga {
        var primerFallo: ResultadoPull.Fallo? = null
        val eliminadas = mutableListOf<String>()
        for (adaptador in adaptadores.todos) {
            val descarga = adaptador.descargar()
            eliminadas += descarga.eliminadas
            when (val pull = descarga.pull) {
                // Sin sesión o sin servidor, los demás tipos tampoco tendrían respuesta
                ResultadoPull.SesionExpirada -> {
                    gestorSesion.expirar()
                    return Descarga(pull, eliminadas)
                }
                ResultadoPull.SinServidor -> {
                    monitor.reportarServidorNoAlcanzable()
                    return Descarga(pull, eliminadas)
                }
                // Si una de las descargas falló se informa, aunque las otras sí se hayan aplicado
                is ResultadoPull.Fallo -> if (primerFallo == null) primerFallo = pull
                is ResultadoPull.Datos<*> -> Unit
            }
        }
        return Descarga(primerFallo ?: ResultadoPull.Datos(emptyList<Nothing>()), eliminadas)
    }

    // Hasta que se descarguen todos los tipos la lista de alguno está vacía sin explicación definitiva
    private suspend fun descargaInicialCompletada() =
        TipoEntidad.entries.all { syncInicialDao.estaCompletada(it) }

    private suspend fun procesar(op: OperationEntity, conProblema: MutableList<OperacionConProblema>): Siguiente {
        operationDao.marcarProcesando(op.operationId, clock.millis())
        when (val resultado = adaptadores[op.entityType].enviar(op)) {
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
                if (intentos >= PoliticaReintentos.MAX_INTENTOS) {
                    val motivo = "${resultado.motivo}. Se agotaron los ${PoliticaReintentos.MAX_INTENTOS} intentos"
                    fallar(op, intentos, motivo)
                    conProblema += problema(op, EstadoOperacion.FALLIDA, motivo)
                } else {
                    operationDao.marcarReintentable(
                        op.operationId, intentos, clock.millis() + PoliticaReintentos.espera(intentos), resultado.motivo
                    )
                }
                if (resultado.sinRespuesta) {
                    monitor.reportarServidorNoAlcanzable()
                    return Siguiente.DETENER_SIN_SERVIDOR
                }
            }

            is ResultadoEnvio.Conflicto -> {
                // Se guarda el registro vigente del servidor: el usuario podrá compararlo con el suyo
                database.withTransaction {
                    operationDao.marcarConflicto(op.operationId, resultado.motivo, resultado.causa, resultado.actual)
                    bloquearDependientes(op)
                }
                conProblema += problema(op, EstadoOperacion.CONFLICTO, resultado.motivo, resultado.causa)
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

    // Lo que el servicio de notificación necesita para nombrar el registro afectado
    private suspend fun problema(
        op: OperationEntity,
        estado: EstadoOperacion,
        motivo: String,
        causa: CausaConflicto? = null
    ) = OperacionConProblema(
        operationId = op.operationId,
        type = op.type,
        status = estado,
        entityType = op.entityType,
        entityId = op.entityId,
        lastError = motivo,
        conflictCause = causa,
        titulo = adaptadores[op.entityType].titulo(op.entityId)
    )

    // Completada, nueva versión y encadenado del baseVersion de las siguientes, en una transacción
    private suspend fun confirmar(op: OperationEntity, version: Int?) = database.withTransaction {
        operationDao.marcarCompletada(op.operationId, clock.millis())
        if (op.type == TipoOperacion.ELIMINAR) {
            // El servidor confirmó el borrado: ahora sí se elimina el registro
            adaptadores[op.entityType].borrar(op.entityId)
            eliminacionDao.delete(op.entityType, op.entityId)
        } else if (version != null) {
            adaptadores[op.entityType].actualizarVersion(op.entityId, version)
            operationDao.encadenarBaseVersion(op.entityType, op.entityId, op.createdAt, version)
        }
    }

    private suspend fun fallar(op: OperationEntity, intentos: Int, motivo: String) = database.withTransaction {
        operationDao.marcarFallida(op.operationId, intentos, motivo)
        bloquearDependientes(op)
    }

    private suspend fun bloquearDependientes(op: OperationEntity) =
        operationDao.bloquearDependientes(op.operationId, op.entityType, op.entityId, op.createdAt)

    private companion object {
        const val ESPERA_DESCARGA_INICIAL_MS = 60_000L
        const val RETENCION_MS = 7 * 24 * 60 * 60 * 1000L // COMPLETADA y DESCARTADA, docs/2.2.md
    }
}
