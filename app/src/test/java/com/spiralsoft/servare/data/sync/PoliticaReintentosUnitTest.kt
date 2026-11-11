package com.spiralsoft.servare.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PoliticaReintentosUnitTest {

    private val segundo = 1_000L
    private val cincoHoras = 5 * 60 * 60 * segundo

    @Test
    fun el_maximo_son_trece_intentos() {
        assertEquals(13, PoliticaReintentos.MAX_INTENTOS)
    }

    @Test
    fun la_espera_se_duplica_desde_diez_segundos() {
        val esperado = listOf(10, 20, 40, 80, 160).map { it * segundo }

        assertEquals(esperado, (1..5).map(PoliticaReintentos::espera))
    }

    @Test
    fun la_espera_nunca_pasa_de_cinco_horas() {
        // 10 s × 2^10 = 10 240 s todavía cabe
        assertEquals(10_240 * segundo, PoliticaReintentos.espera(11))
        assertEquals(cincoHoras, PoliticaReintentos.espera(12))
        assertEquals(cincoHoras, PoliticaReintentos.espera(13))
    }

    @Test
    fun con_muchos_intentos_no_desborda() {
        for (intento in listOf(21, 22, 64, 65, 1_000, Int.MAX_VALUE)) {
            assertEquals("intento $intento", cincoHoras, PoliticaReintentos.espera(intento))
        }
    }

    @Test
    fun la_espera_nunca_decrece() {
        val esperas = (1..100).map(PoliticaReintentos::espera)

        assertTrue(esperas.zipWithNext().all { (a, b) -> a <= b })
    }
}
