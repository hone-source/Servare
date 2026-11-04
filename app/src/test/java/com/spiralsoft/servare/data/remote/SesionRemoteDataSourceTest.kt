package com.spiralsoft.servare.data.remote

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.Response
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import com.spiralsoft.servare.testutil.error

class SesionRemoteDataSourceTest {

    private class AuthApiFalsa(var respuesta: () -> Response<SesionResponse>) : AuthApi {
        var claveRecibida: String? = null
        var registroRecibido: RegistroRequest? = null
        var loginRecibido: LoginRequest? = null

        override suspend fun registrar(claveRegistro: String, registro: RegistroRequest): Response<SesionResponse> {
            claveRecibida = claveRegistro
            registroRecibido = registro
            return respuesta()
        }

        override suspend fun iniciarSesion(credenciales: LoginRequest): Response<SesionResponse> {
            loginRecibido = credenciales
            return respuesta()
        }
    }

    private val sesion = SesionResponse(token = "tok", expiraEn = 1_000L, correo = "a@b.mx")

    private fun origen(respuesta: () -> Response<SesionResponse>): Pair<AuthApiFalsa, SesionRemoteDataSource> {
        val api = AuthApiFalsa(respuesta)
        return api to SesionRemoteDataSource(api)
    }

    // ---- registro ----

    @Test
    fun registro_exitoso_entrega_la_sesion_y_envia_la_clave_de_idempotencia() = runTest {
        val (api, origen) = origen { Response.success(sesion) }

        val resultado = origen.registrar("a@b.mx", "secreta123", "clave-1")

        assertEquals(ResultadoRegistroRemoto.Creada("tok", 1_000L, "a@b.mx"), resultado)
        assertEquals("clave-1", api.claveRecibida)
        assertEquals(RegistroRequest("a@b.mx", "secreta123"), api.registroRecibido)
    }

    @Test
    fun registro_exitoso_sin_cuerpo_es_error_de_servidor() = runTest {
        val (_, origen) = origen { Response.success<SesionResponse>(null) }
        assertEquals(ResultadoRegistroRemoto.ErrorServidor, origen.registrar("a@b.mx", "x", "k"))
    }

    @Test
    fun registro_traduce_cada_codigo() = runTest {
        val esperado = mapOf(
            409 to ResultadoRegistroRemoto.CorreoYaRegistrado,
            400 to ResultadoRegistroRemoto.Rechazado,
            413 to ResultadoRegistroRemoto.Rechazado,
            422 to ResultadoRegistroRemoto.Rechazado,
            401 to ResultadoRegistroRemoto.ErrorServidor,
            500 to ResultadoRegistroRemoto.ErrorServidor,
            503 to ResultadoRegistroRemoto.ErrorServidor
        )
        for ((codigo, resultado) in esperado) {
            val (_, origen) = origen { error(codigo) }
            assertEquals("HTTP $codigo", resultado, origen.registrar("a@b.mx", "x", "k"))
        }
    }

    @Test
    fun registro_distingue_tiempo_agotado_conexion_perdida_y_respuesta_ilegible() = runTest {
        val esperado = listOf(
            InterruptedIOException("timeout") to ResultadoRegistroRemoto.SinRespuesta,
            SocketTimeoutException() to ResultadoRegistroRemoto.SinRespuesta,
            IOException("sin red") to ResultadoRegistroRemoto.SinConexion,
            SerializationException("ilegible") to ResultadoRegistroRemoto.ErrorServidor
        )
        for ((fallo, resultado) in esperado) {
            val (_, origen) = origen { throw fallo }
            assertEquals(fallo.toString(), resultado, origen.registrar("a@b.mx", "x", "k"))
        }
    }

    // ---- inicio de sesión ----

    @Test
    fun login_exitoso_entrega_la_sesion() = runTest {
        val (api, origen) = origen { Response.success(sesion) }

        val resultado = origen.iniciarSesion("a@b.mx", "secreta123")

        assertEquals(ResultadoLoginRemoto.Autenticada("tok", 1_000L, "a@b.mx"), resultado)
        assertEquals(LoginRequest("a@b.mx", "secreta123"), api.loginRecibido)
    }

    @Test
    fun login_401_son_credenciales_incorrectas_y_no_sesion_expirada() = runTest {
        val (_, origen) = origen { error(401) }
        assertEquals(ResultadoLoginRemoto.CredencialesInvalidas, origen.iniciarSesion("a@b.mx", "x"))
    }

    @Test
    fun login_traduce_el_resto_de_codigos() = runTest {
        val esperado = mapOf(
            400 to ResultadoLoginRemoto.Rechazado,
            413 to ResultadoLoginRemoto.Rechazado,
            422 to ResultadoLoginRemoto.Rechazado,
            // 409 solo existe en el registro
            409 to ResultadoLoginRemoto.ErrorServidor,
            500 to ResultadoLoginRemoto.ErrorServidor
        )
        for ((codigo, resultado) in esperado) {
            val (_, origen) = origen { error(codigo) }
            assertEquals("HTTP $codigo", resultado, origen.iniciarSesion("a@b.mx", "x"))
        }
    }

    @Test
    fun login_distingue_tiempo_agotado_conexion_perdida_y_respuesta_ilegible() = runTest {
        val esperado = listOf(
            InterruptedIOException("timeout") to ResultadoLoginRemoto.SinRespuesta,
            IOException("sin red") to ResultadoLoginRemoto.SinConexion,
            SerializationException("ilegible") to ResultadoLoginRemoto.ErrorServidor
        )
        for ((fallo, resultado) in esperado) {
            val (_, origen) = origen { throw fallo }
            assertEquals(fallo.toString(), resultado, origen.iniciarSesion("a@b.mx", "x"))
        }
    }
}
