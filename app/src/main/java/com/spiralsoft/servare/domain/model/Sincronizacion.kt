package com.spiralsoft.servare.domain.model

import java.time.Instant
import com.spiralsoft.servare.data.local.operation.OperationType

/** Estado de sincronización de una ficha en la lista. */
enum class EstadoSincronizacion {
    SINCRONIZADA,
    /** Tiene cambios en la cola que se enviarán solos. */
    PENDIENTE,
    /** Tiene un cambio FALLIDO, en CONFLICTO o BLOQUEADO: espera una decisión del usuario. */
    REQUIERE_ATENCION
}

/** Por qué un cambio no se sincronizó. */
enum class MotivoProblema {
    /** La ficha cambió en el servidor (otra versión). */
    CONFLICTO,
    /** El servidor no aceptó los datos (validación, regla de negocio, tamaño). */
    RECHAZADO,
    /** El servidor siguió fallando hasta agotar los intentos automáticos. */
    INTENTOS_AGOTADOS
}

/** Cambio que no se pudo sincronizar y espera que el usuario lo reintente o lo descarte. */
data class CambioPorResolver(
    val operationId: String,
    val entityId: String,
    val nombreFicha: String?,
    val tipo: OperationType,
    val motivo: MotivoProblema,
    val detalle: String?,       // motivo que dio el servidor, solo en RECHAZADO
    val creadoEl: Instant,
    val dependientes: Int       // cambios posteriores que esperan a este (BLOQUEADOS)
)
