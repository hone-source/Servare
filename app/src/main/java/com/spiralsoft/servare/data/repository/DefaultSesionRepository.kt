package com.spiralsoft.servare.data.repository

import android.database.sqlite.SQLiteException
import android.util.Patterns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import com.spiralsoft.servare.data.connectivity.ConnectivityMonitor
import com.spiralsoft.servare.data.local.AppDatabase
import com.spiralsoft.servare.data.local.dao.OperationDao
import com.spiralsoft.servare.data.remote.ResultadoLoginRemoto
import com.spiralsoft.servare.data.remote.ResultadoRegistroRemoto
import com.spiralsoft.servare.data.remote.SesionRemoteDataSource
import com.spiralsoft.servare.data.sesion.GestorSesion
import com.spiralsoft.servare.data.sync.SyncEngine
import com.spiralsoft.servare.data.sync.SyncScheduler
import com.spiralsoft.servare.domain.model.EstadoConectividad
import com.spiralsoft.servare.domain.model.EstadoConectividad.CONECTADO
import com.spiralsoft.servare.domain.model.EstadoSesion
import com.spiralsoft.servare.domain.repository.LONGITUD_MINIMA_CONTRASENA
import com.spiralsoft.servare.domain.repository.ResultadoCierreSesion
import com.spiralsoft.servare.domain.repository.ResultadoInicioSesion
import com.spiralsoft.servare.domain.repository.ResultadoRegistro
import com.spiralsoft.servare.domain.repository.SesionRepository

class DefaultSesionRepository @Inject constructor(
    private val gestor: GestorSesion,
    private val remoto: SesionRemoteDataSource,
    private val monitor: ConnectivityMonitor,
    private val database: AppDatabase,
    private val operationDao: OperationDao,
    private val syncScheduler: SyncScheduler,
    private val motor: SyncEngine
) : SesionRepository {

    override val estado: StateFlow<EstadoSesion> = gestor.estado

    override suspend fun registrar(correo: String, contrasena: String, claveRegistro: String): ResultadoRegistro {
        val correoLimpio = correo.trim()
        if (!esCorreoValido(correoLimpio)) return ResultadoRegistro.CorreoInvalido
        if (contrasena.length < LONGITUD_MINIMA_CONTRASENA) return ResultadoRegistro.ContrasenaCorta

        // Sin CONECTADO no se lanza la solicitud
        val conectividad = sinServidor()
        if (conectividad != null) return ResultadoRegistro.SinConexion(conectividad)

        return when (val resultado = remoto.registrar(correoLimpio, contrasena, claveRegistro)) {
            is ResultadoRegistroRemoto.Creada ->
                if (gestor.guardar(resultado.token, resultado.correo, resultado.expiraEn)) ResultadoRegistro.Registrado
                else ResultadoRegistro.ErrorAlmacenamiento
            ResultadoRegistroRemoto.CorreoYaRegistrado -> ResultadoRegistro.CorreoYaRegistrado
            ResultadoRegistroRemoto.Rechazado -> ResultadoRegistro.Rechazado
            ResultadoRegistroRemoto.ErrorServidor -> ResultadoRegistro.ErrorServidor
            ResultadoRegistroRemoto.SinRespuesta -> {
                monitor.reportarServidorNoAlcanzable()
                ResultadoRegistro.SinRespuesta
            }
            ResultadoRegistroRemoto.SinConexion -> {
                monitor.reportarServidorNoAlcanzable()
                ResultadoRegistro.SinConexion(monitor.estado.value)
            }
        }
    }

    override suspend fun iniciarSesion(correo: String, contrasena: String): ResultadoInicioSesion {
        val correoLimpio = correo.trim()
        if (!esCorreoValido(correoLimpio)) return ResultadoInicioSesion.CorreoInvalido
        // No se exige la longitud mínima: eso es regla del registro, aquí solo importa que coincida
        if (contrasena.isEmpty()) return ResultadoInicioSesion.FaltaContrasena

        val conectividad = sinServidor()
        if (conectividad != null) return ResultadoInicioSesion.SinConexion(conectividad)

        return when (val resultado = remoto.iniciarSesion(correoLimpio, contrasena)) {
            is ResultadoLoginRemoto.Autenticada ->
                if (gestor.guardar(resultado.token, resultado.correo, resultado.expiraEn)) ResultadoInicioSesion.Autenticado
                else ResultadoInicioSesion.ErrorAlmacenamiento
            ResultadoLoginRemoto.CredencialesInvalidas -> ResultadoInicioSesion.CredencialesInvalidas
            ResultadoLoginRemoto.Rechazado -> ResultadoInicioSesion.Rechazado
            ResultadoLoginRemoto.ErrorServidor -> ResultadoInicioSesion.ErrorServidor
            ResultadoLoginRemoto.SinRespuesta -> {
                monitor.reportarServidorNoAlcanzable()
                ResultadoInicioSesion.SinRespuesta
            }
            ResultadoLoginRemoto.SinConexion -> {
                monitor.reportarServidorNoAlcanzable()
                ResultadoInicioSesion.SinConexion(monitor.estado.value)
            }
        }
    }

    override suspend fun contarCambiosSinEnviar(): Int? = try {
        operationDao.contarSinTerminar()
    } catch (_: SQLiteException) {
        null
    }

    override suspend fun cerrarSesion(): ResultadoCierreSesion {
        syncScheduler.cancelar()
        val resultado = try {
            // Con el motor detenido: ningún ciclo vuelve a llenar los datos antes de que la sesión se cierre.
            // Primero los datos y luego la sesión: si algo falla a medias el usuario sigue dentro y puede reintentar
            motor.pausado {
                withContext(Dispatchers.IO) { database.clearAllTables() }
                if (gestor.cerrar()) ResultadoCierreSesion.Cerrada else ResultadoCierreSesion.ErrorAlmacenamiento
            }
        } catch (_: SQLiteException) {
            ResultadoCierreSesion.ErrorAlmacenamiento
        }
        // Sigue con sesión: se reanuda lo que se canceló
        if (resultado != ResultadoCierreSesion.Cerrada) syncScheduler.requestSync()
        return resultado
    }

    private fun esCorreoValido(correo: String) = Patterns.EMAIL_ADDRESS.matcher(correo).matches()

    // El estado del monitor si no permite lanzar la solicitud, null si se puede
    private fun sinServidor(): EstadoConectividad? = monitor.estado.value.takeIf { it != CONECTADO }
}
