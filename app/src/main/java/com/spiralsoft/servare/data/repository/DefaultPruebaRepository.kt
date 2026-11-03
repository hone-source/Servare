package com.spiralsoft.servare.data.repository

import android.database.sqlite.SQLiteException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import com.spiralsoft.servare.data.local.dao.PruebaDao
import com.spiralsoft.servare.data.local.dao.SyncInicialDao
import com.spiralsoft.servare.data.local.entity.PruebaEntity
import com.spiralsoft.servare.data.repository.EscrituraOutbox.Existente
import com.spiralsoft.servare.data.sync.SyncEngine
import com.spiralsoft.servare.domain.model.AccionOperacion
import com.spiralsoft.servare.domain.model.Prueba
import com.spiralsoft.servare.domain.model.TipoEntidad
import com.spiralsoft.servare.domain.repository.PruebaRepository
import com.spiralsoft.servare.domain.repository.ResultadoEliminacionPrueba
import com.spiralsoft.servare.domain.repository.ResultadoGuardadoPrueba
import com.spiralsoft.servare.domain.repository.ResultadoObtencionPrueba
import com.spiralsoft.servare.domain.repository.ResultadoRefresco
import com.spiralsoft.servare.domain.repository.ResultadoResolucion

class DefaultPruebaRepository @Inject constructor(
    private val pruebaDao: PruebaDao,
    private val escritura: EscrituraOutbox,
    private val syncInicialDao: SyncInicialDao,
    private val syncEngine: SyncEngine,
    private val resolutor: ResolutorOperaciones
) : PruebaRepository {

    override fun observarTodas(): Flow<List<Prueba>>
        = pruebaDao.observarTodas().map { lista ->
            lista.map { it.aDominio() }
    }

    override fun observarDescargaInicial(): Flow<Boolean>
        = syncInicialDao.observarCompletada(TipoEntidad.PRUEBA)

    override suspend fun obtener(entityId: String): ResultadoObtencionPrueba = try {
        pruebaDao.findActivaById(entityId)?.let {
            ResultadoObtencionPrueba.Encontrada(it.aDominio()) }
            ?: ResultadoObtencionPrueba.NoEncontrada
    } catch (_: SQLiteException) {
        ResultadoObtencionPrueba.ErrorAlmacenamiento
    }

    override suspend fun guardar(titulo: String, texto: String, numero: Int): ResultadoGuardadoPrueba {
        if (titulo.isBlank()) return ResultadoGuardadoPrueba.FaltaTitulo

        return escritura.crear(TipoEntidad.PRUEBA) { id, ahora ->
            pruebaDao.insert(
                PruebaEntity(
                    entityId = id,
                    titulo = titulo.trim(),
                    texto = texto,
                    numero = numero,
                    createdAt = ahora
                )
            )
        }.aGuardado()
    }

    override suspend fun actualizar(
        entityId: String,
        titulo: String,
        texto: String,
        numero: Int
    ): ResultadoGuardadoPrueba {
        if (titulo.isBlank()) return ResultadoGuardadoPrueba.FaltaTitulo

        return escritura.actualizar(
            TipoEntidad.PRUEBA, entityId,
            buscar = { pruebaDao.findActivaById(entityId)?.let { Existente(it, it.version) } },
            guardar = { pruebaDao.update(it.copy(titulo = titulo.trim(), texto = texto, numero = numero)) }
        ).aGuardado()
    }

    override suspend fun eliminar(entityId: String): ResultadoEliminacionPrueba =
        when (val resultado = escritura.eliminar(TipoEntidad.PRUEBA, entityId) {
            pruebaDao.findActivaById(entityId)?.let { Existente(it, it.version) }
        }) {
            is ResultadoEscritura.Ok -> ResultadoEliminacionPrueba.Eliminada(resultado.valor.titulo)
            ResultadoEscritura.NoEncontrada -> ResultadoEliminacionPrueba.NoEncontrada
            // Borrar no necesita espacio libre: el mensaje es el mismo
            ResultadoEscritura.SinEspacio, ResultadoEscritura.ErrorAlmacenamiento ->
                ResultadoEliminacionPrueba.ErrorAlmacenamiento
        }

    override suspend fun refrescar(): ResultadoRefresco = syncEngine.refrescar()

    override suspend fun resolver(operationId: String, accion: AccionOperacion): ResultadoResolucion =
        resolutor.resolver(operationId, accion)
}

private fun PruebaEntity.aDominio() = Prueba(entityId, titulo, texto, numero, createdAt)

private fun ResultadoEscritura<String>.aGuardado(): ResultadoGuardadoPrueba = when (this) {
    is ResultadoEscritura.Ok -> ResultadoGuardadoPrueba.Guardada(valor)
    ResultadoEscritura.NoEncontrada -> ResultadoGuardadoPrueba.NoEncontrada
    ResultadoEscritura.SinEspacio -> ResultadoGuardadoPrueba.SinEspacio
    ResultadoEscritura.ErrorAlmacenamiento -> ResultadoGuardadoPrueba.ErrorAlmacenamiento
}
