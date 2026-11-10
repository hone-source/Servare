package com.spiralsoft.servare.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import com.spiralsoft.servare.data.local.dao.PuntoDao
import com.spiralsoft.servare.data.local.dao.SyncInicialDao
import com.spiralsoft.servare.data.local.entity.PuntoEntity
import com.spiralsoft.servare.data.repository.EscrituraOutbox.Existente
import com.spiralsoft.servare.data.sync.SyncEngine
import com.spiralsoft.servare.domain.model.AccionOperacion
import com.spiralsoft.servare.domain.model.Punto
import com.spiralsoft.servare.domain.model.TipoEntidad
import com.spiralsoft.servare.domain.repository.PuntoRepository
import com.spiralsoft.servare.domain.repository.ResultadoEliminacionPunto
import com.spiralsoft.servare.domain.repository.ResultadoGuardadoPunto
import com.spiralsoft.servare.domain.repository.ResultadoRefresco
import com.spiralsoft.servare.domain.repository.ResultadoResolucion

class DefaultPuntoRepository @Inject constructor(
    private val puntoDao: PuntoDao,
    private val escritura: EscrituraOutbox,
    private val syncInicialDao: SyncInicialDao,
    private val syncEngine: SyncEngine,
    private val resolutor: ResolutorOperaciones
) : PuntoRepository {

    override fun observarTodos(): Flow<List<Punto>> =
        puntoDao.observarTodos().map { lista -> lista.map { it.aDominio() } }

    override fun observarDescargaInicial(): Flow<Boolean> =
        syncInicialDao.observarCompletada(TipoEntidad.PUNTO)

    override suspend fun guardar(nombre: String, latitud: Double, longitud: Double): ResultadoGuardadoPunto {
        if (nombre.isBlank()) return ResultadoGuardadoPunto.FaltaNombre
        if (!coordenadasValidas(latitud, longitud)) return ResultadoGuardadoPunto.CoordenadasInvalidas

        return escritura.crear(TipoEntidad.PUNTO) { id, ahora ->
            puntoDao.insert(
                PuntoEntity(
                    entityId = id,
                    nombre = nombre.trim(),
                    latitud = latitud,
                    longitud = longitud,
                    createdAt = ahora
                )
            )
        }.aGuardado(nombre.trim())
    }

    override suspend fun actualizar(
        entityId: String,
        nombre: String,
        latitud: Double,
        longitud: Double
    ): ResultadoGuardadoPunto {
        if (nombre.isBlank()) return ResultadoGuardadoPunto.FaltaNombre
        if (!coordenadasValidas(latitud, longitud)) return ResultadoGuardadoPunto.CoordenadasInvalidas

        return escritura.actualizar(
            TipoEntidad.PUNTO, entityId,
            buscar = { puntoDao.findActivaById(entityId)?.let { Existente(it, it.version) } },
            guardar = { puntoDao.update(it.copy(nombre = nombre.trim(), latitud = latitud, longitud = longitud)) }
        ).aGuardado(nombre.trim())
    }

    override suspend fun eliminar(entityId: String): ResultadoEliminacionPunto =
        when (val resultado = escritura.eliminar(TipoEntidad.PUNTO, entityId) {
            puntoDao.findActivaById(entityId)?.let { Existente(it, it.version) }
        }) {
            is ResultadoEscritura.Ok -> ResultadoEliminacionPunto.Eliminado(resultado.valor.nombre)
            ResultadoEscritura.NoEncontrada -> ResultadoEliminacionPunto.NoEncontrado
            // Borrar no necesita espacio libre, el mensaje es el mismo
            ResultadoEscritura.SinEspacio, ResultadoEscritura.ErrorAlmacenamiento ->
                ResultadoEliminacionPunto.ErrorAlmacenamiento
        }

    override suspend fun refrescar(): ResultadoRefresco = syncEngine.refrescar()

    override suspend fun resolver(operationId: String, accion: AccionOperacion): ResultadoResolucion =
        resolutor.resolver(operationId, accion)
}

private fun coordenadasValidas(latitud: Double, longitud: Double) =
    latitud.isFinite() && longitud.isFinite() && latitud in -90.0..90.0 && longitud in -180.0..180.0

private fun PuntoEntity.aDominio() = Punto(entityId, nombre, latitud, longitud, createdAt)

private fun ResultadoEscritura<String>.aGuardado(nombre: String): ResultadoGuardadoPunto = when (this) {
    is ResultadoEscritura.Ok -> ResultadoGuardadoPunto.Guardado(nombre)
    ResultadoEscritura.NoEncontrada -> ResultadoGuardadoPunto.NoEncontrado
    ResultadoEscritura.SinEspacio -> ResultadoGuardadoPunto.SinEspacio
    ResultadoEscritura.ErrorAlmacenamiento -> ResultadoGuardadoPunto.ErrorAlmacenamiento
}
