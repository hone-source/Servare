package com.spiralsoft.servare.data.repository

import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteFullException
import androidx.room.withTransaction
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import com.spiralsoft.servare.data.local.AppDatabase
import com.spiralsoft.servare.data.local.dao.EliminacionDao
import com.spiralsoft.servare.data.local.entity.EliminacionEntity
import com.spiralsoft.servare.data.sync.SyncScheduler
import com.spiralsoft.servare.domain.model.TipoEntidad
import com.spiralsoft.servare.domain.model.TipoOperacion

sealed interface ResultadoEscritura<out T> {
    data class Ok<T>(val valor: T) : ResultadoEscritura<T>
    data object NoEncontrada : ResultadoEscritura<Nothing>
    data object SinEspacio : ResultadoEscritura<Nothing>
    data object ErrorAlmacenamiento : ResultadoEscritura<Nothing>
}

// Escritura local del usuario en un registro sincronizable.
// Es el mismo recorrido para todos los tipos: el cambio del registro y su operación
// PENDIENTE en UNA transacción, y después (fuera de ella) se pide
// un ciclo de sincronización. Cada repositorio solo aporta lo propio de su tabla
class EscrituraOutbox @Inject constructor(
    private val database: AppDatabase,
    private val encolador: EncoladorOperaciones,
    private val eliminacionDao: EliminacionDao,
    private val syncScheduler: SyncScheduler,
    private val clock: Clock
) {
    // El registro activo que se va a modificar, con la versión del servidor que se conocía
    class Existente<out E>(val registro: E, val version: Int?)

    // Devuelve el entityId generado. insertar recibe el id y el createdAt que lleva también la operación
    suspend fun crear(
        entidad: TipoEntidad,
        insertar: suspend (entityId: String, createdAt: Long) -> Unit
    ): ResultadoEscritura<String> = escribir {
        val id = UUID.randomUUID().toString()
        val ahora = clock.millis()
        database.withTransaction {
            insertar(id, ahora)
            encolador.encolar(TipoOperacion.CREAR, entidad, id, createdAt = ahora)
        }
        id
    }

    // buscar devuelve el registro activo (null si ya no está) guardar escribe el cambio sobre él.
    // Si ya hay una operación sin enviar, el cambio viaja con ella y no se crea otra
    suspend fun <E> actualizar(
        entidad: TipoEntidad,
        entityId: String,
        buscar: suspend () -> Existente<E>?,
        guardar: suspend (E) -> Unit
    ): ResultadoEscritura<String> = escribir {
        database.withTransaction {
            val existente = buscar() ?: return@withTransaction null
            guardar(existente.registro)
            encolador.encolarActualizacion(entidad, entityId, existente.version)
            entityId
        }
    }

    // El registro sigue en su tabla con una marca de borrado: si el borrado falla en el servidor no se pierde.
    // Al completarse el ELIMINAR, el motor borra el registro y su marca en una transacción.
    // Devuelve el registro, para que el repositorio nombre lo que se eliminó
    suspend fun <E : Any> eliminar(
        entidad: TipoEntidad,
        entityId: String,
        buscar: suspend () -> Existente<E>?
    ): ResultadoEscritura<E> = escribir {
        database.withTransaction {
            val existente = buscar() ?: return@withTransaction null
            val ahora = clock.millis()
            eliminacionDao.insert(EliminacionEntity(entidad, entityId, ahora))
            encolador.encolar(
                TipoOperacion.ELIMINAR, entidad, entityId, createdAt = ahora, baseVersion = existente.version
            )
            existente.registro
        }
    }

    // null es "el registro ya no existe". El ciclo se pide solo si se escribió, y siempre fuera de la transacción
    private suspend fun <T : Any> escribir(bloque: suspend () -> T?): ResultadoEscritura<T> = try {
        val valor = bloque()
        if (valor == null) ResultadoEscritura.NoEncontrada
        else {
            syncScheduler.requestSync()
            ResultadoEscritura.Ok(valor)
        }
    } catch (_: SQLiteFullException) {
        ResultadoEscritura.SinEspacio
    } catch (_: SQLiteException) {
        ResultadoEscritura.ErrorAlmacenamiento
    }
}
