package com.spiralsoft.servare.data.repository

import android.database.sqlite.SQLiteException
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import com.spiralsoft.servare.data.local.dao.FichaDao
import com.spiralsoft.servare.data.local.dao.SyncInicialDao
import com.spiralsoft.servare.data.local.dao.aCompleta
import com.spiralsoft.servare.data.local.dao.aFicha
import com.spiralsoft.servare.data.repository.EscrituraOutbox.Existente
import com.spiralsoft.servare.data.sync.SyncEngine
import com.spiralsoft.servare.domain.model.AccionOperacion
import com.spiralsoft.servare.domain.model.Ficha
import com.spiralsoft.servare.domain.model.FichaResumen
import com.spiralsoft.servare.domain.model.TipoEntidad
import com.spiralsoft.servare.domain.repository.FichaRepository
import com.spiralsoft.servare.domain.repository.ResultadoEliminacionFicha
import com.spiralsoft.servare.domain.repository.ResultadoGuardadoFicha
import com.spiralsoft.servare.domain.repository.ResultadoObtencionFicha
import com.spiralsoft.servare.domain.repository.ResultadoRefresco
import com.spiralsoft.servare.domain.repository.ResultadoResolucion

class DefaultFichaRepository @Inject constructor(
    private val fichaDao: FichaDao,
    private val escritura: EscrituraOutbox,
    private val syncInicialDao: SyncInicialDao,
    private val syncEngine: SyncEngine,
    private val resolutor: ResolutorOperaciones
) : FichaRepository {

    override fun observarResumenes(): Flow<List<FichaResumen>> = fichaDao.observarResumenes()

    override fun observarDescargaInicial(): Flow<Boolean> =
        syncInicialDao.observarCompletada(TipoEntidad.FICHA)

    override suspend fun obtener(entityId: String): ResultadoObtencionFicha = try {
        fichaDao.findCompletaActivaById(entityId)?.let { ResultadoObtencionFicha.Encontrada(it.aFicha()) }
            ?: ResultadoObtencionFicha.NoEncontrada
    } catch (_: SQLiteException) {
        ResultadoObtencionFicha.ErrorAlmacenamiento
    }

    override suspend fun guardar(ficha: Ficha): ResultadoGuardadoFicha {
        if (ficha.nombre.isBlank()) return ResultadoGuardadoFicha.FaltaNombre

        return escritura.crear(TipoEntidad.FICHA) { id, ahora ->
            fichaDao.insert(ficha.limpia().aCompleta(id, ahora, version = null))
        }.aGuardado()
    }

    override suspend fun actualizar(entityId: String, ficha: Ficha): ResultadoGuardadoFicha {
        if (ficha.nombre.isBlank()) return ResultadoGuardadoFicha.FaltaNombre

        return escritura.actualizar(
            TipoEntidad.FICHA, entityId,
            buscar = { fichaDao.findActivaById(entityId)?.let { Existente(it, it.version) } },
            guardar = { fichaDao.update(ficha.limpia().aCompleta(entityId, it.createdAt, it.version)) }
        ).aGuardado()
    }

    override suspend fun eliminar(entityId: String): ResultadoEliminacionFicha =
        when (val resultado = escritura.eliminar(TipoEntidad.FICHA, entityId) {
            fichaDao.findActivaById(entityId)?.let { Existente(it, it.version) }
        }) {
            is ResultadoEscritura.Ok -> ResultadoEliminacionFicha.Eliminada(resultado.valor.nombre)
            ResultadoEscritura.NoEncontrada -> ResultadoEliminacionFicha.NoEncontrada
            // Borrar no necesita espacio libre: el mensaje es el mismo
            ResultadoEscritura.SinEspacio, ResultadoEscritura.ErrorAlmacenamiento ->
                ResultadoEliminacionFicha.ErrorAlmacenamiento
        }

    override suspend fun refrescar(): ResultadoRefresco = syncEngine.refrescar()

    override suspend fun resolver(operationId: String, accion: AccionOperacion): ResultadoResolucion =
        resolutor.resolver(operationId, accion)

    // El nombre se guarda sin espacios sobrantes, como el título de Prueba
    private fun Ficha.limpia() = copy(nombre = nombre.trim())
}

private fun ResultadoEscritura<String>.aGuardado(): ResultadoGuardadoFicha = when (this) {
    is ResultadoEscritura.Ok -> ResultadoGuardadoFicha.Guardada(valor)
    ResultadoEscritura.NoEncontrada -> ResultadoGuardadoFicha.NoEncontrada
    ResultadoEscritura.SinEspacio -> ResultadoGuardadoFicha.SinEspacio
    ResultadoEscritura.ErrorAlmacenamiento -> ResultadoGuardadoFicha.ErrorAlmacenamiento
}
