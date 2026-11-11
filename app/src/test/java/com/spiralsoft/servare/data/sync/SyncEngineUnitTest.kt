package com.spiralsoft.servare.data.sync

import com.spiralsoft.servare.data.connectivity.ConnectivityMonitor
import com.spiralsoft.servare.data.local.dao.OperacionConProblema
import com.spiralsoft.servare.data.local.entity.EliminacionEntity
import com.spiralsoft.servare.data.local.entity.PruebaEntity
import com.spiralsoft.servare.data.local.entity.SyncInicialEntity
import com.spiralsoft.servare.data.remote.PruebaRemota
import com.spiralsoft.servare.data.remote.PruebaRemoteDataSource
import com.spiralsoft.servare.data.remote.ResultadoEnvio
import com.spiralsoft.servare.data.remote.ResultadoPull
import com.spiralsoft.servare.data.sesion.GestorSesion
import com.spiralsoft.servare.domain.model.CausaConflicto
import com.spiralsoft.servare.domain.model.EstadoOperacion.BLOQUEADA
import com.spiralsoft.servare.domain.model.EstadoOperacion.COMPLETADA
import com.spiralsoft.servare.domain.model.EstadoOperacion.CONFLICTO
import com.spiralsoft.servare.domain.model.EstadoOperacion.FALLIDA
import com.spiralsoft.servare.domain.model.EstadoOperacion.PENDIENTE
import com.spiralsoft.servare.domain.model.EstadoOperacion.PROCESANDO
import com.spiralsoft.servare.domain.model.EstadoOperacion.REINTENTABLE
import com.spiralsoft.servare.domain.model.EstadoSesion
import com.spiralsoft.servare.domain.model.TipoEntidad
import com.spiralsoft.servare.domain.model.TipoOperacion
import com.spiralsoft.servare.domain.repository.ResultadoRefresco
import com.spiralsoft.servare.notification.ServicioNotificacionEstado
import com.spiralsoft.servare.testutil.AdaptadorFalso
import com.spiralsoft.servare.testutil.BaseDeDatosRegla
import com.spiralsoft.servare.testutil.RelojFalso
import com.spiralsoft.servare.testutil.operacion
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
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
import kotlin.time.Duration.Companion.milliseconds

// El ciclo completo contra Room real. Se doblan solo las fronteras: la red (PruebaRemoteDataSource),
// la conectividad, la sesión y los avisos. FICHA y PUNTO usan un adaptador inerte
@RunWith(RobolectricTestRunner::class)
class SyncEngineUnitTest {
    @get:Rule val bd = BaseDeDatosRegla()

    private val reloj = RelojFalso()
    private val sesion = MutableStateFlow<EstadoSesion>(EstadoSesion.ConSesion("a@b.c"))
    private val gestor = mockk<GestorSesion>(relaxed = true).also { every { it.estado } returns sesion }
    private val monitor = mockk<ConnectivityMonitor>(relaxed = true)
    private val notificaciones = mockk<ServicioNotificacionEstado>(relaxed = true)
    private val remoto = mockk<PruebaRemoteDataSource>()

    // Lo que recibió el servidor, en orden: (operationId, baseVersion)
    private val enviadas = mutableListOf<Pair<String, Int?>>()
    private val orden = mutableListOf<String>()

    private val motor by lazy {
        val adaptadorPrueba = AdaptadorPrueba(
            bd.pruebas, remoto, Json, bd.db, bd.operaciones, bd.eliminaciones, bd.syncInicial, reloj
        )
        SyncEngine(
            bd.db, bd.operaciones, bd.eliminaciones, bd.syncInicial,
            Adaptadores(
                mapOf(
                    TipoEntidad.PRUEBA to adaptadorPrueba,
                    TipoEntidad.FICHA to AdaptadorFalso(TipoEntidad.FICHA),
                    TipoEntidad.PUNTO to AdaptadorFalso(TipoEntidad.PUNTO)
                )
            ),
            monitor, gestor, notificaciones, reloj
        )
    }

    private var respuestaEnvio: (String) -> ResultadoEnvio = { ResultadoEnvio.Confirmada(null) }
    private var respuestaPull: ResultadoPull<PruebaRemota> = ResultadoPull.Datos(emptyList())

    private fun preparar() {
        coEvery { monitor.confirmarAlcanzabilidad() } returns true
        coEvery { remoto.actualizar(any(), any(), any()) } answers {
            val id = firstArg<String>()
            enviadas += id to thirdArg<Int?>()
            orden += "push"
            respuestaEnvio(id)
        }
        coEvery { remoto.crear(any(), any()) } answers {
            val id = firstArg<String>()
            enviadas += id to null
            orden += "push"
            respuestaEnvio(id)
        }
        coEvery { remoto.eliminar(any(), any(), any()) } answers {
            val id = firstArg<String>()
            enviadas += id to thirdArg<Int?>()
            orden += "push"
            respuestaEnvio(id)
        }
        coEvery { remoto.listar() } answers {
            orden += "pull"
            respuestaPull
        }
    }

    private suspend fun sembrar(id: String, version: Int? = 1) =
        bd.pruebas.insert(PruebaEntity(id, "Título $id", "texto", 1, createdAt = 1, version = version))

    private suspend fun estado(id: String) = bd.operaciones.buscar(id)!!.status

    private suspend fun ciclo(): ResultadoCiclo {
        preparar()
        return motor.ejecutarCiclo()
    }

    // Para probar solo el push: un pull vacío borraría, con razón, el registro ya confirmado que el servidor no lista
    private suspend fun sinEfectoDelPull() {
        descargaInicialCompletada()
        respuestaPull = ResultadoPull.Fallo(MOTIVO_PULL)
    }

    private suspend fun descargaInicialCompletada() =
        TipoEntidad.entries.forEach { bd.syncInicial.marcarCompletada(SyncInicialEntity(it, 1)) }

    // --- Precondiciones del ciclo ---

    @Test
    fun sin_sesion_no_se_sincroniza_y_la_cola_queda_como_esta() = runBlocking {
        for (estadoSesion in listOf(
            EstadoSesion.SinSesion, EstadoSesion.Cargando, EstadoSesion.Expirada("a@b.c")
        )) {
            sesion.value = estadoSesion
            sembrar("e1")
            bd.operaciones.insert(operacion("a", 10, entidad = "e1"))

            assertEquals(ResultadoCiclo.SesionExpirada, ciclo())

            assertEquals(PENDIENTE, estado("a"))
            assertTrue(enviadas.isEmpty())
            bd.db.clearAllTables()
        }
        coVerify(exactly = 0) { monitor.confirmarAlcanzabilidad() }
    }

    @Test
    fun sin_servidor_el_ciclo_se_detiene_sin_tocar_la_cola() = runBlocking {
        sembrar("e1")
        bd.operaciones.insert(operacion("a", 10, entidad = "e1"))
        preparar()
        coEvery { monitor.confirmarAlcanzabilidad() } returns false

        assertEquals(ResultadoCiclo.SinServidor, motor.ejecutarCiclo())

        assertEquals(PENDIENTE, estado("a"))
        assertEquals(0, bd.operaciones.buscar("a")!!.attemptCount)
        assertTrue(enviadas.isEmpty())
        assertTrue("sin servidor tampoco hay pull", "pull" !in orden)
    }

    @Test
    fun la_retencion_se_purga_aunque_no_haya_servidor() = runBlocking {
        val siete = 7 * 24 * 60 * 60 * 1000L
        bd.operaciones.insert(operacion("vencida", 1, estado = COMPLETADA, finishedAt = reloj.ahora - siete - 1, entidad = "e1"))
        bd.operaciones.insert(operacion("dentro", 1, estado = COMPLETADA, finishedAt = reloj.ahora - siete + 1, entidad = "e2"))
        bd.operaciones.insert(operacion("fallida", 1, estado = FALLIDA, finishedAt = 1, entidad = "e3"))
        preparar()
        coEvery { monitor.confirmarAlcanzabilidad() } returns false

        motor.ejecutarCiclo()

        assertNull(bd.operaciones.buscar("vencida"))
        assertNotNull(bd.operaciones.buscar("dentro"))
        assertNotNull("FALLIDA no se purga nunca", bd.operaciones.buscar("fallida"))
    }

    @Test
    fun una_operacion_interrumpida_se_reenvia_con_el_mismo_operationId_sin_sumar_intento() = runBlocking {
        sembrar("e1")
        bd.operaciones.insert(operacion("huerfana", 10, estado = PROCESANDO, entidad = "e1"))

        ciclo()

        assertEquals(listOf("huerfana"), enviadas.map { it.first })
        val op = bd.operaciones.buscar("huerfana")!!
        assertEquals(COMPLETADA, op.status)
        assertEquals(0, op.attemptCount)
        assertFalse(op.uncertain)
    }

    // --- Resultado de cada envío ---

    @Test
    fun confirmada_completa_la_operacion_y_guarda_la_version_del_servidor() = runBlocking {
        sembrar("e1", version = 1)
        bd.operaciones.insert(operacion("a", 10, entidad = "e1", baseVersion = 1))
        respuestaEnvio = { ResultadoEnvio.Confirmada(2) }
        sinEfectoDelPull()

        val resultado = ciclo()

        assertEquals(COMPLETADA, estado("a"))
        assertEquals(reloj.ahora, bd.operaciones.buscar("a")!!.finishedAt)
        assertEquals(2, bd.pruebas.findById("e1")!!.version)
        assertEquals(ResultadoCiclo.Terminado(null, MOTIVO_PULL), resultado)
    }

    @Test
    fun confirmada_sin_version_no_toca_la_version_guardada() = runBlocking {
        sembrar("e1", version = 5)
        bd.operaciones.insert(operacion("a", 10, entidad = "e1"))
        sinEfectoDelPull()

        ciclo()

        assertEquals(COMPLETADA, estado("a"))
        assertEquals(5, bd.pruebas.findById("e1")!!.version)
    }

    @Test
    fun la_version_confirmada_se_encadena_a_la_siguiente_edicion_de_la_misma_entidad() = runBlocking {
        sembrar("e1", version = 1)
        bd.operaciones.insert(operacion("primera", 10, entidad = "e1", baseVersion = 1))
        bd.operaciones.insert(operacion("segunda", 20, entidad = "e1", baseVersion = 1))
        var siguiente = 2
        respuestaEnvio = { ResultadoEnvio.Confirmada(siguiente++) }
        sinEfectoDelPull()

        ciclo()

        assertEquals(listOf("primera" to 1, "segunda" to 2), enviadas)
        assertEquals(3, bd.pruebas.findById("e1")!!.version)
    }

    @Test
    fun confirmar_un_eliminar_borra_el_registro_y_su_marca_de_borrado() = runBlocking {
        sembrar("e1")
        bd.eliminaciones.insert(EliminacionEntity(TipoEntidad.PRUEBA, "e1", eliminadaEn = 1))
        bd.operaciones.insert(operacion("a", 10, tipo = TipoOperacion.ELIMINAR, entidad = "e1", baseVersion = 1))

        ciclo()

        assertEquals(COMPLETADA, estado("a"))
        assertNull(bd.pruebas.findById("e1"))
        assertTrue(bd.eliminaciones.entidadesEliminadas(TipoEntidad.PRUEBA).isEmpty())
    }

    @Test
    fun una_conexion_perdida_no_suma_intento_detiene_el_ciclo_y_avisa_al_monitor() = runBlocking {
        sembrar("e1")
        sembrar("e2")
        bd.operaciones.insert(operacion("a", 10, entidad = "e1"))
        bd.operaciones.insert(operacion("b", 20, entidad = "e2"))
        respuestaEnvio = { ResultadoEnvio.Incierto }

        val resultado = ciclo()

        assertEquals(ResultadoCiclo.SinServidor, resultado)
        val a = bd.operaciones.buscar("a")!!
        assertEquals(PENDIENTE, a.status)
        assertTrue(a.uncertain)
        assertEquals(0, a.attemptCount)
        assertEquals(listOf("a"), enviadas.map { it.first })
        assertEquals(PENDIENTE, estado("b"))
        verify { monitor.reportarServidorNoAlcanzable() }
        assertTrue("no hay pull con el servidor caído", "pull" !in orden)
    }

    @Test
    fun un_error_temporal_con_servidor_alcanzable_suma_intento_y_sigue_con_las_demas() = runBlocking {
        sembrar("e1")
        sembrar("e2")
        bd.operaciones.insert(operacion("a", 10, entidad = "e1"))
        bd.operaciones.insert(operacion("b", 20, entidad = "e2"))
        respuestaEnvio = { if (it == "a") ResultadoEnvio.Temporal("Error del servidor (503)") else ResultadoEnvio.Confirmada(null) }

        val resultado = ciclo()

        val a = bd.operaciones.buscar("a")!!
        assertEquals(REINTENTABLE, a.status)
        assertEquals(1, a.attemptCount)
        assertEquals(reloj.ahora + 10_000, a.nextAttemptAt)
        assertEquals("Error del servidor (503)", a.lastError)
        assertEquals(COMPLETADA, estado("b"))
        verify(exactly = 0) { monitor.reportarServidorNoAlcanzable() }
        assertEquals(ResultadoCiclo.Terminado(10_000L), resultado)
    }

    @Test
    fun un_timeout_suma_intento_pero_ademas_detiene_el_ciclo() = runBlocking {
        sembrar("e1")
        sembrar("e2")
        bd.operaciones.insert(operacion("a", 10, entidad = "e1"))
        bd.operaciones.insert(operacion("b", 20, entidad = "e2"))
        respuestaEnvio = { ResultadoEnvio.Temporal("Tiempo agotado", sinRespuesta = true) }

        val resultado = ciclo()

        assertEquals(ResultadoCiclo.SinServidor, resultado)
        assertEquals(REINTENTABLE, estado("a"))
        assertEquals(1, bd.operaciones.buscar("a")!!.attemptCount)
        assertEquals(PENDIENTE, estado("b"))
        verify { monitor.reportarServidorNoAlcanzable() }
    }

    @Test
    fun la_espera_de_cada_intento_sigue_la_politica_de_backoff() = runBlocking {
        respuestaEnvio = { ResultadoEnvio.Temporal("503") }
        for (intento in 1 until PoliticaReintentos.MAX_INTENTOS) {
            bd.db.clearAllTables()
            sembrar("e1")
            bd.operaciones.insert(operacion("a", 10, entidad = "e1").copy(attemptCount = intento - 1))

            ciclo()

            val a = bd.operaciones.buscar("a")!!
            assertEquals("intento $intento", REINTENTABLE, a.status)
            assertEquals("intento $intento", intento, a.attemptCount)
            assertEquals("intento $intento", reloj.ahora + PoliticaReintentos.espera(intento), a.nextAttemptAt)
        }
    }

    @Test
    fun en_el_intento_trece_la_operacion_falla_y_bloquea_las_posteriores() = runBlocking {
        sembrar("e1")
        bd.operaciones.insert(operacion("a", 10, entidad = "e1").copy(attemptCount = PoliticaReintentos.MAX_INTENTOS - 1))
        bd.operaciones.insert(operacion("b", 20, entidad = "e1"))
        respuestaEnvio = { ResultadoEnvio.Temporal("Error del servidor (503)") }

        ciclo()

        val a = bd.operaciones.buscar("a")!!
        assertEquals(FALLIDA, a.status)
        assertEquals(13, a.attemptCount)
        assertNull(a.nextAttemptAt)
        assertEquals("Error del servidor (503). Se agotaron los 13 intentos", a.lastError)
        assertEquals(BLOQUEADA, estado("b"))
        assertEquals("solo se envió la primera", listOf("a"), enviadas.map { it.first })
    }

    @Test
    fun un_conflicto_guarda_causa_y_registro_vigente_y_bloquea_las_posteriores() = runBlocking {
        sembrar("e1")
        sembrar("e2")
        bd.operaciones.insert(operacion("a", 10, entidad = "e1", baseVersion = 1))
        bd.operaciones.insert(operacion("b", 20, entidad = "e1"))
        bd.operaciones.insert(operacion("otra", 30, entidad = "e2"))
        respuestaEnvio = {
            if (it == "a") ResultadoEnvio.Conflicto("Versión distinta", CausaConflicto.VERSION_DISTINTA, """{"version":3}""")
            else ResultadoEnvio.Confirmada(null)
        }

        ciclo()

        val a = bd.operaciones.buscar("a")!!
        assertEquals(CONFLICTO, a.status)
        assertEquals(CausaConflicto.VERSION_DISTINTA, a.conflictCause)
        assertEquals("""{"version":3}""", a.serverSnapshot)
        assertEquals(BLOQUEADA, estado("b"))
        assertEquals("otras entidades siguen", COMPLETADA, estado("otra"))
    }

    @Test
    fun un_error_definitivo_falla_sin_cambiar_los_intentos_y_bloquea_las_posteriores() = runBlocking {
        sembrar("e1")
        bd.operaciones.insert(operacion("a", 10, entidad = "e1"))
        bd.operaciones.insert(operacion("b", 20, entidad = "e1"))
        respuestaEnvio = { ResultadoEnvio.Definitivo("El servidor rechazó los datos (422)") }

        val resultado = ciclo()

        val a = bd.operaciones.buscar("a")!!
        assertEquals(FALLIDA, a.status)
        assertEquals(0, a.attemptCount)
        assertEquals("El servidor rechazó los datos (422)", a.lastError)
        assertEquals(BLOQUEADA, estado("b"))
        assertEquals(ResultadoCiclo.Terminado(null), resultado)
    }

    @Test
    fun el_401_pausa_toda_la_cola_deja_la_operacion_pendiente_y_expira_la_sesion() = runBlocking {
        sembrar("e1")
        sembrar("e2")
        bd.operaciones.insert(operacion("a", 10, entidad = "e1"))
        bd.operaciones.insert(operacion("b", 20, entidad = "e2"))
        respuestaEnvio = { ResultadoEnvio.SesionExpirada }

        val resultado = ciclo()

        assertEquals(ResultadoCiclo.SesionExpirada, resultado)
        val a = bd.operaciones.buscar("a")!!
        assertEquals(PENDIENTE, a.status)
        assertEquals(0, a.attemptCount)
        assertFalse(a.uncertain)
        assertNull(a.lastError)
        assertEquals(PENDIENTE, estado("b"))
        assertEquals(listOf("a"), enviadas.map { it.first })
        verify { gestor.expirar() }
        assertTrue("sin sesión tampoco hay pull", "pull" !in orden)
    }

    @Test
    fun un_registro_que_ya_no_esta_en_el_dispositivo_falla_sin_llamar_al_servidor() = runBlocking {
        bd.operaciones.insert(operacion("a", 10, entidad = "fantasma"))

        ciclo()

        val a = bd.operaciones.buscar("a")!!
        assertEquals(FALLIDA, a.status)
        assertEquals("El registro ya no está en el dispositivo", a.lastError)
        assertTrue(enviadas.isEmpty())
    }

    // --- Orden, dependencias y reintentos ---

    @Test
    fun las_operaciones_salen_por_antiguedad() = runBlocking {
        sembrar("e1")
        sembrar("e2")
        bd.operaciones.insert(operacion("tarde", 30, entidad = "e2"))
        bd.operaciones.insert(operacion("temprano", 10, entidad = "e1"))
        bd.operaciones.insert(operacion("medio", 20, entidad = "e1"))

        ciclo()

        assertEquals(listOf("temprano", "medio", "tarde"), enviadas.map { it.first })
    }

    @Test
    fun una_dependiente_sale_despues_de_su_dependencia_y_se_bloquea_si_esta_falla() = runBlocking {
        sembrar("padre")
        sembrar("hija")
        bd.operaciones.insert(operacion("crear_padre", 20, tipo = TipoOperacion.CREAR, entidad = "padre"))
        bd.operaciones.insert(operacion("crear_hija", 10, tipo = TipoOperacion.CREAR, entidad = "hija", dependsOn = "crear_padre"))
        respuestaEnvio = { ResultadoEnvio.Definitivo("422") }

        ciclo()

        assertEquals(listOf("crear_padre"), enviadas.map { it.first })
        assertEquals(FALLIDA, estado("crear_padre"))
        assertEquals(BLOQUEADA, estado("crear_hija"))
    }

    @Test
    fun una_dependiente_sale_cuando_la_dependencia_se_completa() = runBlocking {
        sembrar("padre")
        sembrar("hija")
        bd.operaciones.insert(operacion("crear_padre", 20, tipo = TipoOperacion.CREAR, entidad = "padre"))
        bd.operaciones.insert(operacion("crear_hija", 10, tipo = TipoOperacion.CREAR, entidad = "hija", dependsOn = "crear_padre"))

        ciclo()

        assertEquals(listOf("crear_padre", "crear_hija"), enviadas.map { it.first })
    }

    @Test
    fun el_reintento_usa_el_mismo_operationId_y_solo_cuando_vence_la_espera() = runBlocking {
        sembrar("e1")
        bd.operaciones.insert(operacion("a", 10, entidad = "e1"))
        respuestaEnvio = { ResultadoEnvio.Temporal("503") }
        ciclo()
        assertEquals(1, enviadas.size)

        reloj.avanzar(9_999)
        ciclo()
        assertEquals("aún no vence", 1, enviadas.size)

        reloj.avanzar(1)
        respuestaEnvio = { ResultadoEnvio.Confirmada(null) }
        ciclo()

        assertEquals(listOf("a", "a"), enviadas.map { it.first })
        assertEquals(COMPLETADA, estado("a"))
        assertEquals(1, bd.operaciones.buscar("a")!!.attemptCount)
    }

    @Test
    fun una_reintentable_pendiente_detiene_a_las_posteriores_de_su_entidad() = runBlocking {
        sembrar("e1")
        bd.operaciones.insert(operacion("a", 10, entidad = "e1"))
        bd.operaciones.insert(operacion("b", 20, entidad = "e1"))
        respuestaEnvio = { ResultadoEnvio.Temporal("503") }

        ciclo()

        assertEquals("b no adelanta a a", listOf("a"), enviadas.map { it.first })
        assertEquals(PENDIENTE, estado("b"))
    }

    // --- Pull ---

    @Test
    fun el_pull_va_despues_del_push() = runBlocking {
        sembrar("e1")
        bd.operaciones.insert(operacion("a", 10, entidad = "e1"))

        ciclo()

        assertEquals(listOf("push", "pull"), orden)
    }

    @Test
    fun sin_operaciones_igual_se_descarga() = runBlocking {
        assertEquals(ResultadoCiclo.Terminado(null), ciclo())
        assertEquals(listOf("pull"), orden)
    }

    @Test
    fun el_pull_no_pisa_un_registro_con_operaciones_pendientes_y_si_actualiza_los_demas() = runBlocking {
        sembrar("e1", version = 1)
        sembrar("e2", version = 1)
        bd.operaciones.insert(operacion("a", 10, entidad = "e1", estado = FALLIDA))
        respuestaPull = ResultadoPull.Datos(
            listOf(
                PruebaRemota("e1", "Del servidor", "x", 9, 1, version = 7),
                PruebaRemota("e2", "Del servidor", "x", 9, 1, version = 7)
            )
        )

        ciclo()

        assertEquals("Título e1", bd.pruebas.findById("e1")!!.titulo)
        assertEquals("Del servidor", bd.pruebas.findById("e2")!!.titulo)
    }

    @Test
    fun el_pull_vencido_por_sesion_expira_la_sesion_y_pausa() = runBlocking {
        respuestaPull = ResultadoPull.SesionExpirada

        assertEquals(ResultadoCiclo.SesionExpirada, ciclo())
        verify { gestor.expirar() }
    }

    @Test
    fun el_pull_sin_servidor_lo_reporta_al_monitor() = runBlocking {
        respuestaPull = ResultadoPull.SinServidor

        assertEquals(ResultadoCiclo.SinServidor, ciclo())
        verify { monitor.reportarServidorNoAlcanzable() }
    }

    @Test
    fun un_pull_fallido_se_informa_y_pide_otro_intento_mientras_no_termine_la_descarga_inicial() = runBlocking {
        respuestaPull = ResultadoPull.Fallo("Error del servidor (500)")

        assertEquals(ResultadoCiclo.Terminado(ESPERA_DESCARGA, "Error del servidor (500)"), ciclo())
    }

    @Test
    fun un_pull_fallido_con_la_descarga_inicial_hecha_no_programa_reintento() = runBlocking {
        descargaInicialCompletada()
        respuestaPull = ResultadoPull.Fallo("Error del servidor (500)")

        assertEquals(ResultadoCiclo.Terminado(null, "Error del servidor (500)"), ciclo())
    }

    @Test
    fun el_siguiente_ciclo_se_programa_para_la_reintentable_mas_proxima() = runBlocking {
        descargaInicialCompletada()
        sembrar("e1")
        sembrar("e2")
        bd.operaciones.insert(operacion("a", 10, entidad = "e1").copy(attemptCount = 2))
        bd.operaciones.insert(operacion("b", 10, entidad = "e2"))
        respuestaEnvio = { ResultadoEnvio.Temporal("503") }

        // a: intento 3 → 40 s
        // b: intento 1 → 10 s
        assertEquals(ResultadoCiclo.Terminado(10_000L), ciclo())
    }

    @Test
    fun los_registros_borrados_en_el_servidor_se_quitan_y_se_avisa_con_su_titulo() = runBlocking {
        sembrar("e1", version = 1)
        respuestaPull = ResultadoPull.Datos(emptyList())

        ciclo()

        assertNull(bd.pruebas.findById("e1"))
        verify { notificaciones.eliminadosEnServidor(listOf("Título e1")) }
    }

    // --- Avisos ---

    @Test
    fun el_resultado_del_ciclo_se_avisa_una_sola_vez_con_confirmadas_y_problemas() = runBlocking {
        sembrar("e1")
        sembrar("e2")
        sembrar("e3")
        bd.operaciones.insert(operacion("ok1", 10, entidad = "e1"))
        bd.operaciones.insert(operacion("mala", 20, entidad = "e2"))
        bd.operaciones.insert(operacion("ok2", 30, entidad = "e3"))
        respuestaEnvio = { if (it == "mala") ResultadoEnvio.Definitivo("422") else ResultadoEnvio.Confirmada(null) }
        val problemas = slot<List<OperacionConProblema>>()

        ciclo()

        verify(exactly = 1) { notificaciones.operacionesFinalizadas(2, capture(problemas)) }
        val problema = problemas.captured.single()
        assertEquals("mala", problema.operationId)
        assertEquals(FALLIDA, problema.status)
        assertEquals("Título e2", problema.titulo)
    }

    @Test
    fun un_conflicto_avisa_con_su_causa() = runBlocking {
        sembrar("e1")
        bd.operaciones.insert(operacion("a", 10, entidad = "e1"))
        respuestaEnvio = { ResultadoEnvio.Conflicto("404", CausaConflicto.INEXISTENTE_EN_SERVIDOR) }
        val problemas = slot<List<OperacionConProblema>>()

        ciclo()

        verify { notificaciones.operacionesFinalizadas(0, capture(problemas)) }
        assertEquals(CONFLICTO, problemas.captured.single().status)
        assertEquals(CausaConflicto.INEXISTENTE_EN_SERVIDOR, problemas.captured.single().conflictCause)
    }

    @Test
    fun aunque_el_ciclo_se_corte_a_la_mitad_se_avisa_de_lo_que_ya_termino() = runBlocking {
        sembrar("e1")
        sembrar("e2")
        bd.operaciones.insert(operacion("ok", 10, entidad = "e1"))
        bd.operaciones.insert(operacion("corta", 20, entidad = "e2"))
        respuestaEnvio = { if (it == "ok") ResultadoEnvio.Confirmada(null) else ResultadoEnvio.Incierto }

        ciclo()

        verify { notificaciones.operacionesFinalizadas(1, emptyList()) }
    }

    // --- Exclusión mutua ---

    @Test
    fun mientras_dura_pausado_ningun_ciclo_empieza() = runBlocking {
        sembrar("e1")
        bd.operaciones.insert(operacion("a", 10, entidad = "e1"))
        preparar()
        val dentro = CompletableDeferred<Unit>()
        val salir = CompletableDeferred<Unit>()

        val pausa = launch { motor.pausado { dentro.complete(Unit); salir.await() } }
        dentro.await()
        val cicloEnEspera = async { motor.ejecutarCiclo() }
        delay(150.milliseconds)

        assertTrue(cicloEnEspera.isActive)
        assertTrue("el ciclo no envió nada mientras duró la pausa", enviadas.isEmpty())

        salir.complete(Unit)
        pausa.join()
        cicloEnEspera.await()
        assertEquals(listOf("a"), enviadas.map { it.first })
    }

    @Test
    fun dos_ciclos_simultaneos_no_envian_la_misma_operacion_dos_veces() = runBlocking {
        sembrar("e1")
        bd.operaciones.insert(operacion("a", 10, entidad = "e1"))
        preparar()

        val ciclos = List(4) { async { motor.ejecutarCiclo() } }
        ciclos.forEach { it.await() }

        assertEquals(listOf("a"), enviadas.map { it.first })
    }

    // --- Refresco manual ---

    @Test
    fun refrescar_sin_sesion_lo_dice_y_no_va_a_la_red() = runBlocking {
        sesion.value = EstadoSesion.SinSesion
        preparar()

        assertEquals(ResultadoRefresco.SinSesion, motor.refrescar())
        assertTrue(orden.isEmpty())
    }

    @Test
    fun refrescar_sin_servidor_lo_dice() = runBlocking {
        preparar()
        coEvery { monitor.confirmarAlcanzabilidad() } returns false

        assertEquals(ResultadoRefresco.SinServidor, motor.refrescar())
        assertTrue(orden.isEmpty())
    }

    @Test
    fun refrescar_solo_descarga_y_no_envia_la_cola() = runBlocking {
        sembrar("e1")
        sembrar("e2", version = 1)
        bd.operaciones.insert(operacion("a", 10, entidad = "e1"))
        respuestaPull = ResultadoPull.Datos(listOf(PruebaRemota("e2", "Nuevo", "x", 1, 1, version = 2)))
        preparar()

        val resultado = motor.refrescar()

        assertEquals(ResultadoRefresco.Actualizada(emptyList()), resultado)
        assertEquals(listOf("pull"), orden)
        assertEquals(PENDIENTE, estado("a"))
        assertEquals("Nuevo", bd.pruebas.findById("e2")!!.titulo)
    }

    @Test
    fun refrescar_devuelve_los_titulos_de_los_borrados_en_el_servidor() = runBlocking {
        sembrar("e1", version = 1)
        preparar()

        assertEquals(ResultadoRefresco.Actualizada(listOf("Título e1")), motor.refrescar())
    }

    @Test
    fun refrescar_traduce_el_fallo_de_la_descarga() = runBlocking {
        preparar()
        respuestaPull = ResultadoPull.Fallo("500")
        assertEquals(ResultadoRefresco.ErrorDescarga, motor.refrescar())

        respuestaPull = ResultadoPull.SinServidor
        assertEquals(ResultadoRefresco.SinServidor, motor.refrescar())
        verify { monitor.reportarServidorNoAlcanzable() }

        respuestaPull = ResultadoPull.SesionExpirada
        assertEquals(ResultadoRefresco.SinSesion, motor.refrescar())
        verify { gestor.expirar() }
    }

    private companion object {
        const val ESPERA_DESCARGA = 60_000L
        const val MOTIVO_PULL = "sin pull"
    }
}
