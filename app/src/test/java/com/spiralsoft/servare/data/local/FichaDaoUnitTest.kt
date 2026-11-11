package com.spiralsoft.servare.data.local

import com.spiralsoft.servare.data.local.dao.aCompleta
import com.spiralsoft.servare.data.local.dao.aFicha
import com.spiralsoft.servare.data.local.entity.EliminacionEntity
import com.spiralsoft.servare.domain.model.EstadoOperacion
import com.spiralsoft.servare.domain.model.Ficha
import com.spiralsoft.servare.domain.model.Ritual
import com.spiralsoft.servare.domain.model.TipoEntidad
import com.spiralsoft.servare.domain.model.TipoEspacio
import com.spiralsoft.servare.domain.model.TipoRitual
import com.spiralsoft.servare.testutil.BaseDeDatosRegla
import com.spiralsoft.servare.testutil.fichaLlena
import com.spiralsoft.servare.testutil.operacion
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// La ficha y sus cuatro tablas hijas: el reemplazo de filas, la cascada y la lista con su marca de pendiente
@RunWith(RobolectricTestRunner::class)
class FichaDaoUnitTest {
    @get:Rule val bd = BaseDeDatosRegla()
    private val dao get() = bd.db.fichaDao()

    private suspend fun guardar(id: String, ficha: Ficha = fichaLlena(), creada: Long = 1, version: Int? = 1) =
        dao.insert(ficha.aCompleta(id, creada, version))

    private fun filas(tabla: String, id: String) =
        bd.db.query("SELECT 1 FROM $tabla WHERE fichaId = ?", arrayOf(id)).use { it.count }

    @Test
    fun la_ficha_con_todas_sus_tablas_sobrevive_al_viaje_por_room() = runBlocking {
        guardar("f1")

        assertEquals(fichaLlena(), dao.findCompletaById("f1")!!.aFicha())
    }

    @Test
    fun el_orden_de_las_listas_se_conserva_aunque_room_no_ordene_las_relaciones() = runBlocking {
        guardar("f1")

        val ficha = dao.findCompletaById("f1")!!.aFicha()

        assertEquals(listOf("Primero", "Segundo", "Tercero"), ficha.rituales.map { it.nombres })
        assertEquals(listOf("Ana", "Luis"), ficha.participantes.map { it.nombre })
        assertEquals(listOf("P1", "P2"), ficha.poligonal.map { it.puntoReferencia })
    }

    @Test
    fun una_ficha_vacia_no_deja_filas_hijas() = runBlocking {
        guardar("f1", Ficha())

        assertEquals(Ficha(), dao.findCompletaById("f1")!!.aFicha())
        listOf("ficha_poligonal", "ficha_rituales", "ficha_elementos", "ficha_participantes").forEach {
            assertEquals(it, 0, filas(it, "f1"))
        }
    }

    @Test
    fun actualizar_reemplaza_las_filas_hijas_sin_dejar_las_viejas() = runBlocking {
        guardar("f1")
        val menos = fichaLlena().copy(
            rituales = listOf(Ritual(TipoRitual.CEREMONIA, "", "Único", "", "")),
            participantes = emptyList()
        )

        dao.update(menos.aCompleta("f1", 1, 1))

        assertEquals(1, filas("ficha_rituales", "f1"))
        assertEquals(0, filas("ficha_participantes", "f1"))
        assertEquals(menos, dao.findCompletaById("f1")!!.aFicha())
    }

    @Test
    fun actualizar_con_mas_filas_las_agrega_en_orden() = runBlocking {
        guardar("f1", Ficha(nombre = "Sola"))
        val mas = fichaLlena()

        dao.update(mas.aCompleta("f1", 1, 1))

        assertEquals(mas, dao.findCompletaById("f1")!!.aFicha())
    }

    @Test
    fun el_upsert_crea_la_ficha_y_reemplaza_las_hijas_si_ya_existia() = runBlocking {
        dao.upsert(fichaLlena("Nueva").aCompleta("f1", 1, 2))
        assertEquals("Nueva", dao.findById("f1")!!.nombre)

        dao.upsert(fichaLlena("Cambiada").copy(rituales = emptyList()).aCompleta("f1", 1, 3))

        assertEquals(0, filas("ficha_rituales", "f1"))
        assertEquals("Cambiada", dao.findById("f1")!!.nombre)
        assertEquals(3, dao.findById("f1")!!.version)
    }

    @Test
    fun borrar_la_ficha_borra_sus_filas_hijas_en_cascada() = runBlocking {
        guardar("f1")
        guardar("f2")

        dao.deleteById("f1")

        assertNull(dao.findById("f1"))
        listOf("ficha_poligonal", "ficha_rituales", "ficha_elementos", "ficha_participantes").forEach {
            assertEquals(it, 0, filas(it, "f1"))
            assertTrue(it, filas(it, "f2") > 0)
        }
    }

    // --- Lecturas y eliminaciones ---

    @Test
    fun las_marcadas_como_eliminadas_se_ocultan_salvo_a_quien_las_necesita_para_enviar() = runBlocking {
        guardar("f1")
        bd.eliminaciones.insert(EliminacionEntity(TipoEntidad.FICHA, "f1", eliminadaEn = 5))

        assertNull(dao.findActivaById("f1"))
        assertNull(dao.findCompletaActivaById("f1"))
        assertNotNull("un CREAR pendiente debe poder enviarse", dao.findById("f1"))
        assertNotNull(dao.findCompletaById("f1"))
    }

    @Test
    fun una_marca_de_otro_tipo_con_el_mismo_id_no_oculta_la_ficha() = runBlocking {
        guardar("f1")
        bd.eliminaciones.insert(EliminacionEntity(TipoEntidad.PRUEBA, "f1", eliminadaEn = 5))

        assertNotNull(dao.findActivaById("f1"))
    }

    @Test
    fun la_lista_va_de_la_mas_nueva_a_la_mas_vieja_con_las_columnas_de_la_ficha() = runBlocking {
        guardar("vieja", fichaLlena("Vieja"), creada = 1)
        guardar("nueva", fichaLlena("Nueva").copy(tipoEspacio = TipoEspacio.CAPILLA), creada = 9)

        val lista = dao.observarResumenes().first()

        assertEquals(listOf("nueva", "vieja"), lista.map { it.entityId })
        assertEquals("Nueva", lista[0].nombre)
        assertEquals(TipoEspacio.CAPILLA, lista[0].tipoEspacio)
        assertEquals("Tuapurie", lista[0].comunidad)
        assertEquals("2026-10-01", lista[0].fechaLevantamiento)
    }

    @Test
    fun la_lista_no_muestra_las_eliminadas() = runBlocking {
        guardar("f1")
        guardar("f2")
        bd.eliminaciones.insert(EliminacionEntity(TipoEntidad.FICHA, "f1", eliminadaEn = 5))

        assertEquals(listOf("f2"), dao.observarResumenes().first().map { it.entityId })
    }

    @Test
    fun pendiente_es_verdadero_solo_con_operaciones_que_el_servidor_aun_no_confirma() = runBlocking {
        val esperados = mapOf(
            EstadoOperacion.PENDIENTE to true,
            EstadoOperacion.PROCESANDO to true,
            EstadoOperacion.REINTENTABLE to true,
            EstadoOperacion.FALLIDA to false,
            EstadoOperacion.CONFLICTO to false,
            EstadoOperacion.BLOQUEADA to false,
            EstadoOperacion.COMPLETADA to false,
            EstadoOperacion.DESCARTADA to false
        )
        esperados.keys.forEachIndexed { i, estado ->
            guardar("f_$estado", creada = i.toLong())
            bd.operaciones.insert(operacion("op_$estado", 1, estado = estado, entidad = "f_$estado", tipoEntidad = TipoEntidad.FICHA))
        }

        val lista = dao.observarResumenes().first().associateBy { it.entityId }

        esperados.forEach { (estado, pendiente) -> assertEquals("$estado", pendiente, lista.getValue("f_$estado").pendiente) }
    }

    @Test
    fun una_operacion_de_otro_tipo_con_el_mismo_id_no_marca_pendiente() = runBlocking {
        guardar("f1")
        bd.operaciones.insert(operacion("op", 1, entidad = "f1", tipoEntidad = TipoEntidad.PRUEBA))

        assertFalse(dao.observarResumenes().first().single().pendiente)
    }

    // --- Versión del servidor ---

    @Test
    fun solo_las_fichas_con_version_son_confirmadas_por_el_servidor() = runBlocking {
        guardar("con", version = 3)
        guardar("sin", version = null)

        assertEquals(listOf("con"), dao.confirmadasPorElServidor().map { it.entityId })
    }

    @Test
    fun actualizar_y_limpiar_la_version_no_tocan_el_contenido() = runBlocking {
        guardar("f1", version = 1)

        dao.actualizarVersion("f1", 8)
        assertEquals(8, dao.findById("f1")!!.version)
        assertEquals(fichaLlena(), dao.findCompletaById("f1")!!.aFicha())

        dao.limpiarVersion("f1")
        assertNull(dao.findById("f1")!!.version)
        assertEquals(fichaLlena(), dao.findCompletaById("f1")!!.aFicha())
    }
}
