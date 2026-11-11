package com.spiralsoft.servare.data.sync

import com.spiralsoft.servare.data.local.dao.aCompleta
import com.spiralsoft.servare.data.local.dao.aFicha
import com.spiralsoft.servare.data.remote.FichaRemota
import com.spiralsoft.servare.data.remote.FichaRemoteDataSource
import com.spiralsoft.servare.data.remote.ResultadoEnvio
import com.spiralsoft.servare.data.remote.ResultadoPull
import com.spiralsoft.servare.data.remote.aDto
import com.spiralsoft.servare.domain.model.EstadoOperacion
import com.spiralsoft.servare.domain.model.Ficha
import com.spiralsoft.servare.domain.model.TipoEntidad
import com.spiralsoft.servare.domain.model.TipoOperacion
import com.spiralsoft.servare.testutil.BaseDeDatosRegla
import com.spiralsoft.servare.testutil.RelojFalso
import com.spiralsoft.servare.testutil.fichaLlena
import com.spiralsoft.servare.testutil.operacion
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// El caso con tablas hijas del molde: el pull y el 409 no pueden dejar filas viejas ni perder el orden
@RunWith(RobolectricTestRunner::class)
class AdaptadorFichaUnitTest {
    @get:Rule val bd = BaseDeDatosRegla()

    private val json = Json { ignoreUnknownKeys = true }
    private val remoto = mockk<FichaRemoteDataSource>()
    private val adaptador by lazy {
        AdaptadorFicha(bd.db.fichaDao(), remoto, json, bd.db, bd.operaciones, bd.eliminaciones, bd.syncInicial, RelojFalso())
    }
    private val dao get() = bd.db.fichaDao()

    private suspend fun local(id: String, ficha: Ficha = fichaLlena("Mía"), version: Int? = 1) =
        dao.insert(ficha.aCompleta(id, 1, version))

    private fun remota(id: String, ficha: Ficha = fichaLlena("Del servidor"), version: Int? = 6) =
        FichaRemota(id, ficha.aDto(), createdAt = 5, version = version)

    private fun servidor(vararg registros: FichaRemota) {
        coEvery { remoto.listar() } returns ResultadoPull.Datos(registros.toList())
    }

    // --- Envío ---

    @Test
    fun enviar_manda_la_ficha_completa_con_sus_tablas() = runBlocking {
        local("f1")
        coEvery { remoto.actualizar(any(), any(), any()) } returns ResultadoEnvio.Confirmada(2)
        coEvery { remoto.crear(any(), any()) } returns ResultadoEnvio.Confirmada(1)
        coEvery { remoto.eliminar(any(), any(), any()) } returns ResultadoEnvio.Confirmada(null)

        adaptador.enviar(operacion("a", 1, tipo = TipoOperacion.CREAR, entidad = "f1", tipoEntidad = TipoEntidad.FICHA))
        adaptador.enviar(operacion("b", 2, entidad = "f1", tipoEntidad = TipoEntidad.FICHA, baseVersion = 1))
        adaptador.enviar(operacion("c", 3, tipo = TipoOperacion.ELIMINAR, entidad = "f1", tipoEntidad = TipoEntidad.FICHA, baseVersion = 2))

        coVerify { remoto.crear("a", match { it.rituales.size == 3 && it.participantes.size == 2 }) }
        coVerify { remoto.actualizar("b", match { it.ficha.nombre == "Mía" }, 1) }
        coVerify { remoto.eliminar("c", "f1", 2) }
    }

    @Test
    fun una_ficha_ausente_es_un_fallo_definitivo() = runBlocking {
        val resultado = adaptador.enviar(operacion("a", 1, entidad = "nada", tipoEntidad = TipoEntidad.FICHA))
        assertEquals(ResultadoEnvio.Definitivo("El registro ya no está en el dispositivo"), resultado)
    }

    // --- Pull ---

    @Test
    fun el_pull_crea_la_ficha_con_todas_sus_tablas_y_el_orden_del_servidor() = runBlocking {
        servidor(remota("f1"))

        adaptador.descargar()

        val guardada = dao.findCompletaById("f1")!!
        assertEquals(fichaLlena("Del servidor"), guardada.aFicha())
        assertEquals(6, guardada.ficha.version)
        assertEquals(5L, guardada.ficha.createdAt)
    }

    @Test
    fun el_pull_reemplaza_las_tablas_sin_dejar_filas_de_la_version_anterior() = runBlocking {
        local("f1")
        servidor(remota("f1", fichaLlena("Nueva").copy(rituales = emptyList(), participantes = emptyList())))

        adaptador.descargar()

        val ficha = dao.findCompletaById("f1")!!
        assertTrue(ficha.rituales.isEmpty())
        assertTrue(ficha.participantes.isEmpty())
        assertEquals("Nueva", ficha.ficha.nombre)
    }

    @Test
    fun sin_version_del_servidor_se_conserva_la_conocida() = runBlocking {
        local("f1", version = 5)
        servidor(remota("f1", version = null))

        adaptador.descargar()

        assertEquals(5, dao.findById("f1")!!.version)
    }

    @Test
    fun el_pull_no_pisa_una_ficha_con_operaciones_sin_terminar() = runBlocking {
        local("f1")
        bd.operaciones.insert(operacion("op", 1, estado = EstadoOperacion.CONFLICTO, entidad = "f1", tipoEntidad = TipoEntidad.FICHA))
        servidor(remota("f1"))

        adaptador.descargar()

        assertEquals("Mía", dao.findById("f1")!!.nombre)
    }

    @Test
    fun una_ficha_confirmada_que_el_servidor_ya_no_envia_se_quita_y_si_no_tiene_nombre_se_avisa_asi() = runBlocking {
        local("con_nombre", fichaLlena("Cerro"), version = 2)
        local("sin_nombre", Ficha(), version = 2)
        local("nunca_enviada", fichaLlena("Local"), version = null)
        servidor()

        val descarga = adaptador.descargar()

        assertNull(dao.findById("con_nombre"))
        assertNull(dao.findById("sin_nombre"))
        assertNotNull(dao.findById("nunca_enviada"))
        assertEquals(setOf("Cerro", "Ficha sin nombre"), descarga.eliminadas.toSet())
    }

    // --- Operaciones sobre el registro ---

    @Test
    fun titulo_existe_versiones_y_borrado() = runBlocking {
        local("f1", fichaLlena("Cerro"), version = 1)

        assertEquals("Cerro", adaptador.titulo("f1"))
        assertNull(adaptador.titulo("otra"))
        assertTrue(adaptador.existe("f1"))
        adaptador.actualizarVersion("f1", 9)
        assertEquals(9, dao.findById("f1")!!.version)
        adaptador.limpiarVersion("f1")
        assertNull(dao.findById("f1")!!.version)
        adaptador.borrar("f1")
        assertTrue(!adaptador.existe("f1"))
    }

    // --- Conflicto (409) ---

    private fun snapshot(version: Int? = 6, ficha: Ficha = fichaLlena("Del servidor")) =
        json.encodeToString(FichaRemota.serializer(), remota("f1", ficha, version))

    @Test
    fun el_registro_vigente_del_409_se_compara_campo_a_campo_con_la_ficha_local() = runBlocking {
        local("f1")

        val servidor = adaptador.leerServidor("f1", snapshot())!!
        val campos = servidor.comparacion()

        assertEquals(6, servidor.version)
        assertTrue(campos.any { it.local == "Mía" && it.servidor == "Del servidor" })
    }

    @Test
    fun si_la_ficha_ya_no_esta_en_el_dispositivo_se_compara_contra_una_vacia() = runBlocking {
        val campos = adaptador.leerServidor("f1", snapshot())!!.comparacion()

        assertTrue(campos.any { it.servidor == "Del servidor" })
    }

    @Test
    fun aceptar_el_servidor_reemplaza_la_ficha_y_sus_tablas() = runBlocking {
        local("f1")

        adaptador.leerServidor("f1", snapshot(version = 8, ficha = fichaLlena("Del servidor").copy(rituales = emptyList())))!!.aplicar()

        val guardada = dao.findCompletaById("f1")!!
        assertEquals("Del servidor", guardada.ficha.nombre)
        assertEquals(8, guardada.ficha.version)
        assertTrue(guardada.rituales.isEmpty())
    }

    @Test
    fun un_json_roto_no_se_puede_leer() {
        for (roto in listOf("", "{", "no es json", """{"entityId":"f1"}""", "[]")) {
            assertNull("«$roto»", adaptador.leerServidor("f1", roto))
        }
    }
}
