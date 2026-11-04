package com.spiralsoft.servare.testutil

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import com.spiralsoft.servare.domain.model.AccionOperacion
import com.spiralsoft.servare.domain.model.Prueba
import com.spiralsoft.servare.domain.repository.PruebaRepository
import com.spiralsoft.servare.domain.repository.ResultadoEliminacionPrueba
import com.spiralsoft.servare.domain.repository.ResultadoGuardadoPrueba
import com.spiralsoft.servare.domain.repository.ResultadoObtencionPrueba
import com.spiralsoft.servare.domain.repository.ResultadoRefresco
import com.spiralsoft.servare.domain.repository.ResultadoResolucion

// Cada operación es una lambda que la prueba reemplaza, y deja constancia de lo que recibió
class PruebaRepositoryFalso : PruebaRepository {
    class Guardado(val entityId: String?, val titulo: String, val texto: String, val numero: Int)

    val guardados = mutableListOf<Guardado>()

    var alObtener: suspend (String) -> ResultadoObtencionPrueba = { ResultadoObtencionPrueba.NoEncontrada }
    var alGuardar: suspend () -> ResultadoGuardadoPrueba = { ResultadoGuardadoPrueba.Guardada("nueva") }
    var alActualizar: suspend () -> ResultadoGuardadoPrueba = { ResultadoGuardadoPrueba.Guardada("existente") }

    override fun observarTodas(): Flow<List<Prueba>> = flowOf(emptyList())

    override suspend fun obtener(entityId: String) = alObtener(entityId)

    override suspend fun guardar(titulo: String, texto: String, numero: Int): ResultadoGuardadoPrueba {
        guardados += Guardado(null, titulo, texto, numero)
        return alGuardar()
    }

    override suspend fun actualizar(entityId: String, titulo: String, texto: String, numero: Int): ResultadoGuardadoPrueba {
        guardados += Guardado(entityId, titulo, texto, numero)
        return alActualizar()
    }

    override suspend fun eliminar(entityId: String): ResultadoEliminacionPrueba =
        ResultadoEliminacionPrueba.Eliminada(entityId)

    override fun observarDescargaInicial(): Flow<Boolean> = flowOf(true)

    override suspend fun resolver(operationId: String, accion: AccionOperacion): ResultadoResolucion =
        ResultadoResolucion.Resuelta(accion)

    override suspend fun refrescar(): ResultadoRefresco = ResultadoRefresco.Actualizada()
}
