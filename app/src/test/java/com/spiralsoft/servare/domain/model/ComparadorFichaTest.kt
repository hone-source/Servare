package com.spiralsoft.servare.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ComparadorFichaTest {

    private val sinDiferencias = listOf(CampoComparado("Contenido de la ficha", "sin diferencias", "sin diferencias"))

    @Test
    fun fichas_iguales_dicen_que_solo_cambio_la_version() {
        val ficha = Ficha(nombre = "Cerro", comunidad = "Tuapurie")
        assertEquals(sinDiferencias, compararFichas(ficha, ficha.copy()))
    }

    @Test
    fun solo_se_listan_los_campos_distintos() {
        val local = Ficha(nombre = "Cerro", comunidad = "Tuapurie", estado = "Jalisco")
        val servidor = local.copy(nombre = "Cerro Grande")

        assertEquals(
            listOf(CampoComparado("Nombre del lugar", "Cerro", "Cerro Grande")),
            compararFichas(local, servidor)
        )
    }

    @Test
    fun los_campos_salen_en_el_orden_del_formulario() {
        val local = Ficha(riesgos = "a", nombre = "a", comunidad = "a")
        val servidor = Ficha(riesgos = "b", nombre = "b", comunidad = "b")

        assertEquals(
            listOf("Comunidad", "Nombre del lugar", "Riesgos"),
            compararFichas(local, servidor).map { it.nombre }
        )
    }

    @Test
    fun sin_ficha_local_se_compara_contra_una_vacia() {
        val servidor = Ficha(nombre = "Cerro")

        assertEquals(
            listOf(CampoComparado("Nombre del lugar", "", "Cerro")),
            compararFichas(null, servidor)
        )
    }

    @Test
    fun sin_ficha_local_y_servidor_vacio_no_hay_diferencias() {
        assertEquals(sinDiferencias, compararFichas(null, Ficha()))
    }

    @Test
    fun las_opciones_se_muestran_con_su_etiqueta() {
        val local = Ficha(pueblo = PuebloOriginario.WIXARIKA, tipoEspacio = TipoEspacio.TUKIPA)
        val servidor = Ficha(pueblo = PuebloOriginario.ODAM_AUDAM, geometria = Geometria.LINEA)

        assertEquals(
            listOf(
                CampoComparado("Pueblo originario", "Wixárika", "O’dam – Au’dam"),
                CampoComparado("Tipo de espacio sagrado", "Tukipa", null),
                CampoComparado("Punto, línea, polígono", null, "Línea")
            ),
            compararFichas(local, servidor)
        )
    }

    @Test
    fun las_tablas_se_muestran_una_linea_por_fila_con_las_celdas_llenas() {
        val local = Ficha(
            poligonal = listOf(PuntoPoligonal("P1", "13Q 2", "", "2100"), PuntoPoligonal()),
            participantes = listOf(Participante("Comisario", "Ana"))
        )

        val campos = compararFichas(local, Ficha()).associateBy { it.nombre }

        assertEquals("P1 · 13Q 2 · 2100\n", campos.getValue("Poligonal").local)
        assertEquals("Comisario · Ana", campos.getValue("Participantes").local)
        assertEquals("", campos.getValue("Poligonal").servidor)
    }

    @Test
    fun los_rituales_incluyen_la_etiqueta_del_tipo() {
        val local = Ficha(rituales = listOf(Ritual(TipoRitual.PEREGRINACION, "", "Hikuri", "Anual", "")))

        val campo = compararFichas(local, Ficha()).single()

        assertEquals("Rituales y actos ceremoniales", campo.nombre)
        assertEquals("Peregrinación · Hikuri · Anual", campo.local)
    }

    @Test
    fun las_medidas_de_un_elemento_muestran_guion_en_las_vacias() {
        val local = Ficha(elementos = listOf(ElementoEspacio(forma = "redonda", largo = "1", alto = "3")))

        val campo = compararFichas(local, Ficha()).single()

        assertEquals("redonda · L×AN×AL×P: 1×—×3×—", campo.local)
    }

    @Test
    fun un_elemento_sin_medidas_no_agrega_la_linea_de_medidas() {
        val local = Ficha(elementos = listOf(ElementoEspacio(tipo = TipoLugar.NATURAL, forma = "redonda")))

        assertEquals("Natural · redonda", compararFichas(local, Ficha()).single().local)
    }

    @Test
    fun los_registros_de_archivos_rellenan_con_guion_lo_que_falta() {
        val local = Ficha(
            fotografias = RegistroArchivos(cantidad = "10", desde = "", hasta = "IMG_10"),
            grabaciones = RegistroArchivos()
        )

        val campos = compararFichas(local, Ficha()).associateBy { it.nombre }

        assertEquals("10, del — al IMG_10", campos.getValue("Fotografías realizadas").local)
        assertEquals("", campos.getValue("Fotografías realizadas").servidor)
        // Vacíos en ambos lados: no es una diferencia
        assertEquals(null, campos["Grabaciones realizadas"])
    }
}
