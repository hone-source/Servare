package com.spiralsoft.servare.data.remote

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import retrofit2.Response
import java.io.IOException
import java.io.InterruptedIOException
import javax.inject.Inject

// Fuente de datos remota de la sesión.
// Traduce cada respuesta a un resultado propio de la operación
class SesionRemoteDataSource @Inject constructor(
    private val api: AuthApi
) {
    suspend fun registrar(correo: String, contrasena: String, claveRegistro: String): ResultadoRegistroRemoto =
        llamar(
            sinRespuesta = ResultadoRegistroRemoto.SinRespuesta,
            sinConexion = ResultadoRegistroRemoto.SinConexion,
            errorServidor = ResultadoRegistroRemoto.ErrorServidor
        ) { traducirRegistro(api.registrar(claveRegistro, RegistroRequest(correo, contrasena))) }

    suspend fun iniciarSesion(correo: String, contrasena: String): ResultadoLoginRemoto =
        llamar(
            sinRespuesta = ResultadoLoginRemoto.SinRespuesta,
            sinConexion = ResultadoLoginRemoto.SinConexion,
            errorServidor = ResultadoLoginRemoto.ErrorServidor
        ) { traducirLogin(api.iniciarSesion(LoginRequest(correo, contrasena))) }

    // Lo que no depende de la operación: tiempo agotado, conexión perdida y respuesta ilegible
    private suspend fun <T> llamar(
        sinRespuesta: T,
        sinConexion: T,
        errorServidor: T,
        llamada: suspend () -> T
    ): T = try {
        llamada()
    } catch (e: CancellationException) {
        throw e
    } catch (_: InterruptedIOException) {
        // El timeout de la llamada (callTimeout) lanza esta, no SocketTimeoutException. Va antes que IOException
        sinRespuesta
    } catch (_: IOException) {
        sinConexion
    } catch (_: SerializationException) {
        errorServidor
    }

    private fun traducirRegistro(respuesta: Response<SesionResponse>): ResultadoRegistroRemoto {
        val codigo = respuesta.code()
        return when {
            respuesta.isSuccessful -> respuesta.body()
                ?.let { ResultadoRegistroRemoto.Creada(it.token, it.expiraEn, it.correo) }
                ?: ResultadoRegistroRemoto.ErrorServidor
            codigo == 409 -> ResultadoRegistroRemoto.CorreoYaRegistrado
            codigo == 400 || codigo == 413 || codigo == 422 -> ResultadoRegistroRemoto.Rechazado
            else -> ResultadoRegistroRemoto.ErrorServidor
        }
    }

    private fun traducirLogin(respuesta: Response<SesionResponse>): ResultadoLoginRemoto {
        val codigo = respuesta.code()
        return when {
            respuesta.isSuccessful -> respuesta.body()
                ?.let { ResultadoLoginRemoto.Autenticada(it.token, it.expiraEn, it.correo) }
                ?: ResultadoLoginRemoto.ErrorServidor
            // Aquí el 401 es "credenciales incorrectas", no "sesión expirada" como en la cola
            codigo == 401 -> ResultadoLoginRemoto.CredencialesInvalidas
            codigo == 400 || codigo == 413 || codigo == 422 -> ResultadoLoginRemoto.Rechazado
            else -> ResultadoLoginRemoto.ErrorServidor
        }
    }
}
