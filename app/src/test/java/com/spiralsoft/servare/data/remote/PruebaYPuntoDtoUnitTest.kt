package com.spiralsoft.servare.data.remote

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Lo que viaja por la red: Json igual al de AppModule
class PruebaYPuntoDtoUnitTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun la_prueba_remota_se_lee_con_version_sin_ella_y_con_campos_de_mas() {
        val completa = json.decodeFromString<PruebaRemota>(
            """{"entityId":"e","titulo":"T","texto":"x","numero":3,"createdAt":9,"version":4,"extra":true}"""
        )
        assertEquals(PruebaRemota("e", "T", "x", 3, 9, 4), completa)

        val sinVersion = json.decodeFromString<PruebaRemota>("""{"entityId":"e","titulo":"T","texto":"","numero":0,"createdAt":1}""")
        assertNull(sinVersion.version)
    }

    @Test
    fun una_prueba_remota_incompleta_o_mal_tipada_es_un_error_de_serializacion() {
        for (roto in listOf("""{"entityId":"e"}""", """{"entityId":"e","titulo":"T","texto":"x","numero":"no","createdAt":1}""")) {
            try {
                json.decodeFromString<PruebaRemota>(roto)
                throw AssertionError("debió fallar: $roto")
            } catch (_: SerializationException) {
            }
        }
    }

    @Test
    fun la_creacion_de_una_prueba_no_envia_baseVersion_y_la_actualizacion_si() {
        val crear = json.encodeToJsonElement(PruebaRequest.serializer(), PruebaRequest("e", "T", "x", 1, 2)).jsonObject
        val actualizar = json.encodeToJsonElement(PruebaRequest.serializer(), PruebaRequest("e", "T", "x", 1, 2, baseVersion = 5)).jsonObject

        assertFalse("baseVersion" in crear)
        assertEquals("5", actualizar["baseVersion"].toString())
        assertEquals(setOf("entityId", "titulo", "texto", "numero", "createdAt"), crear.keys)
    }

    @Test
    fun el_punto_remoto_conserva_las_coordenadas_con_decimales() {
        val punto = json.decodeFromString<PuntoRemoto>(
            """{"entityId":"p","nombre":"Mirador","latitud":22.770123,"longitud":-102.573456,"createdAt":1,"version":2}"""
        )

        assertEquals(PuntoRemoto("p", "Mirador", 22.770123, -102.573456, 1, 2), punto)
        val ida = json.decodeFromString<PuntoRemoto>(json.encodeToString(PuntoRemoto.serializer(), punto))
        assertEquals(punto, ida)
    }

    @Test
    fun el_punto_remoto_acepta_coordenadas_enteras_y_rechaza_texto() {
        val entero = json.decodeFromString<PuntoRemoto>("""{"entityId":"p","nombre":"N","latitud":22,"longitud":-102,"createdAt":1}""")
        assertEquals(22.0, entero.latitud, 0.0)
        try {
            json.decodeFromString<PuntoRemoto>("""{"entityId":"p","nombre":"N","latitud":"norte","longitud":0,"createdAt":1}""")
            throw AssertionError("debió fallar")
        } catch (_: SerializationException) {
        }
    }

    @Test
    fun la_solicitud_de_un_punto_lleva_todos_sus_campos_y_baseVersion_solo_al_actualizar() {
        val crear: JsonObject = json.encodeToJsonElement(PuntoRequest.serializer(), PuntoRequest("p", "N", 1.5, -2.5, 3)).jsonObject
        val actualizar = json.encodeToJsonElement(PuntoRequest.serializer(), PuntoRequest("p", "N", 1.5, -2.5, 3, baseVersion = 7)).jsonObject

        assertEquals(setOf("entityId", "nombre", "latitud", "longitud", "createdAt"), crear.keys)
        assertTrue("baseVersion" in actualizar)
        assertEquals("1.5", crear["latitud"].toString())
    }
}
