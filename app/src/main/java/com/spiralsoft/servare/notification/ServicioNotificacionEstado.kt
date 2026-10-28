package com.spiralsoft.servare.notification

import android.database.sqlite.SQLiteException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton
import com.spiralsoft.servare.data.connectivity.ConnectivityMonitor
import com.spiralsoft.servare.data.local.dao.OperacionConProblema
import com.spiralsoft.servare.data.local.dao.OperationDao
import com.spiralsoft.servare.data.sesion.GestorSesion
import com.spiralsoft.servare.domain.model.AvisoConectividad
import com.spiralsoft.servare.domain.model.AvisoDescargaInicial
import com.spiralsoft.servare.domain.model.AvisoOperacion
import com.spiralsoft.servare.domain.model.AvisoResultado
import com.spiralsoft.servare.domain.model.AvisoSesionExpirada
import com.spiralsoft.servare.domain.model.AvisoSinConexion
import com.spiralsoft.servare.domain.model.EstadoOperacion
import com.spiralsoft.servare.domain.model.EstadoSesion
import com.spiralsoft.servare.domain.model.EstadoConectividad
import com.spiralsoft.servare.domain.model.EstadoConectividad.CONECTADO
import com.spiralsoft.servare.domain.model.EstadoConectividad.RED_SIN_SERVIDOR
import com.spiralsoft.servare.domain.model.EstadoConectividad.SIN_RED
import com.spiralsoft.servare.domain.model.TipoOperacion

// Servicio de notificación de estado, traduce estados y errores a mensajes para el usuario.
// Solo observa al monitor, nunca comprueba la red por su cuenta
@Singleton
class ServicioNotificacionEstado @Inject constructor(
    private val connectivityMonitor: ConnectivityMonitor,
    gestorSesion: GestorSesion,
    operationDao: OperationDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val conectividad: StateFlow<AvisoConectividad> = connectivityMonitor.estado
        .map(::traducir)
        .stateIn(scope, SharingStarted.Eagerly, traducir(connectivityMonitor.estado.value))

    // La causa depende del estado de red
    // La app lo reintenta sola en cada caso
    val descargaInicial: StateFlow<AvisoDescargaInicial> = connectivityMonitor.estado
        .map(::traducirDescargaInicial)
        .stateIn(scope, SharingStarted.Eagerly, traducirDescargaInicial(connectivityMonitor.estado.value))

    // null con la sesión en orden. Con la sesión expirada la app sigue funcionando con lo guardado,
    // pero la cola está pausada, el aviso lo dice y pide iniciar sesión
    val sesionExpirada: StateFlow<AvisoSesionExpirada?> = gestorSesion.estado
        .map(::traducirSesion)
        .stateIn(scope, SharingStarted.Eagerly, traducirSesion(gestorSesion.estado.value))

    // Cambios que requieren al usuario: FALLIDA, CONFLICTO y BLOQUEADA no se purgan, así que el aviso
    // dura hasta que se resuelvan. Se lee de la cola: sobrevive a cerrar la app, a diferencia de
    // resultados, que solo llega si hay una pantalla a la vista
    val problemasOperacion: StateFlow<List<AvisoOperacion>> = operationDao.observarConProblema()
        .map(::traducirProblemas)
        .catch { if (it is SQLiteException) emit(emptyList()) else throw it }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Resultado de las operaciones que terminaron: aviso pasajero, sin pantalla a la vista se pierde
    private val _resultados = MutableSharedFlow<AvisoResultado>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val resultados: SharedFlow<AvisoResultado> = _resultados.asSharedFlow()

    // Lo llama el motor al terminar de procesar la cola: cuántas operaciones confirmó el servidor y cuáles
    // pasaron a FALLIDA o CONFLICTO. Es un solo mensaje por ciclo, así uno no tapa al otro; el detalle de
    // cada problema está en problemasOperacion
    fun operacionesFinalizadas(confirmadas: Int, conProblema: List<OperacionConProblema>) {
        if (confirmadas <= 0 && conProblema.isEmpty()) return
        val mensaje = if (conProblema.isEmpty()) {
            if (confirmadas == 1) "Se sincronizó 1 cambio con el servidor"
            else "Se sincronizaron $confirmadas cambios con el servidor"
        } else {
            val problema = if (conProblema.size == 1) traducirOrigen(conProblema.single(), enEspera = 0).titulo
            else "${conProblema.size} cambios no se pudieron sincronizar"
            val exito = when (confirmadas) {
                0 -> ""
                1 -> ". Otro cambio sí se sincronizó"
                else -> ". Otros $confirmadas cambios sí se sincronizaron"
            }
            problema + exito
        }
        _resultados.tryEmit(AvisoResultado(mensaje))
    }

    // Un aviso por cada FALLIDA o CONFLICTO, con sus BLOQUEADAS contadas dentro. Una BLOQUEADA cuyo origen
    // es de otro registro (dependsOn) se avisa sola
    private fun traducirProblemas(operaciones: List<OperacionConProblema>): List<AvisoOperacion> {
        val (bloqueadas, origenes) = operaciones.partition { it.status == EstadoOperacion.BLOQUEADA }
        val enEspera = bloqueadas.groupBy { it.entityType to it.entityId }
        val conOrigen = origenes.map { it.entityType to it.entityId }.toSet()
        return origenes.map { traducirOrigen(it, enEspera[it.entityType to it.entityId].orEmpty().size) } +
            bloqueadas.filter { (it.entityType to it.entityId) !in conOrigen }.map(::traducirBloqueada)
    }

    private fun traducirOrigen(op: OperacionConProblema, enEspera: Int): AvisoOperacion {
        val registro = nombrar(op)
        val espera = when (enEspera) {
            0 -> ""
            1 -> " Hay 1 cambio posterior en espera."
            else -> " Hay $enEspera cambios posteriores en espera."
        }
        return if (op.status == EstadoOperacion.CONFLICTO) AvisoOperacion(
            op.operationId,
            op.status,
            titulo = "$registro tiene cambios por resolver",
            detalle = "${op.lastError ?: "El registro cambió en el servidor"}. " +
                "Tu versión sigue guardada en el dispositivo.$espera"
        ) else AvisoOperacion(
            op.operationId,
            op.status,
            titulo = when (op.type) {
                TipoOperacion.CREAR -> "No se pudo enviar $registro al servidor"
                TipoOperacion.ACTUALIZAR -> "No se pudieron enviar los cambios de $registro"
                TipoOperacion.ELIMINAR -> "No se pudo eliminar $registro del servidor"
                TipoOperacion.ADJUNTAR -> "No se pudo subir un archivo de $registro"
            },
            detalle = "${op.lastError ?: "El servidor no aceptó el cambio"}. " +
                "El cambio sigue guardado en el dispositivo.$espera"
        )
    }

    private fun traducirBloqueada(op: OperacionConProblema) = AvisoOperacion(
        op.operationId,
        op.status,
        titulo = "Cambios en espera en ${nombrar(op)}",
        detalle = "Dependen de otro cambio que no se pudo completar y no se enviarán hasta resolverlo. " +
            "Siguen guardados en el dispositivo."
    )

    private fun nombrar(op: OperacionConProblema) = op.titulo?.let { "«$it»" } ?: "un registro"

    private fun traducirSesion(estado: EstadoSesion): AvisoSesionExpirada? =
        (estado as? EstadoSesion.Expirada)?.let {
            AvisoSesionExpirada(
                titulo = "Tu sesión expiró",
                detalle = "Puedes seguir trabajando: tus cambios se guardan en el dispositivo, " +
                    "pero no se enviarán hasta que inicies sesión de nuevo con ${it.correo}."
            )
        }

    // Registrarse e iniciar sesión requieren servidor
    // null cuando se puede, se avisa con la causa cuando no es posible
    val registroNoDisponible: StateFlow<AvisoSinConexion?> =
        requiereConexion(accion = "Crear la cuenta", verbo = "registrarte")

    val inicioSesionNoDisponible: StateFlow<AvisoSinConexion?> =
        requiereConexion(accion = "Iniciar sesión", verbo = "iniciar sesión")

    private fun requiereConexion(accion: String, verbo: String): StateFlow<AvisoSinConexion?> =
        connectivityMonitor.estado
            .map { traducirRequiereConexion(it, accion, verbo) }
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                traducirRequiereConexion(connectivityMonitor.estado.value, accion, verbo)
            )

    private fun traducirRequiereConexion(estado: EstadoConectividad, accion: String, verbo: String) =
        when (estado) {
            SIN_RED -> AvisoSinConexion(
                titulo = "$accion requiere conexión",
                detalle = "El dispositivo no tiene red. Conéctate a internet para $verbo."
            )
            RED_SIN_SERVIDOR -> AvisoSinConexion(
                titulo = "$accion requiere conexión",
                detalle = "Hay red, pero el servidor no responde. Podrás $verbo en cuanto responda."
            )
            CONECTADO -> null
        }

    private fun traducirDescargaInicial(estado: EstadoConectividad) = AvisoDescargaInicial(
        titulo = "Aún no se descargaron los registros",
        detalle = when (estado) {
            SIN_RED -> "El dispositivo no tiene red. Se descargarán al recuperar la conexión."
            RED_SIN_SERVIDOR -> "Hay red, pero el servidor no responde. Se descargarán cuando responda."
            CONECTADO -> "Se están solicitando al servidor. Si no aparecen, se volverá a intentar automáticamente."
        }
    )

    private fun traducir(estado: EstadoConectividad) = when (estado) {
        SIN_RED -> AvisoConectividad(
            estado,
            titulo = "Sin conexión",
            detalle = "El dispositivo no tiene red. Puedes seguir trabajando: " +
                "tus cambios se guardan en el dispositivo y se enviarán al recuperar la conexión."
        )
        RED_SIN_SERVIDOR -> AvisoConectividad(
            estado,
            titulo = "Servidor no disponible",
            detalle = "Hay red, pero el servidor no responde. Tus cambios se guardan en el dispositivo." +
                    " Se volverá a intentar automáticamente."
        )
        CONECTADO -> AvisoConectividad(
            estado,
            titulo = "Conectado",
            detalle = "El servidor responde. Tus cambios se sincronizan."
        )
    }
}
