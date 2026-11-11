package com.spiralsoft.servare.data.remote

import com.spiralsoft.servare.testutil.ServidorApi
import com.spiralsoft.servare.testutil.cabecera
import com.spiralsoft.servare.testutil.cuerpoJson
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit

// Registro e inicio de sesión por el cable: no llevan token (aún no hay sesión) y el registro lleva la clave
class SesionRemoteDataSourceConCableUnitTest {
    @get:Rule val api = ServidorApi()

    private val remoto by lazy { SesionRemoteDataSource(api.authApi) }
    private val sesion = """{"token":"nuevo-token","expiraEn":1000,"correo":"a@b.mx"}"""

    @Test
    fun registrar_manda_correo_y_contrasena_con_la_clave_de_registro_y_sin_token() = runBlocking {
        api.responder(201, sesion)

        val resultado = remoto.registrar("a@b.mx", "secreta123", "clave-uuid")

        val peticion = api.solicitud()
        assertEquals("POST", peticion.method)
        assertEquals("/api/auth/registro", peticion.url.encodedPath)
        assertEquals("clave-uuid", peticion.cabecera("Idempotency-Key"))
        assertNull("aún no hay sesión: el token de otra cuenta no debe viajar", peticion.cabecera("Authorization"))
        assertEquals(setOf("correo", "contrasena"), peticion.cuerpoJson().keys)
        assertEquals(ResultadoRegistroRemoto.Creada("nuevo-token", 1000L, "a@b.mx"), resultado)
    }

    @Test
    fun iniciar_sesion_no_lleva_clave_de_idempotencia_ni_token() = runBlocking {
        api.responder(200, sesion)

        val resultado = remoto.iniciarSesion("a@b.mx", "secreta123")

        val peticion = api.solicitud()
        assertEquals("/api/auth/login", peticion.url.encodedPath)
        assertNull(peticion.cabecera("Idempotency-Key"))
        assertNull(peticion.cabecera("Authorization"))
        assertEquals(ResultadoLoginRemoto.Autenticada("nuevo-token", 1000L, "a@b.mx"), resultado)
    }

    @Test
    fun los_codigos_del_registro_por_el_cable() = runBlocking {
        val esperados = mapOf(
            409 to ResultadoRegistroRemoto.CorreoYaRegistrado,
            400 to ResultadoRegistroRemoto.Rechazado,
            422 to ResultadoRegistroRemoto.Rechazado,
            500 to ResultadoRegistroRemoto.ErrorServidor,
        )
        for ((codigo, esperado) in esperados) {
            api.responder(codigo)
            assertEquals("HTTP $codigo", esperado, remoto.registrar("a@b.mx", "secreta123", "c"))
        }
    }

    @Test
    fun los_codigos_del_inicio_de_sesion_por_el_cable() = runBlocking {
        api.responder(401)
        assertEquals(ResultadoLoginRemoto.CredencialesInvalidas, remoto.iniciarSesion("a@b.mx", "mala"))

        api.responder(500)
        assertEquals(ResultadoLoginRemoto.ErrorServidor, remoto.iniciarSesion("a@b.mx", "x"))
    }

    @Test
    fun una_respuesta_correcta_ilegible_es_un_error_del_servidor() = runBlocking {
        api.responder(200, "no es json")
        assertEquals(ResultadoLoginRemoto.ErrorServidor, remoto.iniciarSesion("a@b.mx", "x"))
    }

    @Test
    fun con_el_servidor_caido_es_sin_conexion() = runBlocking {
        api.servidor.close()

        assertEquals(ResultadoRegistroRemoto.SinConexion, remoto.registrar("a@b.mx", "secreta123", "c"))
        assertEquals(ResultadoLoginRemoto.SinConexion, remoto.iniciarSesion("a@b.mx", "x"))
    }

    // La llamada completa se acota a 5 s (NetworkModule.authApi): el usuario está mirando la pantalla
    @Test
    fun un_servidor_que_no_responde_en_cinco_segundos_es_sin_respuesta() = runBlocking {
        api.servidor.enqueue(MockResponse.Builder().code(200).body(sesion).headersDelay(8, TimeUnit.SECONDS).build())

        val inicio = System.nanoTime()
        val resultado = remoto.iniciarSesion("a@b.mx", "x")
        val segundos = (System.nanoTime() - inicio) / 1_000_000_000.0

        assertEquals(ResultadoLoginRemoto.SinRespuesta, resultado)
        assertEquals("corta cerca de los 5 s, no espera al servidor", 5.0, segundos, 1.5)
    }
}
