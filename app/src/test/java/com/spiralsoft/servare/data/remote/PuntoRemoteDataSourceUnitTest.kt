package com.spiralsoft.servare.data.remote

import com.spiralsoft.servare.data.local.entity.PuntoEntity
import com.spiralsoft.servare.testutil.ServidorApi
import com.spiralsoft.servare.testutil.cabecera
import com.spiralsoft.servare.testutil.cuerpoJson
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

// Mismo contrato que Prueba con otra ruta y otro cuerpo: las coordenadas viajan como números, sin perder decimales
class PuntoRemoteDataSourceUnitTest {
    @get:Rule val api = ServidorApi()

    private val remoto by lazy { PuntoRemoteDataSource(api.puntoApi, TraductorRespuestas(api.json)) }
    private val punto = PuntoEntity("p1", "Mirador", 22.770123, -102.573456, createdAt = 5, version = 2)

    @Test
    fun crear_va_a_la_ruta_de_puntos_con_las_coordenadas_exactas_y_sin_baseVersion() = runBlocking {
        api.responder(201, """{"version":1}""")

        val resultado = remoto.crear("op-1", punto)

        val peticion = api.solicitud()
        assertEquals("POST", peticion.method)
        assertEquals("/api/puntos/p1", peticion.url.encodedPath)
        assertEquals("op-1", peticion.cabecera("Idempotency-Key"))
        assertEquals("Bearer tok-123", peticion.cabecera("Authorization"))
        val cuerpo = peticion.cuerpoJson()
        assertEquals(setOf("entityId", "nombre", "latitud", "longitud", "createdAt"), cuerpo.keys)
        assertEquals("22.770123", cuerpo.getValue("latitud").toString())
        assertEquals("-102.573456", cuerpo.getValue("longitud").toString())
        assertEquals(ResultadoEnvio.Confirmada(1), resultado)
    }

    @Test
    fun actualizar_lleva_la_baseVersion_y_solo_si_se_conoce() = runBlocking {
        api.responder(200, """{"version":3}""")
        api.responder(200, """{"version":3}""")

        remoto.actualizar("op-2", punto, baseVersion = 2)
        remoto.actualizar("op-3", punto, baseVersion = null)

        val con = api.solicitud()
        val sin = api.solicitud()
        assertEquals("PUT", con.method)
        assertEquals("2", con.cuerpoJson().getValue("baseVersion").toString())
        assertFalse("baseVersion" in sin.cuerpoJson().keys)
    }

    @Test
    fun eliminar_lleva_la_baseVersion_en_la_consulta() = runBlocking {
        api.responder(200, """{"version":4}""")
        api.responder(204)

        remoto.eliminar("op-4", "p1", baseVersion = 3)
        remoto.eliminar("op-5", "p1", baseVersion = null)

        val con = api.solicitud()
        val sin = api.solicitud()
        assertEquals("DELETE", con.method)
        assertEquals("/api/puntos/p1", con.url.encodedPath)
        assertEquals("3", con.url.queryParameter("baseVersion"))
        assertNull(sin.url.query)
    }

    @Test
    fun listar_lee_los_puntos_del_servidor() = runBlocking {
        api.responder(200, """[{"entityId":"p1","nombre":"Mirador","latitud":22.5,"longitud":-102,"createdAt":1,"version":2}]""")

        val resultado = remoto.listar() as ResultadoPull.Datos<PuntoRemoto>

        assertEquals("/api/puntos", api.solicitud().url.encodedPath)
        assertEquals(listOf(PuntoRemoto("p1", "Mirador", 22.5, -102.0, 1, 2)), resultado.registros)
    }

    @Test
    fun el_401_y_el_servidor_caido_se_traducen_por_el_cable() = runBlocking {
        api.responder(401)
        assertEquals(ResultadoEnvio.SesionExpirada, remoto.crear("op", punto))

        api.servidor.close()
        assertEquals(ResultadoEnvio.Incierto, remoto.crear("op", punto))
    }
}
