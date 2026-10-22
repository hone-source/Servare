package com.spiralsoft.servare.notification

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton
import com.spiralsoft.servare.data.connectivity.ConnectivityState
import com.spiralsoft.servare.data.local.operation.OperationType
import com.spiralsoft.servare.data.sync.ResultadoSync
import com.spiralsoft.servare.domain.model.CambioPorResolver
import com.spiralsoft.servare.domain.model.MotivoProblema

/** Mensaje para el usuario: */
data class MensajeEstado(
    val titulo: String,
    val detalle: String
)

/**
 * Servicio de notificación de estado: traduce estados y errores a mensajes.
 * El ViewModel obtiene aquí el mensaje según el tipo de estado o fallo; nunca un error genérico.
 */
interface StatusNotifier {
    /** null = no hay nada que avisar (CONECTADO). */
    fun mensajeConectividad(estado: ConnectivityState): MensajeEstado?

    val resultados: Flow<MensajeEstado>

    fun notificar(resultado: ResultadoSync)

    fun mensajeCambio(cambio: CambioPorResolver): MensajeEstado

    fun confirmacionDescarte(cambio: CambioPorResolver): MensajeEstado
}

@Singleton
class DefaultStatusNotifier @Inject constructor() : StatusNotifier {

    // Sin pantalla que escuche, el mensaje se pierde; el estado queda en la cola, la lista marca
    // la ficha y el cambio aparece en "Cambios por resolver"
    private val _resultados = MutableSharedFlow<MensajeEstado>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val resultados: Flow<MensajeEstado> = _resultados.asSharedFlow()

    override fun notificar(resultado: ResultadoSync) {
        _resultados.tryEmit(mensajeResultado(resultado))
    }

    override fun mensajeConectividad(estado: ConnectivityState): MensajeEstado? = when (estado) {
        ConnectivityState.CONECTADO -> null
        ConnectivityState.SIN_RED -> MensajeEstado(
            titulo = "Sin conexión",
            detalle = "Trabajas con los datos guardados en el dispositivo. " +
                "Tus cambios se conservan y se enviarán cuando vuelva la conexión."
        )
        ConnectivityState.RED_SIN_SERVIDOR -> MensajeEstado(
            titulo = "Servidor no disponible",
            detalle = "Hay red, pero el servidor no responde. Trabajas con los datos del dispositivo " +
                "y se volverá a intentar automáticamente."
        )
    }

    private fun mensajeResultado(resultado: ResultadoSync): MensajeEstado = when (resultado) {
        is ResultadoSync.Sincronizadas -> MensajeEstado(
            titulo = "Cambios sincronizados",
            detalle = if (resultado.cantidad == 1) "Se envió 1 cambio al servidor."
            else "Se enviaron ${resultado.cantidad} cambios al servidor."
        )
        is ResultadoSync.Conflicto -> MensajeEstado(
            titulo = "Cambios pendientes de resolver",
            detalle = "${cambio(resultado.tipo, resultado.nombre)} no se sincronizó: la ficha fue " +
                "modificada en otro dispositivo. Tu versión se conservó en el dispositivo. $REVISAR"
        )
        is ResultadoSync.Fallida -> MensajeEstado(
            titulo = "No se pudo sincronizar",
            detalle = "${cambio(resultado.tipo, resultado.nombre)} " + when {
                resultado.porIntentos ->
                    "no se pudo enviar: el servidor siguió fallando tras varios intentos."
                resultado.motivo != null -> "no se aceptó en el servidor: ${resultado.motivo.trimEnd('.')}."
                else -> "no se aceptó en el servidor."
            } + " Se conserva en el dispositivo. $REVISAR"
        )
        ResultadoSync.SesionRequerida -> MensajeEstado(
            titulo = "Inicia sesión para sincronizar",
            detalle = "El servidor no aceptó la sesión. Tus cambios siguen guardados en el dispositivo " +
                "y se enviarán al iniciar sesión."
        )
        ResultadoSync.DescargaFallida -> MensajeEstado(
            titulo = "No se descargaron cambios",
            detalle = "El servidor no entregó las fichas. Se volverá a intentar en la próxima sincronización."
        )
    }

    override fun mensajeCambio(cambio: CambioPorResolver): MensajeEstado {
        val queCambio = cambio(cambio.tipo, cambio.nombreFicha)
        val mensaje = when (cambio.motivo) {
            MotivoProblema.CONFLICTO -> MensajeEstado(
                titulo = "Conflicto con el servidor",
                detalle = "$queCambio no se sincronizó porque la ficha cambió en otro dispositivo. " +
                    "Puedes conservar tu versión (reemplaza la del servidor) o descartarla."
            )
            MotivoProblema.RECHAZADO -> MensajeEstado(
                titulo = "El servidor no aceptó el cambio",
                detalle = "$queCambio no se aceptó" +
                    (cambio.detalle?.let { ": ${it.trimEnd('.')}." } ?: ".") +
                    if (cambio.tipo == OperationType.CREATE || cambio.tipo == OperationType.UPDATE) {
                        " Corrige la ficha y guárdala: se enviará la versión corregida en lugar de esta."
                    } else {
                        " Si reintentas se enviarán los mismos datos."
                    }
            )
            MotivoProblema.INTENTOS_AGOTADOS -> MensajeEstado(
                titulo = "No se pudo enviar",
                detalle = "$queCambio no se pudo enviar: el servidor siguió fallando tras varios intentos. " +
                    "Puedes volver a intentarlo."
            )
        }
        return if (cambio.dependientes == 0) mensaje
        else mensaje.copy(detalle = mensaje.detalle + " " + cambiosEnEspera(cambio.dependientes))
    }

    override fun confirmacionDescarte(cambio: CambioPorResolver): MensajeEstado {
        val ficha = ficha(cambio.nombreFicha)
        val consecuencia = when (cambio.tipo) {
            OperationType.CREATE ->
                "El servidor no ha confirmado el registro de $ficha: se quitará de este dispositivo " +
                    "y no se podrá recuperar."
            OperationType.UPDATE ->
                "Se perderá tu modificación de $ficha y quedará la versión del servidor " +
                    "en la próxima sincronización."
            OperationType.DELETE ->
                "No se eliminará $ficha del servidor y volverá a la lista del dispositivo."
            OperationType.ATTACH ->
                "El adjunto de $ficha no se enviará al servidor."
        }
        val dependientes = when (cambio.dependientes) {
            0 -> ""
            1 -> " También se descartará 1 cambio posterior que depende de este."
            else -> " También se descartarán ${cambio.dependientes} cambios posteriores que dependen de este."
        }
        return MensajeEstado(titulo = "¿Descartar el cambio?", detalle = consecuencia + dependientes)
    }

    private fun cambiosEnEspera(cantidad: Int) =
        if (cantidad == 1) "1 cambio posterior espera a que lo resuelvas."
        else "$cantidad cambios posteriores esperan a que lo resuelvas."

    private fun ficha(nombre: String?) = nombre?.let { "la ficha “$it”" } ?: "una ficha"

    private fun cambio(tipo: OperationType, nombre: String?): String {
        val ficha = ficha(nombre)
        return when (tipo) {
            OperationType.CREATE -> "El registro de $ficha"
            OperationType.UPDATE -> "La modificación de $ficha"
            OperationType.DELETE -> "La eliminación de $ficha"
            OperationType.ATTACH -> "Un adjunto de $ficha"
        }
    }

    private companion object {
        const val REVISAR = "Revísalo en “Cambios por resolver”."
    }
}
