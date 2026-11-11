package com.spiralsoft.servare.testutil

import com.spiralsoft.servare.data.remote.AuthApi
import com.spiralsoft.servare.data.remote.AuthInterceptor
import com.spiralsoft.servare.data.remote.FichaApi
import com.spiralsoft.servare.data.remote.PruebaApi
import com.spiralsoft.servare.data.remote.PuntoApi
import com.spiralsoft.servare.data.sesion.GestorSesion
import com.spiralsoft.servare.di.NetworkModule
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.rules.TestWatcher
import org.junit.runner.Description

// Un servidor HTTP real (MockWebServer) con Retrofit armado por el propio NetworkModule: lo que se prueba es lo que
// sale por el cable, con el cliente, el convertidor y el interceptor de producción
class ServidorApi : TestWatcher() {
    val servidor = MockWebServer()

    // La misma configuración que AppModule
    val json = Json { ignoreUnknownKeys = true }

    // null: sin sesión, el interceptor no añade la cabecera
    var token: String? = "tok-123"

    private val gestor = mockk<GestorSesion>().also { every { it.token() } answers { token } }
    private val cliente = OkHttpClient()
    private val retrofit by lazy { NetworkModule.retrofit(servidor.url("/"), cliente, json) }

    val pruebaApi: PruebaApi by lazy { NetworkModule.pruebaApi(retrofit, cliente, AuthInterceptor(gestor)) }
    val puntoApi: PuntoApi by lazy { NetworkModule.puntoApi(retrofit, cliente, AuthInterceptor(gestor)) }
    val fichaApi: FichaApi by lazy { NetworkModule.fichaApi(retrofit, cliente, AuthInterceptor(gestor)) }
    val authApi: AuthApi by lazy { NetworkModule.authApi(retrofit, cliente) }

    override fun starting(description: Description) = servidor.start()

    override fun finished(description: Description) = servidor.close()

    fun responder(codigo: Int, cuerpo: String? = null) {
        val respuesta = MockResponse.Builder().code(codigo)
        cuerpo?.let { respuesta.addHeader("Content-Type", "application/json").body(it) }
        servidor.enqueue(respuesta.build())
    }

    fun solicitud(): RecordedRequest = servidor.takeRequest()
}

fun RecordedRequest.cuerpoJson(): JsonObject = Json.parseToJsonElement(checkNotNull(body).utf8()).jsonObject

fun RecordedRequest.cabecera(nombre: String): String? = headers[nombre]

operator fun JsonObject.contains(campo: String) = containsKey(campo)

fun JsonElement.texto() = toString().trim('"')
