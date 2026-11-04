package com.spiralsoft.servare.testutil

import com.spiralsoft.servare.data.local.entity.OperationEntity
import com.spiralsoft.servare.data.remote.ResultadoEnvio
import com.spiralsoft.servare.data.remote.ResultadoPull
import com.spiralsoft.servare.data.sync.AdaptadorEntidad
import com.spiralsoft.servare.data.sync.Descarga
import com.spiralsoft.servare.data.sync.RegistroServidor
import com.spiralsoft.servare.domain.model.TipoEntidad

// Adaptador que no hace nada: sirve para armar Adaptadores sin Room ni red
class AdaptadorFalso(override val tipo: TipoEntidad) : AdaptadorEntidad {
    override suspend fun enviar(op: OperationEntity): ResultadoEnvio = ResultadoEnvio.Confirmada(null)
    override suspend fun descargar() = Descarga(ResultadoPull.Datos(emptyList<Nothing>()))
    override suspend fun titulo(entityId: String): String? = null
    override suspend fun existe(entityId: String) = false
    override suspend fun borrar(entityId: String) = Unit
    override suspend fun actualizarVersion(entityId: String, version: Int) = Unit
    override suspend fun limpiarVersion(entityId: String) = Unit
    override fun leerServidor(entityId: String, snapshot: String): RegistroServidor? = null
}
