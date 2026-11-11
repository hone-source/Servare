package com.spiralsoft.servare.data.remote

import com.spiralsoft.servare.data.local.entity.PruebaEntity
import com.spiralsoft.servare.domain.model.CausaConflicto
import com.spiralsoft.servare.testutil.ServidorApi
import com.spiralsoft.servare.testutil.cabecera
import com.spiralsoft.servare.testutil.cuerpoJson
import com.spiralsoft.servare.testutil.texto
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

// El molde de los recursos sincronizables, de la llamada al cable y de vuelta: método, ruta, cabeceras, cuerpo y resultado
class PruebaRemoteDataSourceUnitTest {
    @get:Rule val api = ServidorApi()

    private val remoto by lazy { PruebaRemoteDataSource(api.pruebaApi, TraductorRespuestas(api.json)) }
    private val prueba = PruebaEntity("e1", "Título", "texto", 7, createdAt = 99, version = 3)

    // --- crear ---

    @Test
    fun crear_hace_un_POST_a_la_ruta_del_registro_con_la_clave_de_idempotencia_y_el_token() = runBlocking {
        api.responder(201, """{"version":1}""")

        val resultado = remoto.crear("op-1", prueba)

        val peticion = api.solicitud()
        assertEquals("POST", peticion.method)
        assertEquals("/api/pruebas/e1", peticion.url.encodedPath)
        assertEquals("op-1", peticion.cabecera("Idempotency-Key"))
        assertEquals("Bearer tok-123", peticion.cabecera("Authorization"))
        assertTrue(peticion.cabecera("Content-Type")!!.startsWith("application/json"))
        assertEquals(ResultadoEnvio.Confirmada(1), resultado)
    }

    @Test
    fun crear_manda_el_contenido_y_nunca_una_baseVersion_ni_la_version_local() = runBlocking {
        api.responder(201, """{"version":1}""")

        remoto.crear("op-1", prueba)

        val cuerpo = api.solicitud().cuerpoJson()
        assertEquals(setOf("entityId", "titulo", "texto", "numero", "createdAt"), cuerpo.keys)
        assertEquals("e1", cuerpo.getValue("entityId").texto())
        assertEquals("Título", cuerpo.getValue("titulo").texto())
        assertEquals("7", cuerpo.getValue("numero").toString())
        assertEquals("99", cuerpo.getValue("createdAt").toString())
    }

    // --- actualizar ---

    @Test
    fun actualizar_hace_un_PUT_con_la_baseVersion_en_el_cuerpo() = runBlocking {
        api.responder(200, """{"version":4}""")

        val resultado = remoto.actualizar("op-2", prueba, baseVersion = 3)

        val peticion = api.solicitud()
        assertEquals("PUT", peticion.method)
        assertEquals("/api/pruebas/e1", peticion.url.encodedPath)
        assertEquals("op-2", peticion.cabecera("Idempotency-Key"))
        assertEquals("3", peticion.cuerpoJson().getValue("baseVersion").toString())
        assertEquals(ResultadoEnvio.Confirmada(4), resultado)
    }

    @Test
    fun actualizar_sin_baseVersion_no_la_manda() = runBlocking {
        api.responder(200, """{"version":4}""")

        remoto.actualizar("op-2", prueba, baseVersion = null)

        assertFalse("baseVersion" in api.solicitud().cuerpoJson())
    }

    // --- eliminar ---

    @Test
    fun eliminar_hace_un_DELETE_sin_cuerpo_y_con_la_baseVersion_en_la_consulta() = runBlocking {
        api.responder(200, """{"version":5}""")

        val resultado = remoto.eliminar("op-3", "e1", baseVersion = 4)

        val peticion = api.solicitud()
        assertEquals("DELETE", peticion.method)
        assertEquals("/api/pruebas/e1", peticion.url.encodedPath)
        assertEquals("4", peticion.url.queryParameter("baseVersion"))
        assertEquals("op-3", peticion.cabecera("Idempotency-Key"))
        assertEquals(0, peticion.bodySize)
        assertEquals(ResultadoEnvio.Confirmada(5), resultado)
    }

    @Test
    fun eliminar_sin_baseVersion_no_lleva_consulta() = runBlocking {
        api.responder(204)

        val resultado = remoto.eliminar("op-3", "e1", baseVersion = null)

        assertNull(api.solicitud().url.query)
        assertEquals("un 204 sin cuerpo también confirma", ResultadoEnvio.Confirmada(null), resultado)
    }

    // --- listar ---

    @Test
    fun listar_hace_un_GET_y_lee_los_registros_con_o_sin_version() = runBlocking {
        api.responder(
            200,
            """[{"entityId":"a","titulo":"A","texto":"x","numero":1,"createdAt":1,"version":2},
                {"entityId":"b","titulo":"B","texto":"y","numero":2,"createdAt":2,"campoNuevo":true}]"""
        )

        val resultado = remoto.listar() as ResultadoPull.Datos<PruebaRemota>

        val peticion = api.solicitud()
        assertEquals("GET", peticion.method)
        assertEquals("/api/pruebas", peticion.url.encodedPath)
        assertEquals("Bearer tok-123", peticion.cabecera("Authorization"))
        assertEquals(listOf("a", "b"), resultado.registros.map { it.entityId })
        assertEquals(2, resultado.registros[0].version)
        assertNull(resultado.registros[1].version)
    }

    @Test
    fun una_lista_vacia_es_una_descarga_valida() = runBlocking {
        api.responder(200, "[]")

        assertEquals(ResultadoPull.Datos(emptyList<PruebaRemota>()), remoto.listar())
    }

    // --- Sesión y reintentos ---

    @Test
    fun sin_sesion_la_peticion_sale_sin_autorizacion_y_el_401_pausa_la_cola() = runBlocking {
        api.token = null
        api.responder(401)

        val resultado = remoto.crear("op-1", prueba)

        assertNull(api.solicitud().cabecera("Authorization"))
        assertEquals(ResultadoEnvio.SesionExpirada, resultado)
    }

    @Test
    fun un_reintento_lleva_la_misma_clave_de_idempotencia_y_el_mismo_cuerpo() = runBlocking {
        api.responder(503)
        api.responder(201, """{"version":1}""")

        remoto.crear("op-9", prueba)
        remoto.crear("op-9", prueba)

        val primera = api.solicitud()
        val segunda = api.solicitud()
        assertEquals(primera.cabecera("Idempotency-Key"), segunda.cabecera("Idempotency-Key"))
        assertEquals(primera.cuerpoJson(), segunda.cuerpoJson())
    }

    // --- Respuestas del servidor ---

    @Test
    fun el_409_trae_el_registro_vigente_y_es_un_conflicto_por_version() = runBlocking {
        api.responder(409, """{"error":"version","actual":{"entityId":"e1","titulo":"Del servidor","texto":"t","numero":1,"createdAt":1,"version":8}}""")

        val resultado = remoto.actualizar("op", prueba, 3) as ResultadoEnvio.Conflicto

        assertEquals(CausaConflicto.VERSION_DISTINTA, resultado.causa)
        assertTrue(resultado.actual!!.contains("Del servidor"))
    }

    @Test
    fun cada_codigo_del_servidor_se_traduce_por_el_cable_igual_que_en_la_tabla_de_errores() = runBlocking {
        val esperados = mapOf(
            404 to ResultadoEnvio.Conflicto("El registro ya no existe en el servidor", CausaConflicto.INEXISTENTE_EN_SERVIDOR),
            410 to ResultadoEnvio.Conflicto("El registro ya no existe en el servidor", CausaConflicto.INEXISTENTE_EN_SERVIDOR),
            401 to ResultadoEnvio.SesionExpirada,
            403 to ResultadoEnvio.SesionExpirada
        )
        for ((codigo, esperado) in esperados) {
            api.responder(codigo)
            assertEquals("HTTP $codigo", esperado, remoto.actualizar("op", prueba, 1))
            api.solicitud()
        }
        for (codigo in listOf(413, 422)) {
            api.responder(codigo)
            assertTrue("HTTP $codigo", remoto.actualizar("op", prueba, 1) is ResultadoEnvio.Definitivo)
            api.solicitud()
        }
        for (codigo in listOf(500, 502, 503, 408, 429)) {
            api.responder(codigo)
            assertTrue("HTTP $codigo", remoto.actualizar("op", prueba, 1) is ResultadoEnvio.Temporal)
            api.solicitud()
        }
    }

    @Test
    fun una_respuesta_2xx_ilegible_es_un_fallo_definitivo_y_no_una_confirmacion() = runBlocking {
        api.responder(200, "esto no es json")

        assertTrue(remoto.crear("op", prueba) is ResultadoEnvio.Definitivo)
    }

    @Test
    fun con_el_servidor_caido_el_resultado_es_incierto_y_no_cuenta_como_intento() = runBlocking {
        api.servidor.close()

        assertEquals(ResultadoEnvio.Incierto, remoto.crear("op", prueba))
    }

    @Test
    fun una_lista_con_error_o_ilegible_no_es_una_lista_vacia() = runBlocking {
        api.responder(500)
        assertTrue(remoto.listar() is ResultadoPull.Fallo)

        api.responder(200, """{"no":"es una lista"}""")
        assertTrue(remoto.listar() is ResultadoPull.Fallo)

        api.responder(401)
        assertEquals(ResultadoPull.SesionExpirada, remoto.listar())
    }
}
