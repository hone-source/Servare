package com.spiralsoft.servare.data.remote

import com.spiralsoft.servare.data.local.dao.aCompleta
import com.spiralsoft.servare.domain.model.Ficha
import com.spiralsoft.servare.testutil.ServidorApi
import com.spiralsoft.servare.testutil.cabecera
import com.spiralsoft.servare.testutil.cuerpoJson
import com.spiralsoft.servare.testutil.fichaLlena
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

// La ficha viaja como "datos" (con sus listas) más createdAt y baseVersion,
// los valores por defecto no viajan
class FichaRemoteDataSourceUnitTest {
    @get:Rule val api = ServidorApi()

    private val remoto by lazy { FichaRemoteDataSource(api.fichaApi, TraductorRespuestas(api.json)) }
    private val ficha = fichaLlena("Cerro").aCompleta("f1", createdAt = 77, version = 4)

    @Test
    fun crear_va_a_la_ruta_de_fichas_con_los_datos_las_listas_y_sin_baseVersion() = runBlocking {
        api.responder(201, """{"version":1}""")

        val resultado = remoto.crear("op-1", ficha)

        val peticion = api.solicitud()
        assertEquals("POST", peticion.method)
        assertEquals("/api/fichas/f1", peticion.url.encodedPath)
        assertEquals("op-1", peticion.cabecera("Idempotency-Key"))
        assertEquals("Bearer tok-123", peticion.cabecera("Authorization"))
        val cuerpo = peticion.cuerpoJson()
        assertEquals(setOf("datos", "createdAt"), cuerpo.keys)
        assertEquals("77", cuerpo.getValue("createdAt").toString())
        val datos = cuerpo.getValue("datos").jsonObject
        assertEquals("\"Cerro\"", datos.getValue("nombre").toString())
        assertEquals(3, datos.getValue("rituales").jsonArray.size)
        assertEquals(2, datos.getValue("participantes").jsonArray.size)
        assertEquals(ResultadoEnvio.Confirmada(1), resultado)
    }

    @Test
    fun las_listas_viajan_en_el_orden_de_la_ficha() = runBlocking {
        api.responder(201, """{"version":1}""")

        remoto.crear("op-1", ficha)

        val rituales: JsonArray = api.solicitud().cuerpoJson().getValue("datos").jsonObject.getValue("rituales").jsonArray
        assertEquals(
            listOf("\"Primero\"", "\"Segundo\"", "\"Tercero\""),
            rituales.map { it.jsonObject.getValue("nombres").toString() }
        )
    }

    @Test
    fun una_ficha_vacia_no_manda_campos_por_defecto() = runBlocking {
        api.responder(201, """{"version":1}""")

        remoto.crear("op-1", Ficha(nombre = "Solo nombre").aCompleta("f2", 1, null))

        val datos = api.solicitud().cuerpoJson().getValue("datos").jsonObject
        assertEquals(setOf("nombre"), datos.keys)
    }

    @Test
    fun actualizar_lleva_la_baseVersion_solo_si_se_conoce() = runBlocking {
        api.responder(200, """{"version":5}""")
        api.responder(200, """{"version":5}""")

        remoto.actualizar("op-2", ficha, baseVersion = 4)
        remoto.actualizar("op-3", ficha, baseVersion = null)

        val con = api.solicitud()
        val sin = api.solicitud()
        assertEquals("PUT", con.method)
        assertEquals("4", con.cuerpoJson().getValue("baseVersion").toString())
        assertFalse("baseVersion" in sin.cuerpoJson().keys)
    }

    @Test
    fun eliminar_lleva_la_baseVersion_en_la_consulta() = runBlocking {
        api.responder(200, """{"version":6}""")
        api.responder(204)

        remoto.eliminar("op-4", "f1", baseVersion = 5)
        remoto.eliminar("op-5", "f1", baseVersion = null)

        val con = api.solicitud()
        assertEquals("DELETE", con.method)
        assertEquals("/api/fichas/f1", con.url.encodedPath)
        assertEquals("5", con.url.queryParameter("baseVersion"))
        assertNull(api.solicitud().url.query)
    }

    @Test
    fun listar_lee_las_fichas_con_sus_datos_y_tolera_campos_que_faltan_o_sobran() = runBlocking {
        api.responder(
            200,
            """[{"entityId":"f1","datos":{"nombre":"Cerro","rituales":[{"nombres":"R1"}],"campoNuevo":1},"createdAt":3,"version":2},
                {"entityId":"f2","datos":{},"createdAt":4}]"""
        )

        val resultado = remoto.listar() as ResultadoPull.Datos<FichaRemota>

        assertEquals("/api/fichas", api.solicitud().url.encodedPath)
        assertEquals(listOf("f1", "f2"), resultado.registros.map { it.entityId })
        assertEquals("Cerro", resultado.registros[0].datos.nombre)
        assertEquals(1, resultado.registros[0].datos.rituales.size)
        assertNull(resultado.registros[1].version)
    }

    @Test
    fun el_409_conserva_el_registro_vigente_para_compararlo() = runBlocking {
        api.responder(409, """{"actual":{"entityId":"f1","datos":{"nombre":"Del servidor"},"createdAt":1,"version":9}}""")

        val resultado = remoto.actualizar("op", ficha, 4) as ResultadoEnvio.Conflicto

        assertTrue(resultado.actual!!.contains("Del servidor"))
    }
}
