package com.spiralsoft.servare.notification

import app.cash.turbine.test
import com.spiralsoft.servare.data.connectivity.ConnectivityMonitor
import com.spiralsoft.servare.data.local.dao.OperacionConProblema
import com.spiralsoft.servare.data.local.entity.OperationEntity
import com.spiralsoft.servare.data.local.entity.PruebaEntity
import com.spiralsoft.servare.data.remote.PruebaRemoteDataSource
import com.spiralsoft.servare.data.sesion.GestorSesion
import com.spiralsoft.servare.data.sync.AdaptadorPrueba
import com.spiralsoft.servare.data.sync.Adaptadores
import com.spiralsoft.servare.domain.model.AccionOperacion
import com.spiralsoft.servare.domain.model.AccionOperacion.ACEPTAR_SERVIDOR
import com.spiralsoft.servare.domain.model.AccionOperacion.CONSERVAR_MIA
import com.spiralsoft.servare.domain.model.AccionOperacion.DESCARTAR
import com.spiralsoft.servare.domain.model.AccionOperacion.RECREAR
import com.spiralsoft.servare.domain.model.AccionOperacion.REINTENTAR
import com.spiralsoft.servare.domain.model.AvisoOperacion
import com.spiralsoft.servare.domain.model.AvisoResultado
import com.spiralsoft.servare.domain.model.CausaConflicto
import com.spiralsoft.servare.domain.model.EstadoConectividad.CONECTADO
import com.spiralsoft.servare.domain.model.EstadoConectividad.RED_SIN_SERVIDOR
import com.spiralsoft.servare.domain.model.EstadoConectividad.SIN_RED
import com.spiralsoft.servare.domain.model.EstadoOperacion
import com.spiralsoft.servare.domain.model.EstadoOperacion.BLOQUEADA
import com.spiralsoft.servare.domain.model.EstadoOperacion.CONFLICTO
import com.spiralsoft.servare.domain.model.EstadoOperacion.FALLIDA
import com.spiralsoft.servare.domain.model.EstadoSesion
import com.spiralsoft.servare.domain.model.TipoEntidad
import com.spiralsoft.servare.domain.model.TipoOperacion
import com.spiralsoft.servare.testutil.AdaptadorFalso
import com.spiralsoft.servare.testutil.BaseDeDatosRegla
import com.spiralsoft.servare.testutil.RelojFalso
import com.spiralsoft.servare.testutil.operacion
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Duration.Companion.milliseconds

// Conjunto de lo que el usuario lee sobre el estado de la app sale de aquí: cada caso fija el mensaje y las acciones
@RunWith(RobolectricTestRunner::class)
class ServicioNotificacionEstadoUnitTest {
    @get:Rule val bd = BaseDeDatosRegla()

    private val conectividad = MutableStateFlow(CONECTADO)
    private val sesion = MutableStateFlow<EstadoSesion>(EstadoSesion.ConSesion("a@b.c"))
    private val monitor = mockk<ConnectivityMonitor>().also { every { it.estado } returns conectividad }
    private val gestor = mockk<GestorSesion>().also { every { it.estado } returns sesion }
    private val servicio by lazy {
        val prueba = AdaptadorPrueba(
            bd.pruebas, mockk<PruebaRemoteDataSource>(), Json, bd.db, bd.operaciones, bd.eliminaciones, bd.syncInicial, RelojFalso()
        )
        ServicioNotificacionEstado(
            monitor, gestor, bd.operaciones,
            Adaptadores(
                mapOf(
                    TipoEntidad.PRUEBA to prueba,
                    TipoEntidad.FICHA to AdaptadorFalso(TipoEntidad.FICHA),
                    TipoEntidad.PUNTO to AdaptadorFalso(TipoEntidad.PUNTO),
                )
            ),
        )
    }

    private val servidorV6 = """{"entityId":"e1","titulo":"Del servidor","texto":"t","numero":9,"createdAt":5,"version":6}"""

    private suspend fun registro(id: String = "e1") =
        bd.pruebas.insert(PruebaEntity(id, "Título $id", "texto", 1, createdAt = 1, version = 2))

    private suspend fun <T> esperar(flujo: StateFlow<T>, condicion: (T) -> Boolean): T =
        withTimeout(5_000.milliseconds) { flujo.first(condicion) }

    private suspend fun problemas(cantidad: Int): List<AvisoOperacion> =
        esperar(servicio.problemasOperacion) { it.size == cantidad }

    private fun conflicto(
        id: String = "c", causa: CausaConflicto = CausaConflicto.VERSION_DISTINTA, snapshot: String? = servidorV6,
        tipo: TipoOperacion = TipoOperacion.ACTUALIZAR, entidad: String = "e1", createdAt: Long = 10
    ) = operacion(id, createdAt, estado = CONFLICTO, tipo = tipo, entidad = entidad)
        .copy(conflictCause = causa, serverSnapshot = snapshot, lastError = "El registro cambió")

    private fun fallida(id: String = "f", tipo: TipoOperacion = TipoOperacion.ACTUALIZAR, entidad: String = "e1", createdAt: Long = 10): OperationEntity =
        operacion(id, createdAt, estado = FALLIDA, tipo = tipo, entidad = entidad).copy(lastError = "El servidor rechazó los datos")

    // --- Conectividad ---

    @Test
    fun cada_estado_de_conectividad_tiene_su_mensaje_y_sigue_al_monitor() = runBlocking {
        val esperados = mapOf(SIN_RED to "Sin conexión", RED_SIN_SERVIDOR to "Servidor no disponible", CONECTADO to "Conectado")
        for ((estado, titulo) in esperados) {
            conectividad.value = estado
            val aviso = esperar(servicio.conectividad) { it.estado == estado }
            assertEquals(titulo, aviso.titulo)
        }
    }

    @Test
    fun sin_red_el_aviso_tranquiliza_que_los_cambios_se_guardan() {
        conectividad.value = SIN_RED
        assertTrue(servicio.conectividad.value.detalle.contains("tus cambios se guardan en el dispositivo"))
    }

    @Test
    fun la_descarga_inicial_explica_su_causa_segun_el_estado() = runBlocking {
        val partes = mapOf(
            SIN_RED to "no tiene red", RED_SIN_SERVIDOR to "el servidor no responde", CONECTADO to "solicitando al servidor"
        )
        for ((estado, parte) in partes) {
            conectividad.value = estado
            val aviso = esperar(servicio.descargaInicial) { it.detalle.contains(parte) }
            assertEquals("Aún no se descargaron los registros", aviso.titulo)
        }
    }

    @Test
    fun registro_e_inicio_de_sesion_solo_estan_disponibles_estando_conectado() = runBlocking {
        conectividad.value = CONECTADO
        assertNull(esperar(servicio.registroNoDisponible) { it == null })
        assertNull(esperar(servicio.inicioSesionNoDisponible) { it == null })

        conectividad.value = SIN_RED
        val sinRed = esperar(servicio.registroNoDisponible) { it != null }!!
        assertEquals("Crear la cuenta requiere conexión", sinRed.titulo)
        assertTrue(sinRed.detalle.contains("registrarte"))

        conectividad.value = RED_SIN_SERVIDOR
        val sinServidor = esperar(servicio.inicioSesionNoDisponible) { it?.detalle?.contains("servidor no responde") == true }!!
        assertEquals("Iniciar sesión requiere conexión", sinServidor.titulo)
        assertTrue(sinServidor.detalle.contains("iniciar sesión"))
    }

    // --- Sesión ---

    @Test
    fun solo_la_sesion_expirada_avisa_y_nombra_la_cuenta() = runBlocking {
        for (estado in listOf(EstadoSesion.Cargando, EstadoSesion.SinSesion, EstadoSesion.ConSesion("a@b.c"))) {
            sesion.value = estado
            assertNull(esperar(servicio.sesionExpirada) { it == null })
        }
        sesion.value = EstadoSesion.Expirada("a@b.c")

        val aviso = esperar(servicio.sesionExpirada) { it != null }!!

        assertEquals("Tu sesión expiró", aviso.titulo)
        assertTrue(aviso.detalle.contains("a@b.c"))
        assertTrue(aviso.detalle.contains("tus cambios se guardan en el dispositivo"))
    }

    // --- Resultado de un ciclo ---

    @Test
    fun un_ciclo_sin_novedades_no_avisa() = runBlocking {
        servicio.resultados.test {
            servicio.operacionesFinalizadas(0, emptyList())
            expectNoEvents()
        }
    }

    @Test
    fun las_confirmadas_se_cuentan_en_singular_y_en_plural() = runBlocking {
        servicio.resultados.test {
            servicio.operacionesFinalizadas(1, emptyList())
            assertEquals(AvisoResultado("Se sincronizó 1 cambio con el servidor"), awaitItem())
            servicio.operacionesFinalizadas(4, emptyList())
            assertEquals(AvisoResultado("Se sincronizaron 4 cambios con el servidor"), awaitItem())
        }
    }

    private fun problema(tipo: TipoOperacion, estado: EstadoOperacion = FALLIDA, titulo: String? = "Hola") = OperacionConProblema(
        operationId = "o", type = tipo, status = estado, entityType = TipoEntidad.PRUEBA, entityId = "e1",
        lastError = "x", conflictCause = null, titulo = titulo
    )

    @Test
    fun un_solo_problema_se_nombra_y_los_exitos_se_suman_en_el_mismo_mensaje() = runBlocking {
        servicio.resultados.test {
            servicio.operacionesFinalizadas(0, listOf(problema(TipoOperacion.CREAR)))
            assertEquals("No se pudo enviar «Hola» al servidor", awaitItem().mensaje)

            servicio.operacionesFinalizadas(1, listOf(problema(TipoOperacion.ACTUALIZAR)))
            assertEquals("No se pudieron enviar los cambios de «Hola». Otro cambio sí se sincronizó", awaitItem().mensaje)

            servicio.operacionesFinalizadas(3, listOf(problema(TipoOperacion.ELIMINAR)))
            assertEquals("No se pudo eliminar «Hola» del servidor. Otros 3 cambios sí se sincronizaron", awaitItem().mensaje)
        }
    }

    @Test
    fun varios_problemas_se_cuentan_sin_nombrar_ninguno() = runBlocking {
        servicio.resultados.test {
            servicio.operacionesFinalizadas(2, listOf(problema(TipoOperacion.CREAR), problema(TipoOperacion.ACTUALIZAR)))
            assertEquals("2 cambios no se pudieron sincronizar. Otros 2 cambios sí se sincronizaron", awaitItem().mensaje)
        }
    }

    @Test
    fun un_conflicto_se_nombra_con_su_registro_y_si_no_hay_titulo_dice_un_registro() = runBlocking {
        servicio.resultados.test {
            servicio.operacionesFinalizadas(0, listOf(problema(TipoOperacion.ACTUALIZAR, CONFLICTO)))
            assertEquals("«Hola» tiene cambios por resolver", awaitItem().mensaje)

            servicio.operacionesFinalizadas(0, listOf(problema(TipoOperacion.ACTUALIZAR, CONFLICTO, titulo = null)))
            assertEquals("un registro tiene cambios por resolver", awaitItem().mensaje)
        }
    }

    @Test
    fun los_borrados_en_el_servidor_se_avisan_en_singular_o_plural_y_vacio_no_avisa() = runBlocking {
        servicio.resultados.test {
            servicio.eliminadosEnServidor(emptyList())
            expectNoEvents()
            servicio.eliminadosEnServidor(listOf("Uno"))
            assertEquals("«Uno» ya no existe en el servidor y se quitó de la lista", awaitItem().mensaje)
            servicio.eliminadosEnServidor(listOf("Uno", "Dos"))
            assertEquals("2 registros ya no existen en el servidor y se quitaron de la lista", awaitItem().mensaje)
        }
        assertEquals("«X» ya no existe en el servidor y se quitó de la lista", servicio.textoEliminadosEnServidor(listOf("X")))
    }

    // --- Problemas de la cola ---

    @Test
    fun una_fallida_ofrece_reintentar_o_descartar_y_conserva_el_motivo() = runBlocking {
        registro()
        bd.operaciones.insert(fallida(tipo = TipoOperacion.ACTUALIZAR))

        val aviso = problemas(1).single()

        assertEquals(FALLIDA, aviso.estado)
        assertEquals("No se pudieron enviar los cambios de «Título e1»", aviso.titulo)
        assertEquals(
            "El servidor rechazó los datos. El cambio sigue guardado en el dispositivo.", aviso.detalle
        )
        assertEquals(listOf(REINTENTAR, DESCARTAR), aviso.acciones)
        assertNull(aviso.comparacion)
    }

    @Test
    fun el_titulo_de_la_fallida_depende_del_tipo_de_operacion() = runBlocking {
        val esperados = mapOf(
            TipoOperacion.CREAR to "No se pudo enviar «Título eCREAR» al servidor",
            TipoOperacion.ELIMINAR to "No se pudo eliminar «Título eELIMINAR» del servidor",
            TipoOperacion.ADJUNTAR to "No se pudo subir un archivo de «Título eADJUNTAR»"
        )
        esperados.keys.forEachIndexed { i, tipo ->
            registro("e$tipo")
            bd.operaciones.insert(fallida(id = tipo.name, tipo = tipo, entidad = "e$tipo", createdAt = i.toLong()))
        }

        val avisos = problemas(esperados.size).associateBy { it.operationId }

        esperados.forEach { (tipo, titulo) -> assertEquals(titulo, avisos.getValue(tipo.name).titulo) }
    }

    @Test
    fun las_posteriores_bloqueadas_se_cuentan_dentro_del_aviso_y_no_salen_solas() = runBlocking {
        registro()
        bd.operaciones.insert(fallida(createdAt = 10))
        bd.operaciones.insert(operacion("b1", 20, estado = BLOQUEADA))
        bd.operaciones.insert(operacion("b2", 30, estado = BLOQUEADA))

        val aviso = problemas(1).single()

        assertTrue(aviso.detalle.endsWith("Hay 2 cambios posteriores en espera."))
        assertTrue(aviso.confirmaciones.getValue(DESCARTAR).endsWith("También se descartan 2 cambios posteriores."))
    }

    @Test
    fun con_una_sola_posterior_el_texto_va_en_singular() = runBlocking {
        registro()
        bd.operaciones.insert(fallida(createdAt = 10))
        bd.operaciones.insert(operacion("b1", 20, estado = BLOQUEADA))

        val aviso = problemas(1).single()

        assertTrue(aviso.detalle.endsWith("Hay 1 cambio posterior en espera."))
        assertTrue(aviso.confirmaciones.getValue(DESCARTAR).endsWith("También se descarta 1 cambio posterior."))
    }

    @Test
    fun una_bloqueada_cuyo_origen_es_de_otro_registro_se_avisa_sola() = runBlocking {
        registro("e1")
        registro("e2")
        bd.operaciones.insert(fallida(entidad = "e1", createdAt = 10))
        bd.operaciones.insert(operacion("huerfana", 20, estado = BLOQUEADA, entidad = "e2", dependsOn = "f"))

        val avisos = problemas(2)

        val bloqueada = avisos.single { it.estado == BLOQUEADA }
        assertEquals("Cambios en espera en «Título e2»", bloqueada.titulo)
        assertTrue(bloqueada.detalle.contains("no se enviarán hasta resolverlo"))
        assertTrue(bloqueada.acciones.isEmpty())
    }

    @Test
    fun los_avisos_salen_de_los_mas_antiguos_a_los_mas_recientes() = runBlocking {
        registro("e1")
        registro("e2")
        bd.operaciones.insert(fallida(id = "reciente", entidad = "e2", createdAt = 50))
        bd.operaciones.insert(fallida(id = "antigua", entidad = "e1", createdAt = 5))

        assertEquals(listOf("antigua", "reciente"), problemas(2).map { it.operationId })
    }

    @Test
    fun un_registro_que_ya_no_esta_en_el_dispositivo_se_nombra_un_registro() = runBlocking {
        bd.operaciones.insert(fallida(entidad = "borrado"))

        assertEquals("No se pudieron enviar los cambios de un registro", problemas(1).single().titulo)
    }

    @Test
    fun los_problemas_resueltos_desaparecen_del_aviso() = runBlocking {
        registro()
        bd.operaciones.insert(fallida())
        problemas(1)

        bd.operaciones.reintentar("f")

        assertTrue(esperar(servicio.problemasOperacion) { it.isEmpty() }.isEmpty())
    }

    // --- Conflictos ---

    @Test
    fun un_conflicto_por_version_con_el_registro_del_servidor_deja_elegir_y_compara() = runBlocking {
        registro()
        bd.operaciones.insert(conflicto())

        val aviso = problemas(1).single()

        assertEquals(CONFLICTO, aviso.estado)
        assertEquals("«Título e1» tiene cambios por resolver", aviso.titulo)
        assertEquals(listOf(CONSERVAR_MIA, ACEPTAR_SERVIDOR), aviso.acciones)
        assertTrue(aviso.detalle.endsWith("Consulta ambas versiones y decide cuál conservar."))
        val titulo = aviso.comparacion!!.first { it.nombre == "Título" }
        assertEquals("Del servidor", titulo.servidor)
        assertEquals("Título e1", titulo.local)
        assertEquals("Tu versión se enviará y reemplazará la que está en el servidor.", aviso.confirmaciones.getValue(CONSERVAR_MIA))
        assertEquals("Tus cambios se reemplazarán por la versión del servidor.", aviso.confirmaciones.getValue(ACEPTAR_SERVIDOR))
    }

    @Test
    fun sin_el_registro_del_servidor_solo_se_puede_descartar() = runBlocking {
        val casos: Map<String, String?> = mapOf(
            "sin_snapshot" to null,
            "snapshot_ilegible" to "{roto",
            "snapshot_sin_version" to servidorV6.replace(""","version":6""", "")
        )
        casos.entries.forEachIndexed { i, (nombre, snapshot) ->
            registro("e$nombre")
            bd.operaciones.insert(conflicto(id = nombre, snapshot = snapshot, entidad = "e$nombre", createdAt = i.toLong()))
        }

        val avisos = problemas(casos.size)

        assertEquals(casos.keys, avisos.asSequence().map { it.operationId }.toSet())
        avisos.forEach { assertEquals(it.operationId, listOf(DESCARTAR), it.acciones) }
    }

    @Test
    fun si_el_registro_ya_no_existe_en_el_servidor_se_puede_recrear_o_descartar() = runBlocking {
        registro()
        bd.operaciones.insert(conflicto(causa = CausaConflicto.INEXISTENTE_EN_SERVIDOR, snapshot = null))

        val aviso = problemas(1).single()

        assertEquals(listOf(RECREAR, DESCARTAR), aviso.acciones)
        assertEquals(
            "Se eliminará el registro del dispositivo, porque ya no existe en el servidor.",
            aviso.confirmaciones.getValue(DESCARTAR)
        )
    }

    @Test
    fun un_borrado_de_algo_que_ya_no_existe_solo_se_descarta() = runBlocking {
        registro()
        bd.operaciones.insert(conflicto(causa = CausaConflicto.INEXISTENTE_EN_SERVIDOR, snapshot = null, tipo = TipoOperacion.ELIMINAR))

        val aviso = problemas(1).single()

        assertEquals(listOf(DESCARTAR), aviso.acciones)
        assertEquals("El registro ya no existe en el servidor: se quitará del dispositivo.", aviso.confirmaciones.getValue(DESCARTAR))
    }

    @Test
    fun un_borrado_en_conflicto_por_version_avisa_lo_que_implica_cada_decision() = runBlocking {
        registro()
        bd.operaciones.insert(conflicto(tipo = TipoOperacion.ELIMINAR))

        val aviso = problemas(1).single()

        assertEquals("Se eliminará el registro del servidor, aunque cambió allí.", aviso.confirmaciones.getValue(CONSERVAR_MIA))
        assertEquals("El registro volverá a mostrarse con la versión del servidor.", aviso.confirmaciones.getValue(ACEPTAR_SERVIDOR))
        assertEquals("El registro volverá a mostrarse en la lista.", aviso.confirmaciones.getValue(DESCARTAR))
    }

    @Test
    fun descartar_dice_que_se_pierde_o_que_se_elimina_segun_el_tipo() = runBlocking {
        val esperados = mapOf(
            TipoOperacion.CREAR to "Se eliminará el registro del dispositivo, porque nunca llegó al servidor.",
            TipoOperacion.ACTUALIZAR to "Se perderán tus cambios: al sincronizar, el registro volverá a la versión del servidor.",
            TipoOperacion.ELIMINAR to "El registro volverá a mostrarse en la lista."
        )
        esperados.keys.forEachIndexed { i, tipo ->
            registro("e$tipo")
            bd.operaciones.insert(fallida(id = tipo.name, tipo = tipo, entidad = "e$tipo", createdAt = i.toLong()))
        }

        val avisos = problemas(esperados.size).associateBy { it.operationId }

        esperados.forEach { (tipo, texto) -> assertEquals(tipo.name, texto, avisos.getValue(tipo.name).confirmaciones.getValue(DESCARTAR)) }
    }

    @Test
    fun el_servicio_nunca_inventa_acciones_fuera_de_las_del_diseno() = runBlocking {
        registro()
        bd.operaciones.insert(conflicto())
        val aviso = problemas(1).single()
        assertTrue(aviso.acciones.all { it in AccionOperacion.entries })
        assertTrue("toda acción ofrecida tiene confirmación o es no destructiva", aviso.acciones.all { it == REINTENTAR || (it in aviso.confirmaciones) })
    }
}
