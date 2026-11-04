package com.spiralsoft.servare.data.remote

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.spiralsoft.servare.domain.model.ElementoEspacio
import com.spiralsoft.servare.domain.model.Ficha
import com.spiralsoft.servare.domain.model.Geometria
import com.spiralsoft.servare.domain.model.Participante
import com.spiralsoft.servare.domain.model.PuebloOriginario
import com.spiralsoft.servare.domain.model.PuntoPoligonal
import com.spiralsoft.servare.domain.model.RegistroArchivos
import com.spiralsoft.servare.domain.model.Ritual
import com.spiralsoft.servare.domain.model.TipoEspacio
import com.spiralsoft.servare.domain.model.TipoLugar
import com.spiralsoft.servare.domain.model.TipoRitual

class FichaDtoTest {

    // La misma configuración que AppModule
    private val json = Json { ignoreUnknownKeys = true }

    private val completa = Ficha(
        pueblo = PuebloOriginario.WIXARIKA,
        nucleoAgrario = "Ejido",
        comunidad = "Tuapurie",
        numeroLevantamiento = "12",
        fechaLevantamiento = "2026-10-01",
        tipoEspacio = TipoEspacio.CENTRO_CEREMONIAL,
        tipoEspacioOtro = "otro",
        nombre = "Cerro",
        otrosNombres = "Cerro grande",
        comoLlegar = "Por la brecha",
        estado = "Jalisco",
        municipio = "Mezquitic",
        localidad = "San Andrés",
        tipoPropiedad = "Comunal",
        lugaresProximos = "Pueblo cercano",
        geometria = Geometria.POLIGONO,
        puntoGps = "22.1,-104.2",
        altura = "2100",
        area = "30 ha",
        utm = "13Q 1",
        poligonal = listOf(PuntoPoligonal("P1", "13Q 2", "3 m", "2100"), PuntoPoligonal("P2", "13Q 3", "", "")),
        historia = "Historia",
        informantes = "Informante",
        rituales = listOf(Ritual(TipoRitual.OFRENDA, "otro", "Nombres", "Anual", "Descripción")),
        descripcionEspacio = "Descripción",
        elementos = listOf(
            ElementoEspacio(TipoLugar.NATURAL, "redonda", "ancestro", "uso", "carac", "elem", "1", "2", "3", "4")
        ),
        observacionesCartografia = "Obs",
        danos = "Daños",
        riesgos = "Riesgos",
        fechaTecnica = "2026-10-02",
        horaInicio = "09:00",
        horaFin = "11:30",
        fotografias = RegistroArchivos("10", "IMG_1", "IMG_10"),
        grabaciones = RegistroArchivos("2", "A1", "A2"),
        duracionGrabacion = "00:45",
        georreferencias = RegistroArchivos("1", "G1", "G1"),
        integrantesEquipo = "Equipo",
        comentariosFinales = "Fin",
        autoridadLocal = "Autoridad",
        participantes = listOf(Participante("Comisario", "Ana"))
    )

    @Test
    fun la_ficha_completa_sobrevive_al_viaje_de_ida_y_vuelta() {
        assertEquals(completa, completa.aDto().aFicha())
    }

    @Test
    fun la_ficha_vacia_sobrevive_al_viaje_de_ida_y_vuelta() {
        assertEquals(Ficha(), Ficha().aDto().aFicha())
    }

    @Test
    fun la_ficha_completa_sobrevive_al_JSON() {
        val texto = json.encodeToString(completa.aDto())
        assertEquals(completa, json.decodeFromString<FichaDatosDto>(texto).aFicha())
    }

    @Test
    fun los_valores_por_defecto_no_viajan() {
        assertEquals("{}", json.encodeToString(Ficha().aDto()))
    }

    @Test
    fun solo_viajan_los_campos_con_valor() {
        val texto = json.encodeToString(Ficha(nombre = "Cerro", pueblo = PuebloOriginario.NAAYERI).aDto())
        assertEquals("""{"pueblo":"NAAYERI","nombre":"Cerro"}""", texto)
    }

    @Test
    fun la_creacion_no_envia_baseVersion_y_la_actualizacion_si() {
        val creacion = json.encodeToString(FichaRequest(Ficha().aDto(), createdAt = 5L))
        val actualizacion = json.encodeToString(FichaRequest(Ficha().aDto(), createdAt = 5L, baseVersion = 2))

        assertFalse(creacion.contains("baseVersion"))
        assertTrue(actualizacion.contains(""""baseVersion":2"""))
    }

    @Test
    fun la_respuesta_del_servidor_ignora_campos_desconocidos_y_acepta_faltantes() {
        val remota = json.decodeFromString<FichaRemota>(
            """{"entityId":"f1","createdAt":9,"datos":{"nombre":"Cerro","campoNuevo":1},"extra":true}"""
        )

        assertEquals("f1", remota.entityId)
        assertEquals(null, remota.version)
        assertEquals(Ficha(nombre = "Cerro"), remota.datos.aFicha())
    }
}
