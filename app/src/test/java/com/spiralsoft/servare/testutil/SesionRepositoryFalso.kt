package com.spiralsoft.servare.testutil

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.spiralsoft.servare.domain.model.EstadoSesion
import com.spiralsoft.servare.domain.repository.ResultadoCierreSesion
import com.spiralsoft.servare.domain.repository.ResultadoInicioSesion
import com.spiralsoft.servare.domain.repository.ResultadoRegistro
import com.spiralsoft.servare.domain.repository.SesionRepository

class SesionRepositoryFalso(inicial: EstadoSesion = EstadoSesion.SinSesion) : SesionRepository {
    override val estado = MutableStateFlow(inicial)

    var cambiosSinEnviar: Int? = 0
    var cuentasDeCambios = 0
    var alCerrar: suspend () -> ResultadoCierreSesion = { ResultadoCierreSesion.Cerrada }
    var cierres = 0

    override suspend fun registrar(correo: String, contrasena: String, claveRegistro: String): ResultadoRegistro =
        ResultadoRegistro.Registrado

    override suspend fun iniciarSesion(correo: String, contrasena: String): ResultadoInicioSesion =
        ResultadoInicioSesion.Autenticado

    override suspend fun contarCambiosSinEnviar(): Int? {
        cuentasDeCambios++
        return cambiosSinEnviar
    }

    override suspend fun cerrarSesion(): ResultadoCierreSesion {
        cierres++
        return alCerrar()
    }
}
