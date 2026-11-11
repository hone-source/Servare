package com.spiralsoft.servare.data.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.spiralsoft.servare.data.sync.SyncScheduler
import com.spiralsoft.servare.domain.model.EstadoConectividad
import com.spiralsoft.servare.domain.model.EstadoConectividad.CONECTADO
import com.spiralsoft.servare.domain.model.EstadoConectividad.RED_SIN_SERVIDOR
import com.spiralsoft.servare.domain.model.EstadoConectividad.SIN_RED
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.TimeUnit

// Las tres posiciones del monitor (SIN_RED, RED_SIN_SERVIDOR, CONECTADO) con un servidor HTTP real
// (MockWebServer) y el sistema operativo doblado: los callbacks de red los dispara la prueba
@RunWith(RobolectricTestRunner::class)
class ConnectivityMonitorUnitTest {
    private val servidor = MockWebServer()
    @Volatile private var respuesta = { MockResponse.Builder().code(200).build() }

    private val callback = slot<ConnectivityManager.NetworkCallback>()
    private val scheduler = mockk<SyncScheduler>(relaxed = true)
    private val gestorDeRed = mockk<ConnectivityManager>(relaxed = true)
    private val contexto = mockk<Context>()
    private lateinit var monitor: ConnectivityMonitor

    @Before
    fun preparar() {
        servidor.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = respuesta()
        }
        servidor.start()
        every { contexto.getSystemService(ConnectivityManager::class.java) } returns gestorDeRed
        every { gestorDeRed.registerDefaultNetworkCallback(capture(callback)) } just Runs
        monitor = ConnectivityMonitor(contexto, OkHttpClient(), servidor.url("/"), scheduler)
    }

    @After
    fun cerrar() = servidor.close()

    private fun red() = mockk<Network>()

    private fun conInternet(valor: Boolean = true) = mockk<NetworkCapabilities> {
        every { hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns valor
    }

    private fun aparece(red: Network, internet: Boolean = true) =
        callback.captured.onCapabilitiesChanged(red, conInternet(internet))

    private suspend fun esperar(estado: EstadoConectividad) =
        withTimeout(5_000) { monitor.estado.first { it == estado } }

    private fun conectar(red: Network = red()): Network {
        monitor.iniciar()
        aparece(red)
        return red
    }

    // --- Arranque ---

    @Test
    fun sin_red_es_el_estado_inicial_y_no_se_va_al_servidor() = runBlocking {
        assertEquals(SIN_RED, monitor.estado.value)
        assertEquals(false, monitor.confirmarAlcanzabilidad())
        assertEquals(0, servidor.requestCount)
    }

    @Test
    fun iniciar_registra_el_callback_una_sola_vez() {
        monitor.iniciar()
        monitor.iniciar()
        verify(exactly = 1) { gestorDeRed.registerDefaultNetworkCallback(any<ConnectivityManager.NetworkCallback>()) }
    }

    // --- Transiciones ---

    @Test
    fun una_red_nueva_pasa_a_conectado_solo_tras_confirmar_el_servidor_y_pide_un_ciclo() = runBlocking {
        conectar()

        esperar(CONECTADO)

        assertTrue(servidor.requestCount >= 1)
        verify(exactly = 1) { scheduler.requestSync() }
    }

    @Test
    fun con_red_pero_sin_servidor_se_queda_en_RED_SIN_SERVIDOR_y_no_pide_ciclo() = runBlocking {
        respuesta = { MockResponse.Builder().code(503).build() }

        conectar()
        delay(400)

        assertEquals(RED_SIN_SERVIDOR, monitor.estado.value)
        verify(exactly = 0) { scheduler.requestSync() }
    }

    @Test
    fun una_red_sin_internet_equivale_a_no_tener_red() = runBlocking {
        conectar()
        esperar(CONECTADO)

        aparece(red(), internet = false)

        assertEquals(SIN_RED, monitor.estado.value)
    }

    @Test
    fun perder_la_red_actual_pasa_a_SIN_RED_pero_perder_otra_no() = runBlocking {
        val actual = conectar()
        esperar(CONECTADO)

        callback.captured.onLost(red())
        assertEquals("otra red no es la actual", CONECTADO, monitor.estado.value)

        callback.captured.onLost(actual)
        assertEquals(SIN_RED, monitor.estado.value)
    }

    @Test
    fun los_cambios_de_capacidades_de_la_misma_red_no_reinician_nada() = runBlocking {
        val r = conectar()
        esperar(CONECTADO)
        val peticiones = servidor.requestCount

        repeat(3) { aparece(r) }

        assertEquals(CONECTADO, monitor.estado.value)
        assertEquals(peticiones, servidor.requestCount)
        verify(exactly = 1) { scheduler.requestSync() }
    }

    @Test
    fun una_red_distinta_obliga_a_confirmar_de_nuevo_y_cuenta_como_recuperacion() = runBlocking {
        conectar()
        esperar(CONECTADO)
        val peticiones = servidor.requestCount
        respuesta = { MockResponse.Builder().code(200).headersDelay(300, TimeUnit.MILLISECONDS).build() }

        aparece(red())
        assertEquals("hasta confirmar no se asume el servidor", RED_SIN_SERVIDOR, monitor.estado.value)
        withTimeout(5_000) { while (monitor.estado.value != CONECTADO) delay(20) }

        assertTrue(servidor.requestCount > peticiones)
        verify(exactly = 2) { scheduler.requestSync() }
    }

    // --- confirmarAlcanzabilidad y reportes ---

    @Test
    fun confirmar_con_el_servidor_respondiendo_es_true_y_no_repite_la_recuperacion() = runBlocking {
        conectar()
        esperar(CONECTADO)

        assertEquals(true, monitor.confirmarAlcanzabilidad())

        verify(exactly = 1) { scheduler.requestSync() }
    }

    @Test
    fun confirmar_con_el_servidor_caido_es_false_y_baja_a_RED_SIN_SERVIDOR() = runBlocking {
        conectar()
        esperar(CONECTADO)
        respuesta = { MockResponse.Builder().code(500).build() }

        assertEquals(false, monitor.confirmarAlcanzabilidad())

        assertEquals(RED_SIN_SERVIDOR, monitor.estado.value)
    }

    @Test
    fun una_respuesta_que_no_es_2xx_no_cuenta_como_servidor_alcanzable() = runBlocking {
        conectar()
        esperar(CONECTADO)

        for (codigo in listOf(301, 401, 404, 500, 503)) {
            respuesta = { MockResponse.Builder().code(codigo).build() }
            assertEquals("código $codigo", false, monitor.confirmarAlcanzabilidad())
            respuesta = { MockResponse.Builder().code(200).build() }
            monitor.confirmarAlcanzabilidad()
            withTimeout(5_000) { while (monitor.estado.value != CONECTADO) delay(20) }
        }
    }

    @Test
    fun reportar_el_servidor_caido_estando_conectado_baja_a_RED_SIN_SERVIDOR() = runBlocking {
        conectar()
        esperar(CONECTADO)
        respuesta = { MockResponse.Builder().code(503).build() }

        monitor.reportarServidorNoAlcanzable()

        assertEquals(RED_SIN_SERVIDOR, monitor.estado.value)
    }

    @Test
    fun reportar_el_servidor_caido_sin_red_no_cambia_nada() {
        monitor.reportarServidorNoAlcanzable()
        assertEquals(SIN_RED, monitor.estado.value)
    }

    @Test
    fun una_perdida_de_red_durante_el_health_check_no_se_pisa_con_conectado() = runBlocking {
        respuesta = { MockResponse.Builder().code(200).headersDelay(600, TimeUnit.MILLISECONDS).build() }
        val r = conectar()
        delay(150) // el health check ya está en vuelo

        callback.captured.onLost(r)
        delay(900)

        assertEquals(SIN_RED, monitor.estado.value)
        verify(exactly = 0) { scheduler.requestSync() }
    }
}
