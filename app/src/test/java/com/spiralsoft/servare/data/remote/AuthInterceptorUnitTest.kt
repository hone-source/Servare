package com.spiralsoft.servare.data.remote

import com.spiralsoft.servare.data.sesion.GestorSesion
import io.mockk.every
import io.mockk.mockk
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Con MockWebServer se ve exactamente lo que sale por la red
class AuthInterceptorUnitTest {
    private val servidor = MockWebServer().apply { enqueue(MockResponse.Builder().code(200).build()); start() }
    private val gestor = mockk<GestorSesion>()

    @After
    fun cerrar() = servidor.close()

    private fun llamar(): String? {
        val cliente = OkHttpClient.Builder().addInterceptor(AuthInterceptor(gestor)).build()
        cliente.newCall(Request.Builder().url(servidor.url("/api/pruebas")).header("X-Otro", "1").build()).execute().close()
        return servidor.takeRequest().headers["Authorization"]
    }

    @Test
    fun con_token_la_peticion_lleva_el_bearer() {
        every { gestor.token() } returns "abc123"
        assertEquals("Bearer abc123", llamar())
    }

    @Test
    fun sin_token_sale_igual_y_sin_cabecera_de_autorizacion() {
        every { gestor.token() } returns null
        assertNull(llamar())
    }

    @Test
    fun el_token_se_lee_en_cada_peticion() {
        every { gestor.token() } returnsMany listOf("uno", "dos")
        servidor.enqueue(MockResponse.Builder().code(200).build())
        assertEquals("Bearer uno", llamar())
        assertEquals("Bearer dos", llamar())
    }
}
