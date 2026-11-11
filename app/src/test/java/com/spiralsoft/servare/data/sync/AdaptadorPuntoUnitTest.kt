package com.spiralsoft.servare.data.sync

import com.spiralsoft.servare.data.local.entity.PuntoEntity
import com.spiralsoft.servare.data.remote.PuntoRemoteDataSource
import com.spiralsoft.servare.data.remote.PuntoRemoto
import com.spiralsoft.servare.data.remote.ResultadoEnvio
import com.spiralsoft.servare.data.remote.ResultadoPull
import com.spiralsoft.servare.domain.model.CampoComparado
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// El segundo adaptador sobre AdaptadorBase: comprueba que lo común funciona con otra entidad (coordenadas, no texto)
@RunWith(RobolectricTestRunner::class)
class AdaptadorPuntoUnitTest {
    @get:Rule val bd = BaseDeDatosRegla()

    private val remoto = mockk<PuntoRemoteDataSource>()
    private val adaptador by lazy {
        AdaptadorPunto(bd.db.puntoDao(), remoto, Json { ignoreUnknownKeys = true }, bd.db, bd.operaciones, bd.eliminaciones, bd.syncInicial, RelojFalso())
    }

    private suspend fun local(id: String, version: Int? = 1, nombre: String = "Mío") =
        bd.db.puntoDao().insert(PuntoEntity(id, nombre, 22.0, -102.0, createdAt = 1, version = version))

    private fun json(version: Int? = 6) =
        """{"entityId":"p1","nombre":"Del servidor","latitud":22.123456789,"longitud":-102.5,"createdAt":5,"version":$version}"""

    @Test
    fun el_tipo_es_PUNTO_y_el_envio_va_al_recurso_de_puntos() = runBlocking {
        assertEquals(TipoEntidad.PUNTO, adaptador.tipo)
        local("p1")
        coEvery { remoto.actualizar(any(), any(), any()) } returns ResultadoEnvio.Confirmada(2)

        val resultado = adaptador.enviar(operacion("op", 1, entidad = "p1", tipoEntidad = TipoEntidad.PUNTO, baseVersion = 1))

        assertEquals(ResultadoEnvio.Confirmada(2), resultado)
        coVerify { remoto.actualizar("op", match { (it.entityId == "p1") && (it.nombre == "Mío") }, 1) }
    }

    @Test
    fun crear_y_eliminar_usan_sus_propias_llamadas() = runBlocking {
        local("p1", version = null)
        coEvery { remoto.crear(any(), any()) } returns ResultadoEnvio.Confirmada(1)
        coEvery { remoto.eliminar(any(), any(), any()) } returns ResultadoEnvio.Confirmada(null)

        adaptador.enviar(operacion("a", 1, tipo = TipoOperacion.CREAR, entidad = "p1", tipoEntidad = TipoEntidad.PUNTO))
        adaptador.enviar(operacion("b", 2, tipo = TipoOperacion.ELIMINAR, entidad = "p1", tipoEntidad = TipoEntidad.PUNTO, baseVersion = 3))

        coVerify { remoto.crear("a", any()) }
        coVerify { remoto.eliminar("b", "p1", 3) }
    }

    @Test
    fun el_pull_crea_actualiza_y_conserva_la_version_si_el_servidor_no_la_trae() = runBlocking {
        local("p1", version = 5)
        coEvery { remoto.listar() } returns ResultadoPull.Datos(
            listOf(
                PuntoRemoto("p1", "Renombrado", 22.0, -102.0, 1, version = null),
                PuntoRemoto("p2", "Nuevo", 1.0, 2.0, 1, version = 1)
            )
        )

        adaptador.descargar()

        val p1 = bd.db.puntoDao().findById("p1")!!
        assertEquals("Renombrado", p1.nombre)
        assertEquals(5, p1.version)
        assertEquals("Nuevo", bd.db.puntoDao().findById("p2")!!.nombre)
    }

    @Test
    fun un_punto_con_operacion_pendiente_no_se_sobrescribe_y_uno_confirmado_ausente_se_quita() = runBlocking {
        local("p1", nombre = "Mío sin enviar")
        local("p2", version = 3, nombre = "Borrado en el servidor")
        bd.operaciones.insert(operacion("op", 1, entidad = "p1", tipoEntidad = TipoEntidad.PUNTO))
        coEvery { remoto.listar() } returns ResultadoPull.Datos(listOf(PuntoRemoto("p1", "Del servidor", 0.0, 0.0, 1, 9)))

        val descarga = adaptador.descargar()

        assertEquals("Mío sin enviar", bd.db.puntoDao().findById("p1")!!.nombre)
        assertNull(bd.db.puntoDao().findById("p2"))
        assertEquals(listOf("Borrado en el servidor"), descarga.eliminadas)
    }

    @Test
    fun el_registro_vigente_del_409_compara_con_seis_decimales() = runBlocking {
        local("p1")

        val servidor = adaptador.leerServidor("p1", json())!!

        assertEquals(6, servidor.version)
        assertEquals(
            listOf(
                CampoComparado("Nombre", "Mío", "Del servidor"),
                CampoComparado("Latitud", "22.000000", "22.123457"),
                CampoComparado("Longitud", "-102.000000", "-102.500000")
            ),
            servidor.comparacion()
        )
    }

    @Test
    fun aceptar_el_registro_del_servidor_lo_guarda_con_su_version() = runBlocking {
        local("p1", version = 1)

        adaptador.leerServidor("p1", json(version = 8))!!.aplicar()

        val p = bd.db.puntoDao().findById("p1")!!
        assertEquals("Del servidor", p.nombre)
        assertEquals(8, p.version)
        assertEquals(22.123456789, p.latitud, 0.0)
    }

    @Test
    fun un_json_roto_no_se_puede_leer() {
        for (roto in listOf("", "{", "no es json", """{"nombre":"solo"}""", """{"entityId":"p","nombre":"N","latitud":"x","longitud":0,"createdAt":1}""")) {
            assertNull("«$roto»", adaptador.leerServidor("p1", roto))
        }
    }

    @Test
    fun titulo_existe_borrar_y_versiones() = runBlocking {
        local("p1", version = 1, nombre = "Cerro")

        assertEquals("Cerro", adaptador.titulo("p1"))
        assertNotNull(bd.db.puntoDao().findById("p1"))
        adaptador.actualizarVersion("p1", 9)
        assertEquals(9, bd.db.puntoDao().findById("p1")!!.version)
        adaptador.limpiarVersion("p1")
        assertNull(bd.db.puntoDao().findById("p1")!!.version)
        adaptador.borrar("p1")
        assertNull(adaptador.titulo("p1"))
    }
}
