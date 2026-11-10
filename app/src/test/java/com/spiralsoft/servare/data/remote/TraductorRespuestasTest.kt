package com.spiralsoft.servare.data.remote

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.Response
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import com.spiralsoft.servare.domain.model.CausaConflicto
import com.spiralsoft.servare.testutil.error

// Tabla de errores de docs/2.2.md: qué hace la cola con cada respuesta del servidor
class TraductorRespuestasTest {

    private val traductor = TraductorRespuestas(Json { ignoreUnknownKeys = true })

    private suspend fun enviar(respuesta: Response<VersionResponse>) = traductor.enviar { respuesta }

    private suspend fun enviarFallando(fallo: Throwable) = traductor.enviar { throw fallo }

    // ---- push ----

    @Test
    fun envio_exitoso_confirma_con_la_version_nueva() = runTest {
        assertEquals(ResultadoEnvio.Confirmada(3), enviar(Response.success(VersionResponse(3))))
    }

    @Test
    fun envio_exitoso_sin_version_confirma_sin_version() = runTest {
        assertEquals(ResultadoEnvio.Confirmada(null), enviar(Response.success(VersionResponse())))
        assertEquals(ResultadoEnvio.Confirmada(null), enviar(Response.success<VersionResponse>(null)))
    }

    @Test
    fun envio_401_y_403_pausan_la_cola() = runTest {
        assertEquals(ResultadoEnvio.SesionExpirada, enviar(error(401)))
        assertEquals(ResultadoEnvio.SesionExpirada, enviar(error(403)))
    }

    @Test
    fun envio_409_es_conflicto_por_version_y_conserva_el_registro_vigente() = runTest {
        val actual = """{"entityId":"p1","titulo":"Del servidor","version":7}"""
        val resultado = enviar(error(409, """{"error":"conflicto","actual":$actual}"""))

        resultado as ResultadoEnvio.Conflicto
        assertEquals(CausaConflicto.VERSION_DISTINTA, resultado.causa)
        assertEquals(actual, resultado.actual)
    }

    @Test
    fun envio_409_con_cuerpo_inservible_sigue_siendo_conflicto_sin_registro_vigente() = runTest {
        val cuerpos = listOf(
            "",                                 // vacío
            "no es json",                       // mal formado
            "[1,2,3]",                          // JSON que no es un objeto
            """{"error":"conflicto"}""",        // sin "actual"
            """{"actual":null}"""               // "actual" nulo
        )
        for (cuerpo in cuerpos) {
            val resultado = enviar(error(409, cuerpo))
            resultado as ResultadoEnvio.Conflicto
            assertEquals("cuerpo: $cuerpo", CausaConflicto.VERSION_DISTINTA, resultado.causa)
            assertNull("cuerpo: $cuerpo", resultado.actual)
        }
    }

    @Test
    fun envio_404_y_410_son_conflicto_por_registro_inexistente() = runTest {
        for (codigo in listOf(404, 410)) {
            val resultado = enviar(error(codigo))
            resultado as ResultadoEnvio.Conflicto
            assertEquals(CausaConflicto.INEXISTENTE_EN_SERVIDOR, resultado.causa)
            assertNull(resultado.actual)
        }
    }

    @Test
    fun envio_413_422_y_otros_4xx_son_definitivos() = runTest {
        for (codigo in listOf(400, 413, 418, 422)) {
            assertTrue("HTTP $codigo", enviar(error(codigo)) is ResultadoEnvio.Definitivo)
        }
    }

    @Test
    fun envio_con_5xx_408_o_429_es_temporal_pero_el_servidor_si_respondio() = runTest {
        for (codigo in listOf(408, 429, 500, 502, 503)) {
            val resultado = enviar(error(codigo))
            resultado as ResultadoEnvio.Temporal
            assertEquals("HTTP $codigo", false, resultado.sinRespuesta)
            assertTrue(resultado.motivo.contains("HTTP $codigo"))
        }
    }

    @Test
    fun envio_con_timeout_es_temporal_y_marca_que_no_hubo_respuesta() = runTest {
        val resultado = enviarFallando(SocketTimeoutException())
        resultado as ResultadoEnvio.Temporal
        assertTrue(resultado.sinRespuesta)
    }

    @Test
    fun envio_con_conexion_perdida_es_incierto_y_no_cuenta_como_intento() = runTest {
        assertEquals(ResultadoEnvio.Incierto, enviarFallando(ConnectException()))
        assertEquals(ResultadoEnvio.Incierto, enviarFallando(IOException("Conexión reiniciada")))
    }

    @Test
    fun envio_con_respuesta_ilegible_es_definitivo() = runTest {
        assertTrue(enviarFallando(SerializationException("mal formada")) is ResultadoEnvio.Definitivo)
    }

    @Test
    fun envio_no_traga_la_cancelacion() = runTest {
        try {
            enviarFallando(CancellationException("cancelada"))
            fail("debía relanzar la cancelación")
        } catch (e: CancellationException) {
            assertEquals("cancelada", e.message)
        }
    }

    // ---- pull ----

    private suspend fun listar(respuesta: Response<List<String>>) = traductor.listar { respuesta }

    private suspend fun listarFallando(fallo: Throwable) = traductor.listar<String> { throw fallo }

    @Test
    fun descarga_exitosa_entrega_los_registros() = runTest {
        assertEquals(ResultadoPull.Datos(listOf("a", "b")), listar(Response.success(listOf("a", "b"))))
    }

    @Test
    fun descarga_exitosa_sin_cuerpo_es_una_lista_vacia() = runTest {
        assertEquals(ResultadoPull.Datos(emptyList<String>()), listar(Response.success<List<String>>(null)))
    }

    @Test
    fun descarga_401_y_403_pausan_la_cola() = runTest {
        assertEquals(ResultadoPull.SesionExpirada, listar(error(401)))
        assertEquals(ResultadoPull.SesionExpirada, listar(error(403)))
    }

    @Test
    fun descarga_con_otro_error_http_es_fallo_con_el_codigo() = runTest {
        for (codigo in listOf(404, 500, 503)) {
            val resultado = listar(error(codigo))
            resultado as ResultadoPull.Fallo
            assertTrue(resultado.motivo.contains("HTTP $codigo"))
        }
    }

    @Test
    fun descarga_con_timeout_es_fallo_y_no_servidor_caido() = runTest {
        assertTrue(listarFallando(SocketTimeoutException()) is ResultadoPull.Fallo)
    }

    @Test
    fun descarga_con_conexion_perdida_es_sin_servidor() = runTest {
        assertEquals(ResultadoPull.SinServidor, listarFallando(ConnectException()))
    }

    @Test
    fun descarga_con_respuesta_ilegible_es_fallo() = runTest {
        assertTrue(listarFallando(SerializationException("mal formada")) is ResultadoPull.Fallo)
    }

    @Test
    fun descarga_no_traga_la_cancelacion() = runTest {
        try {
            listarFallando(CancellationException("cancelada"))
            fail("debía relanzar la cancelación")
        } catch (_: CancellationException) {
        }
    }
}
