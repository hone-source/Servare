package com.spiralsoft.servare.data.repository

import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteFullException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import com.spiralsoft.servare.data.local.dao.PuntoDao
import com.spiralsoft.servare.data.local.entity.PuntoEntity
import com.spiralsoft.servare.domain.model.Punto
import com.spiralsoft.servare.domain.repository.PuntoRepository
import com.spiralsoft.servare.domain.repository.ResultadoEliminacionPunto
import com.spiralsoft.servare.domain.repository.ResultadoGuardadoPunto

// Los puntos solo viven en Room: no pasan por el outbox ni piden sincronización
class DefaultPuntoRepository @Inject constructor(
    private val puntoDao: PuntoDao,
    private val clock: Clock
) : PuntoRepository {

    override fun observarTodos(): Flow<List<Punto>> =
        puntoDao.observarTodos().map { lista -> lista.map { it.aDominio() } }

    override suspend fun guardar(nombre: String, latitud: Double, longitud: Double): ResultadoGuardadoPunto {
        if (nombre.isBlank()) return ResultadoGuardadoPunto.FaltaNombre
        if (!coordenadasValidas(latitud, longitud)) return ResultadoGuardadoPunto.CoordenadasInvalidas

        return try {
            puntoDao.insert(
                PuntoEntity(
                    entityId = UUID.randomUUID().toString(),
                    nombre = nombre.trim(),
                    latitud = latitud,
                    longitud = longitud,
                    createdAt = clock.millis()
                )
            )
            ResultadoGuardadoPunto.Guardado(nombre.trim())
        } catch (_: SQLiteFullException) {
            ResultadoGuardadoPunto.SinEspacio
        } catch (_: SQLiteException) {
            ResultadoGuardadoPunto.ErrorAlmacenamiento
        }
    }

    override suspend fun actualizar(
        entityId: String,
        nombre: String,
        latitud: Double,
        longitud: Double
    ): ResultadoGuardadoPunto {
        if (nombre.isBlank()) return ResultadoGuardadoPunto.FaltaNombre
        if (!coordenadasValidas(latitud, longitud)) return ResultadoGuardadoPunto.CoordenadasInvalidas

        return try {
            val punto = puntoDao.findById(entityId) ?: return ResultadoGuardadoPunto.NoEncontrado
            puntoDao.update(punto.copy(nombre = nombre.trim(), latitud = latitud, longitud = longitud))
            ResultadoGuardadoPunto.Guardado(nombre.trim())
        } catch (_: SQLiteFullException) {
            ResultadoGuardadoPunto.SinEspacio
        } catch (_: SQLiteException) {
            ResultadoGuardadoPunto.ErrorAlmacenamiento
        }
    }

    override suspend fun eliminar(entityId: String): ResultadoEliminacionPunto = try {
        val punto = puntoDao.findById(entityId)
        if (punto == null) {
            ResultadoEliminacionPunto.NoEncontrado
        } else {
            puntoDao.deleteById(entityId)
            ResultadoEliminacionPunto.Eliminado(punto.nombre)
        }
    } catch (_: SQLiteException) {
        // Borrar no necesita espacio libre, el mensaje es el mismo
        ResultadoEliminacionPunto.ErrorAlmacenamiento
    }
}

private fun coordenadasValidas(latitud: Double, longitud: Double) =
    latitud.isFinite() && longitud.isFinite() && latitud in -90.0..90.0 && longitud in -180.0..180.0

private fun PuntoEntity.aDominio() = Punto(entityId, nombre, latitud, longitud, createdAt)
