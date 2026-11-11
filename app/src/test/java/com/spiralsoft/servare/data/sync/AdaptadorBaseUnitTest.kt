package com.spiralsoft.servare.data.sync

import com.spiralsoft.servare.data.local.entity.EliminacionEntity
import com.spiralsoft.servare.data.local.entity.PruebaEntity
import com.spiralsoft.servare.data.remote.PruebaRemota
import com.spiralsoft.servare.data.remote.PruebaRemoteDataSource
import com.spiralsoft.servare.data.remote.ResultadoEnvio
import com.spiralsoft.servare.data.remote.ResultadoPull
import com.spiralsoft.servare.domain.model.CampoComparado
import com.spiralsoft.servare.domain.model.EstadoOperacion.BLOQUEADA
import com.spiralsoft.servare.domain.model.EstadoOperacion.COMPLETADA
import com.spiralsoft.servare.domain.model.EstadoOperacion.CONFLICTO
import com.spiralsoft.servare.domain.model.EstadoOperacion.DESCARTADA
import com.spiralsoft.servare.domain.model.EstadoOperacion.FALLIDA
import com.spiralsoft.servare.domain.model.EstadoOperacion.PENDIENTE
import com.spiralsoft.servare.domain.model.TipoEntidad
import com.spiralsoft.servare.domain.model.TipoOperacion
import com.spiralsoft.servare.testutil.BaseDeDatosRegla
import com.spiralsoft.servare.testutil.RelojFalso
import com.spiralsoft.servare.testutil.operacion
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// La lógica común a todos los tipos (AdaptadorBase) se prueba a través del molde, AdaptadorPrueba,
// con Room real y el recurso remoto doblado
@RunWith(RobolectricTestRunner::class)
class AdaptadorBaseUnitTest {
    @get:Rule val bd = BaseDeDatosRegla()

    private val remoto = mockk<PruebaRemoteDataSource>()
    private val reloj = RelojFalso()
    private val adaptador by lazy {
        AdaptadorPrueba(bd.pruebas, remoto, Json, bd.db, bd.operaciones, bd.eliminaciones, bd.syncInicial, reloj)
    }

    private suspend fun local(id: String, version: Int? = 1, titulo: String = "Título $id") =
        bd.pruebas.insert(PruebaEntity(id, titulo, "texto", 1, createdAt = 1, version = version))

    private fun remota(id: String, version: Int? = 2, titulo: String = "Remoto $id") =
        PruebaRemota(id, titulo, "texto remoto", 9, createdAt = 5, version = version)

    private fun servidor(vararg registros: PruebaRemota) {
        coEvery { remoto.listar() } returns ResultadoPull.Datos(registros.toList())
    }

    // --- enviar ---

    @Test
    fun crear_envia_el_registro_tal_como_esta_ahora_con_el_mismo_operationId() = runBlocking {
        local("e1", version = null, titulo = "Versión más reciente")
        coEvery { remoto.crear(any(), any()) } returns ResultadoEnvio.Confirmada(1)

        val resultado = adaptador.enviar(operacion("op1", 1, tipo = TipoOperacion.CREAR, entidad = "e1"))

        assertEquals(ResultadoEnvio.Confirmada(1), resultado)
        coVerify { remoto.crear("op1", match { (it.entityId == "e1") && (it.titulo == "Versión más reciente") }) }
    }

    @Test
    fun actualizar_envia_el_registro_actual_con_la_baseVersion_de_la_operacion() = runBlocking {
        local("e1", titulo = "Editado")
        coEvery { remoto.actualizar(any(), any(), any()) } returns ResultadoEnvio.Confirmada(4)

        adaptador.enviar(operacion("op1", 1, entidad = "e1", baseVersion = 3))

        coVerify { remoto.actualizar("op1", match { it.titulo == "Editado" }, 3) }
    }

    @Test
    fun eliminar_no_necesita_leer_el_registro_y_lleva_la_baseVersion() = runBlocking {
        coEvery { remoto.eliminar(any(), any(), any()) } returns ResultadoEnvio.Confirmada(null)

        adaptador.enviar(operacion("op1", 1, tipo = TipoOperacion.ELIMINAR, entidad = "e1", baseVersion = 2))

        coVerify { remoto.eliminar("op1", "e1", 2) }
    }

    @Test
    fun un_crear_pendiente_se_envia_aunque_el_registro_se_haya_marcado_como_eliminado_despues() = runBlocking {
        local("e1", version = null)
        bd.eliminaciones.insert(EliminacionEntity(TipoEntidad.PRUEBA, "e1", eliminadaEn = 2))
        coEvery { remoto.crear(any(), any()) } returns ResultadoEnvio.Confirmada(1)

        adaptador.enviar(operacion("op1", 1, tipo = TipoOperacion.CREAR, entidad = "e1"))

        coVerify { remoto.crear("op1", any()) }
    }

    @Test
    fun un_registro_ausente_es_un_fallo_definitivo_y_no_llama_al_servidor() = runBlocking {
        for (tipo in listOf(TipoOperacion.CREAR, TipoOperacion.ACTUALIZAR)) {
            val resultado = adaptador.enviar(operacion("op", 1, tipo = tipo, entidad = "fantasma"))
            assertEquals(ResultadoEnvio.Definitivo("El registro ya no está en el dispositivo"), resultado)
        }
        coVerify(exactly = 0) { remoto.crear(any(), any()) }
        coVerify(exactly = 0) { remoto.actualizar(any(), any(), any()) }
    }

    @Test
    fun los_adjuntos_aun_no_se_envian() = runBlocking {
        local("e1")
        val resultado = adaptador.enviar(operacion("op", 1, tipo = TipoOperacion.ADJUNTAR, entidad = "e1"))
        assertEquals(ResultadoEnvio.Definitivo("Los adjuntos aún no se pueden enviar"), resultado)
    }

    @Test
    fun el_resultado_del_servidor_pasa_tal_cual() = runBlocking {
        local("e1")
        val resultados = listOf(
            ResultadoEnvio.Incierto,
            ResultadoEnvio.SesionExpirada,
            ResultadoEnvio.Temporal("503", sinRespuesta = true),
            ResultadoEnvio.Definitivo("422")
        )
        for (esperado in resultados) {
            coEvery { remoto.actualizar(any(), any(), any()) } returns esperado
            assertEquals(esperado, adaptador.enviar(operacion("op", 1, entidad = "e1")))
        }
    }

    // --- descargar: protección del pull ---

    @Test
    fun el_pull_crea_los_registros_nuevos_y_actualiza_los_que_cambiaron() = runBlocking {
        local("existente", version = 1)
        servidor(remota("existente", version = 2, titulo = "Cambiado"), remota("nuevo"))

        val descarga = adaptador.descargar()

        assertTrue(descarga.pull is ResultadoPull.Datos<*>)
        assertEquals("Cambiado", bd.pruebas.findById("existente")!!.titulo)
        assertEquals(2, bd.pruebas.findById("existente")!!.version)
        assertEquals("Remoto nuevo", bd.pruebas.findById("nuevo")!!.titulo)
    }

    @Test
    fun un_registro_con_operacion_sin_terminar_no_se_sobrescribe_en_ningun_estado() = runBlocking {
        val estados = listOf(PENDIENTE, FALLIDA, CONFLICTO, BLOQUEADA)
        estados.forEachIndexed { i, estado ->
            local("e$i", titulo = "Mío $i")
            bd.operaciones.insert(operacion("op$i", i.toLong(), estado = estado, entidad = "e$i"))
        }
        servidor(*estados.indices.map { remota("e$it") }.toTypedArray())

        adaptador.descargar()

        estados.indices.forEach {
            assertEquals("estado ${estados[it]}", "Mío $it", bd.pruebas.findById("e$it")!!.titulo)
        }
    }

    @Test
    fun un_registro_con_operaciones_terminadas_si_se_sobrescribe() = runBlocking {
        local("e1")
        local("e2")
        bd.operaciones.insert(operacion("a", 1, estado = COMPLETADA, entidad = "e1"))
        bd.operaciones.insert(operacion("b", 1, estado = DESCARTADA, entidad = "e2"))
        servidor(remota("e1"), remota("e2"))

        adaptador.descargar()

        assertEquals("Remoto e1", bd.pruebas.findById("e1")!!.titulo)
        assertEquals("Remoto e2", bd.pruebas.findById("e2")!!.titulo)
    }

    @Test
    fun la_operacion_de_otro_tipo_de_entidad_con_el_mismo_id_no_protege() = runBlocking {
        local("e1")
        bd.operaciones.insert(operacion("a", 1, entidad = "e1", tipoEntidad = TipoEntidad.FICHA))
        servidor(remota("e1"))

        adaptador.descargar()

        assertEquals("Remoto e1", bd.pruebas.findById("e1")!!.titulo)
    }

    @Test
    fun el_pull_no_resucita_un_registro_con_el_borrado_pendiente() = runBlocking {
        local("e1", titulo = "Borrado por mí")
        bd.eliminaciones.insert(EliminacionEntity(TipoEntidad.PRUEBA, "e1", eliminadaEn = 2))
        servidor(remota("e1"))

        adaptador.descargar()

        assertEquals("Borrado por mí", bd.pruebas.findById("e1")!!.titulo)
    }

    @Test
    fun sin_version_en_el_servidor_se_conserva_la_que_ya_se_conocia() = runBlocking {
        local("e1", version = 5)
        servidor(remota("e1", version = null))

        adaptador.descargar()

        assertEquals(5, bd.pruebas.findById("e1")!!.version)
    }

    // --- descargar: lo que el servidor ya no tiene ---

    @Test
    fun lo_que_el_servidor_conocio_y_ya_no_envia_se_quita_y_se_devuelve_su_titulo() = runBlocking {
        local("borrado", version = 3, titulo = "Se fue")
        local("sigue", version = 1)
        servidor(remota("sigue"))

        val descarga = adaptador.descargar()

        assertNull(bd.pruebas.findById("borrado"))
        assertNotNull(bd.pruebas.findById("sigue"))
        assertEquals(listOf("Se fue"), descarga.eliminadas)
    }

    @Test
    fun lo_que_el_servidor_nunca_confirmo_no_se_quita() = runBlocking {
        local("nuevo", version = null)
        servidor()

        val descarga = adaptador.descargar()

        assertNotNull(bd.pruebas.findById("nuevo"))
        assertTrue(descarga.eliminadas.isEmpty())
    }

    @Test
    fun lo_que_tiene_operaciones_sin_terminar_no_se_quita_aunque_el_servidor_no_lo_envie() = runBlocking {
        local("e1", version = 3)
        bd.operaciones.insert(operacion("a", 1, estado = CONFLICTO, entidad = "e1"))
        servidor()

        val descarga = adaptador.descargar()

        assertNotNull(bd.pruebas.findById("e1"))
        assertTrue(descarga.eliminadas.isEmpty())
    }

    @Test
    fun lo_que_se_borro_en_el_dispositivo_y_espera_confirmacion_no_se_cuenta_como_borrado_del_servidor() = runBlocking {
        local("e1", version = 3)
        bd.eliminaciones.insert(EliminacionEntity(TipoEntidad.PRUEBA, "e1", eliminadaEn = 2))
        servidor()

        val descarga = adaptador.descargar()

        assertNotNull(bd.pruebas.findById("e1"))
        assertTrue(descarga.eliminadas.isEmpty())
    }

    // --- descargar: descarga inicial y errores ---

    @Test
    fun una_lista_vacia_tambien_completa_la_descarga_inicial() = runBlocking {
        servidor()
        assertFalse(bd.syncInicial.estaCompletada(TipoEntidad.PRUEBA))

        adaptador.descargar()

        assertTrue(bd.syncInicial.estaCompletada(TipoEntidad.PRUEBA))
        assertFalse("solo el tipo descargado", bd.syncInicial.estaCompletada(TipoEntidad.FICHA))
    }

    @Test
    fun un_pull_con_error_no_toca_nada_ni_marca_la_descarga_inicial() = runBlocking {
        local("e1", version = 3)
        for (fallo in listOf(
            ResultadoPull.Fallo("500"), ResultadoPull.SinServidor, ResultadoPull.SesionExpirada
        )) {
            coEvery { remoto.listar() } returns fallo

            val descarga = adaptador.descargar()

            assertEquals(fallo, descarga.pull)
            assertTrue(descarga.eliminadas.isEmpty())
            assertNotNull("un error del pull no es 'el servidor no tiene nada'", bd.pruebas.findById("e1"))
            assertFalse(bd.syncInicial.estaCompletada(TipoEntidad.PRUEBA))
        }
    }

    // --- Lectura del 409 ---

    @Test
    fun el_registro_vigente_del_409_se_lee_y_se_compara_con_el_local() = runBlocking {
        local("e1", titulo = "Mío")
        val json = """{"entityId":"e1","titulo":"Del servidor","texto":"texto","numero":9,"createdAt":5,"version":6}"""

        val servidor = adaptador.leerServidor("e1", json)!!

        assertEquals(6, servidor.version)
        assertEquals(
            listOf(
                CampoComparado("Título", "Mío", "Del servidor"),
                CampoComparado("Texto", "texto", "texto"),
                CampoComparado("Número", "1", "9")
            ),
            servidor.comparacion()
        )
    }

    @Test
    fun aceptar_el_registro_del_servidor_reemplaza_el_local_con_su_version() = runBlocking {
        local("e1", titulo = "Mío", version = 1)
        val json = """{"entityId":"e1","titulo":"Del servidor","texto":"t","numero":9,"createdAt":5,"version":6}"""

        adaptador.leerServidor("e1", json)!!.aplicar()

        val guardado = bd.pruebas.findById("e1")!!
        assertEquals("Del servidor", guardado.titulo)
        assertEquals(6, guardado.version)
    }

    @Test
    fun si_el_registro_local_ya_no_existe_la_comparacion_usa_valores_vacios() = runBlocking {
        val json = """{"entityId":"e1","titulo":"Del servidor","texto":"t","numero":9,"createdAt":5,"version":6}"""

        val campos = adaptador.leerServidor("e1", json)!!.comparacion()

        assertEquals(CampoComparado("Título", null, "Del servidor"), campos.first())
    }

    @Test
    fun un_json_roto_o_vacio_del_409_no_se_puede_leer() {
        for (roto in listOf("", "no es json", "{", """{"titulo":"falta el resto"}""", "[]")) {
            assertNull("«$roto»", adaptador.leerServidor("e1", roto))
        }
    }

    // --- Operaciones sobre el registro ---

    @Test
    fun titulo_existe_borrar_y_versiones() = runBlocking {
        local("e1", titulo = "Hola", version = 1)

        assertEquals("Hola", adaptador.titulo("e1"))
        assertNull(adaptador.titulo("otro"))
        assertTrue(adaptador.existe("e1"))
        assertFalse(adaptador.existe("otro"))

        adaptador.actualizarVersion("e1", 8)
        assertEquals(8, bd.pruebas.findById("e1")!!.version)
        adaptador.limpiarVersion("e1")
        assertNull(bd.pruebas.findById("e1")!!.version)

        adaptador.borrar("e1")
        assertFalse(adaptador.existe("e1"))
    }
}
