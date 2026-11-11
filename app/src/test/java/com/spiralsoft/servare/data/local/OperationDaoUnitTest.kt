package com.spiralsoft.servare.data.local

import com.spiralsoft.servare.domain.model.CausaConflicto
import com.spiralsoft.servare.domain.model.EstadoOperacion.BLOQUEADA
import com.spiralsoft.servare.domain.model.EstadoOperacion.COMPLETADA
import com.spiralsoft.servare.domain.model.EstadoOperacion.CONFLICTO
import com.spiralsoft.servare.domain.model.EstadoOperacion.DESCARTADA
import com.spiralsoft.servare.domain.model.EstadoOperacion.FALLIDA
import com.spiralsoft.servare.domain.model.EstadoOperacion.PENDIENTE
import com.spiralsoft.servare.domain.model.EstadoOperacion.PROCESANDO
import com.spiralsoft.servare.domain.model.EstadoOperacion.REINTENTABLE
import com.spiralsoft.servare.domain.model.TipoEntidad
import com.spiralsoft.servare.domain.model.TipoOperacion
import com.spiralsoft.servare.testutil.BaseDeDatosRegla
import com.spiralsoft.servare.testutil.operacion
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// La máquina de estados de la cola vive en SQL: se prueba contra Room real
@RunWith(RobolectricTestRunner::class)
class OperationDaoUnitTest {
    @get:Rule val bd = BaseDeDatosRegla()
    private val dao get() = bd.operaciones

    private suspend fun estado(id: String) = dao.buscar(id)!!.status

    // --- Selección ---

    @Test
    fun la_siguiente_elegible_es_la_pendiente_mas_antigua() = runBlocking {
        dao.insert(operacion("b", createdAt = 20, entidad = "e2"))
        dao.insert(operacion("a", createdAt = 10, entidad = "e1"))
        assertEquals("a", dao.siguienteElegible()!!.operationId)
    }

    @Test
    fun sin_pendientes_no_hay_siguiente() = runBlocking {
        dao.insert(operacion("a", 10, estado = COMPLETADA))
        dao.insert(operacion("b", 20, estado = FALLIDA))
        assertNull(dao.siguienteElegible())
    }

    @Test
    fun una_operacion_espera_a_las_anteriores_de_su_entidad_aunque_esten_reintentables() = runBlocking {
        dao.insert(operacion("a", 10, estado = REINTENTABLE, nextAttemptAt = 999))
        dao.insert(operacion("b", 20))
        assertNull(dao.siguienteElegible())
    }

    @Test
    fun las_terminadas_de_la_misma_entidad_no_bloquean() = runBlocking {
        dao.insert(operacion("a", 10, estado = COMPLETADA))
        dao.insert(operacion("b", 15, estado = DESCARTADA))
        dao.insert(operacion("c", 20))
        assertEquals("c", dao.siguienteElegible()!!.operationId)
    }

    @Test
    fun otra_entidad_no_bloquea() = runBlocking {
        dao.insert(operacion("a", 10, estado = FALLIDA, entidad = "e1"))
        dao.insert(operacion("b", 20, entidad = "e2"))
        assertEquals("b", dao.siguienteElegible()!!.operationId)
    }

    @Test
    fun la_misma_clave_en_otro_tipo_de_entidad_no_bloquea() = runBlocking {
        dao.insert(operacion("a", 10, estado = FALLIDA, tipoEntidad = TipoEntidad.FICHA))
        dao.insert(operacion("b", 20, tipoEntidad = TipoEntidad.PRUEBA))
        assertEquals("b", dao.siguienteElegible()!!.operationId)
    }

    @Test
    fun con_dependsOn_espera_a_que_la_dependencia_este_completada() = runBlocking {
        dao.insert(operacion("padre", 10, entidad = "p", estado = PROCESANDO))
        dao.insert(operacion("hija", 5, entidad = "h", dependsOn = "padre"))
        assertNull(dao.siguienteElegible())

        dao.marcarCompletada("padre", ahora = 100)
        assertEquals("hija", dao.siguienteElegible()!!.operationId)
    }

    @Test
    fun una_dependencia_fallida_deja_la_dependiente_sin_elegir() = runBlocking {
        dao.insert(operacion("padre", 10, entidad = "p", estado = FALLIDA))
        dao.insert(operacion("hija", 20, entidad = "h", dependsOn = "padre"))
        assertNull(dao.siguienteElegible())
    }

    // --- Recuperación y reintentos ---

    @Test
    fun las_huerfanas_vuelven_a_pendiente_como_inciertas_y_sin_sumar_intento() = runBlocking {
        dao.insert(operacion("a", 10, estado = PROCESANDO))
        dao.insert(operacion("b", 20, estado = FALLIDA, entidad = "e2"))

        assertEquals(1, dao.recuperarHuerfanas())

        val a = dao.buscar("a")!!
        assertEquals(PENDIENTE, a.status)
        assertTrue(a.uncertain)
        assertEquals(0, a.attemptCount)
        assertEquals(FALLIDA, estado("b"))
    }

    @Test
    fun solo_se_liberan_las_reintentables_vencidas() = runBlocking {
        dao.insert(operacion("vencida", 10, estado = REINTENTABLE, nextAttemptAt = 100, entidad = "e1"))
        dao.insert(operacion("justo", 10, estado = REINTENTABLE, nextAttemptAt = 200, entidad = "e2"))
        dao.insert(operacion("futura", 10, estado = REINTENTABLE, nextAttemptAt = 201, entidad = "e3"))

        assertEquals(2, dao.liberarReintentables(ahora = 200))

        assertEquals(PENDIENTE, estado("vencida"))
        assertEquals(PENDIENTE, estado("justo"))
        assertEquals(REINTENTABLE, estado("futura"))
    }

    @Test
    fun el_proximo_intento_es_el_menor_de_las_reintentables() = runBlocking {
        assertNull(dao.proximoIntento())
        dao.insert(operacion("a", 10, estado = REINTENTABLE, nextAttemptAt = 500, entidad = "e1"))
        dao.insert(operacion("b", 10, estado = REINTENTABLE, nextAttemptAt = 300, entidad = "e2"))
        dao.insert(operacion("c", 10, estado = FALLIDA, nextAttemptAt = 100, entidad = "e3"))
        assertEquals(300L, dao.proximoIntento())
    }

    @Test
    fun marcar_procesando_registra_el_instante_del_intento() = runBlocking {
        dao.insert(operacion("a", 10))
        dao.marcarProcesando("a", ahora = 777)
        val a = dao.buscar("a")!!
        assertEquals(PROCESANDO, a.status)
        assertEquals(777L, a.lastAttemptAt)
    }

    @Test
    fun marcar_incierta_vuelve_a_pendiente_sin_sumar_intento() = runBlocking {
        dao.insert(operacion("a", 10, estado = PROCESANDO))
        dao.marcarIncierta("a")
        val a = dao.buscar("a")!!
        assertEquals(PENDIENTE, a.status)
        assertTrue(a.uncertain)
        assertEquals(0, a.attemptCount)
    }

    @Test
    fun marcar_reintentable_guarda_intentos_proximo_y_error() = runBlocking {
        dao.insert(operacion("a", 10, estado = PROCESANDO))
        dao.marcarReintentable("a", intentos = 3, proximo = 4000, error = "Tiempo agotado")
        val a = dao.buscar("a")!!
        assertEquals(REINTENTABLE, a.status)
        assertEquals(3, a.attemptCount)
        assertEquals(4000L, a.nextAttemptAt)
        assertEquals("Tiempo agotado", a.lastError)
    }

    @Test
    fun marcar_completada_limpia_el_error_y_la_incertidumbre() = runBlocking {
        dao.insert(operacion("a", 10, estado = PROCESANDO, uncertain = true))
        dao.marcarReintentable("a", 1, 5, "x")
        dao.marcarCompletada("a", ahora = 900)
        val a = dao.buscar("a")!!
        assertEquals(COMPLETADA, a.status)
        assertFalse(a.uncertain)
        assertNull(a.lastError)
        assertEquals(900L, a.finishedAt)
    }

    @Test
    fun marcar_pendiente_devuelve_la_operacion_a_la_cola_sin_tocar_nada_mas() = runBlocking {
        dao.insert(operacion("a", 10, estado = PROCESANDO))
        dao.marcarPendiente("a")
        val a = dao.buscar("a")!!
        assertEquals(PENDIENTE, a.status)
        assertEquals(0, a.attemptCount)
        assertFalse(a.uncertain)
    }

    // --- Fallos y conflictos ---

    @Test
    fun marcar_fallida_guarda_el_motivo_y_quita_el_proximo_intento() = runBlocking {
        dao.insert(operacion("a", 10, estado = REINTENTABLE, nextAttemptAt = 50))
        dao.marcarFallida("a", intentos = 13, error = "Se agotaron los intentos")
        val a = dao.buscar("a")!!
        assertEquals(FALLIDA, a.status)
        assertEquals(13, a.attemptCount)
        assertNull(a.nextAttemptAt)
        assertEquals("Se agotaron los intentos", a.lastError)
    }

    @Test
    fun marcar_conflicto_guarda_causa_y_registro_vigente() = runBlocking {
        dao.insert(operacion("a", 10, estado = PROCESANDO))
        dao.marcarConflicto("a", "Versión distinta", CausaConflicto.VERSION_DISTINTA, """{"v":2}""")
        val a = dao.buscar("a")!!
        assertEquals(CONFLICTO, a.status)
        assertEquals(CausaConflicto.VERSION_DISTINTA, a.conflictCause)
        assertEquals("""{"v":2}""", a.serverSnapshot)
        assertEquals("Versión distinta", a.lastError)
    }

    @Test
    fun bloquear_dependientes_alcanza_a_las_posteriores_y_a_las_que_dependen_pero_no_a_otras() = runBlocking {
        dao.insert(operacion("origen", 10, estado = CONFLICTO))
        dao.insert(operacion("posterior", 20, estado = PENDIENTE))
        dao.insert(operacion("posterior_reintentable", 30, estado = REINTENTABLE, nextAttemptAt = 5))
        dao.insert(operacion("anterior_terminada", 5, estado = COMPLETADA))
        dao.insert(operacion("dependiente", 15, entidad = "otra", dependsOn = "origen"))
        dao.insert(operacion("ajena", 40, entidad = "otra2"))
        dao.insert(operacion("posterior_fallida", 50, estado = FALLIDA))

        dao.bloquearDependientes("origen", TipoEntidad.PRUEBA, "e1", createdAt = 10)

        assertEquals(BLOQUEADA, estado("posterior"))
        assertEquals(BLOQUEADA, estado("posterior_reintentable"))
        assertEquals(BLOQUEADA, estado("dependiente"))
        assertEquals(CONFLICTO, estado("origen"))
        assertEquals(COMPLETADA, estado("anterior_terminada"))
        assertEquals(PENDIENTE, estado("ajena"))
        assertEquals(FALLIDA, estado("posterior_fallida"))
    }

    @Test
    fun encadenar_base_version_actualiza_las_posteriores_que_no_son_crear() = runBlocking {
        dao.insert(operacion("crear", 10, tipo = TipoOperacion.CREAR, estado = COMPLETADA))
        dao.insert(operacion("edita1", 20, baseVersion = 1))
        dao.insert(operacion("edita2", 30, estado = BLOQUEADA, baseVersion = 1))
        dao.insert(operacion("elimina", 40, tipo = TipoOperacion.ELIMINAR, estado = REINTENTABLE, baseVersion = 1))
        dao.insert(operacion("crear_posterior", 50, tipo = TipoOperacion.CREAR, baseVersion = null))
        dao.insert(operacion("vieja", 5, estado = PENDIENTE, baseVersion = 1, entidad = "e1"))
        dao.insert(operacion("otra_entidad", 60, entidad = "e2", baseVersion = 1))
        dao.insert(operacion("terminada", 70, estado = DESCARTADA, baseVersion = 1))

        dao.encadenarBaseVersion(TipoEntidad.PRUEBA, "e1", createdAt = 10, version = 4)

        assertEquals(4, dao.buscar("edita1")!!.baseVersion)
        assertEquals(4, dao.buscar("edita2")!!.baseVersion)
        assertEquals(4, dao.buscar("elimina")!!.baseVersion)
        assertNull(dao.buscar("crear_posterior")!!.baseVersion)
        assertEquals(1, dao.buscar("vieja")!!.baseVersion)
        assertEquals(1, dao.buscar("otra_entidad")!!.baseVersion)
        assertEquals(1, dao.buscar("terminada")!!.baseVersion)
    }

    // --- Acciones del usuario ---

    @Test
    fun reintentar_solo_actua_sobre_fallidas_y_conserva_el_operationId() = runBlocking {
        dao.insert(operacion("f", 10, estado = FALLIDA, entidad = "e1"))
        dao.marcarFallida("f", intentos = 13, error = "x")
        dao.insert(operacion("c", 10, estado = CONFLICTO, entidad = "e2"))

        assertEquals(1, dao.reintentar("f"))
        assertEquals(0, dao.reintentar("c"))

        val f = dao.buscar("f")!!
        assertEquals(PENDIENTE, f.status)
        assertEquals(0, f.attemptCount)
        assertNull(f.lastError)
        assertEquals(CONFLICTO, estado("c"))
    }

    @Test
    fun reintentar_dos_veces_la_segunda_no_encuentra_nada_que_cambiar() = runBlocking {
        dao.insert(operacion("f", 10, estado = FALLIDA))
        assertEquals(1, dao.reintentar("f"))
        assertEquals(0, dao.reintentar("f"))
    }

    @Test
    fun conservar_la_mia_sobre_una_actualizacion_toma_la_version_del_servidor() = runBlocking {
        dao.insert(operacion("a", 10, estado = PROCESANDO, baseVersion = 1))
        dao.marcarConflicto("a", "x", CausaConflicto.VERSION_DISTINTA, "{}")

        assertEquals(1, dao.reanudarSobreVersion("a", version = 7))

        val a = dao.buscar("a")!!
        assertEquals(PENDIENTE, a.status)
        assertEquals(7, a.baseVersion)
        assertEquals(TipoOperacion.ACTUALIZAR, a.type)
        assertNull(a.conflictCause)
        assertNull(a.serverSnapshot)
    }

    @Test
    fun conservar_la_mia_sobre_una_creacion_la_convierte_en_actualizacion() = runBlocking {
        dao.insert(operacion("a", 10, tipo = TipoOperacion.CREAR, estado = CONFLICTO))
        assertEquals(1, dao.reanudarSobreVersion("a", version = 3))
        val a = dao.buscar("a")!!
        assertEquals(TipoOperacion.ACTUALIZAR, a.type)
        assertEquals(3, a.baseVersion)
    }

    @Test
    fun conservar_la_mia_solo_actua_sobre_conflictos() = runBlocking {
        dao.insert(operacion("a", 10, estado = FALLIDA))
        assertEquals(0, dao.reanudarSobreVersion("a", version = 3))
        assertEquals(FALLIDA, estado("a"))
    }

    @Test
    fun descartar_solo_actua_sobre_fallidas_y_conflictos() = runBlocking {
        dao.insert(operacion("f", 10, estado = FALLIDA, entidad = "e1"))
        dao.insert(operacion("c", 10, estado = CONFLICTO, entidad = "e2"))
        dao.insert(operacion("p", 10, estado = PENDIENTE, entidad = "e3"))
        dao.insert(operacion("b", 10, estado = BLOQUEADA, entidad = "e4"))

        assertEquals(1, dao.descartar("f", ahora = 55))
        assertEquals(1, dao.descartar("c", ahora = 55))
        assertEquals(0, dao.descartar("p", ahora = 55))
        assertEquals(0, dao.descartar("b", ahora = 55))

        assertEquals(DESCARTADA, estado("f"))
        assertEquals(55L, dao.buscar("f")!!.finishedAt)
        assertEquals(PENDIENTE, estado("p"))
        assertEquals(BLOQUEADA, estado("b"))
    }

    @Test
    fun liberar_bloqueadas_devuelve_a_la_cola_solo_las_de_esa_operacion() = runBlocking {
        dao.insert(operacion("origen", 10, estado = CONFLICTO))
        dao.insert(operacion("posterior", 20, estado = BLOQUEADA))
        dao.insert(operacion("dependiente", 5, entidad = "otra", estado = BLOQUEADA, dependsOn = "origen"))
        dao.insert(operacion("anterior", 5, estado = BLOQUEADA))
        dao.insert(operacion("ajena", 20, entidad = "otra2", estado = BLOQUEADA))

        dao.liberarBloqueadas("origen", TipoEntidad.PRUEBA, "e1", createdAt = 10)

        assertEquals(PENDIENTE, estado("posterior"))
        assertEquals(PENDIENTE, estado("dependiente"))
        assertEquals(BLOQUEADA, estado("anterior"))
        assertEquals(BLOQUEADA, estado("ajena"))
        assertEquals(CONFLICTO, estado("origen"))
    }

    @Test
    fun descartar_posteriores_incluye_pendientes_reintentables_y_bloqueadas_pero_no_las_fallidas() = runBlocking {
        dao.insert(operacion("origen", 10, estado = CONFLICTO))
        dao.insert(operacion("bloqueada", 20, estado = BLOQUEADA))
        dao.insert(operacion("pendiente", 30, estado = PENDIENTE))
        dao.insert(operacion("reintentable", 40, estado = REINTENTABLE, nextAttemptAt = 1))
        dao.insert(operacion("fallida", 50, estado = FALLIDA))
        dao.insert(operacion("dependiente", 5, entidad = "otra", estado = BLOQUEADA, dependsOn = "origen"))
        dao.insert(operacion("ajena", 60, entidad = "otra2"))

        dao.descartarPosteriores("origen", TipoEntidad.PRUEBA, "e1", createdAt = 10, ahora = 99)

        assertEquals(DESCARTADA, estado("bloqueada"))
        assertEquals(DESCARTADA, estado("pendiente"))
        assertEquals(DESCARTADA, estado("reintentable"))
        assertEquals(DESCARTADA, estado("dependiente"))
        assertEquals(99L, dao.buscar("pendiente")!!.finishedAt)
        assertEquals(FALLIDA, estado("fallida"))
        assertEquals(PENDIENTE, estado("ajena"))
        assertEquals(CONFLICTO, estado("origen"))
    }

    // --- Retención ---

    @Test
    fun se_purgan_completadas_y_descartadas_vencidas_pero_no_las_recientes() = runBlocking {
        dao.insert(operacion("vieja_c", 1, estado = COMPLETADA, finishedAt = 100, entidad = "e1"))
        dao.insert(operacion("vieja_d", 1, estado = DESCARTADA, finishedAt = 100, entidad = "e2"))
        dao.insert(operacion("reciente", 1, estado = COMPLETADA, finishedAt = 500, entidad = "e3"))

        assertEquals(2, dao.purgarTerminadasAntesDe(limite = 300))

        assertNull(dao.buscar("vieja_c"))
        assertNull(dao.buscar("vieja_d"))
        assertEquals(COMPLETADA, estado("reciente"))
    }

    @Test
    fun el_limite_de_la_purga_es_estricto() = runBlocking {
        dao.insert(operacion("justo", 1, estado = COMPLETADA, finishedAt = 300))
        assertEquals(0, dao.purgarTerminadasAntesDe(limite = 300))
        assertEquals(COMPLETADA, estado("justo"))
    }

    @Test
    fun fallidas_conflictos_y_bloqueadas_nunca_se_purgan() = runBlocking {
        dao.insert(operacion("f", 1, estado = FALLIDA, finishedAt = 1, entidad = "e1"))
        dao.insert(operacion("c", 1, estado = CONFLICTO, finishedAt = 1, entidad = "e2"))
        dao.insert(operacion("b", 1, estado = BLOQUEADA, finishedAt = 1, entidad = "e3"))
        dao.insert(operacion("p", 1, estado = PENDIENTE, finishedAt = 1, entidad = "e4"))

        assertEquals(0, dao.purgarTerminadasAntesDe(limite = Long.MAX_VALUE))
        assertEquals(4, dao.contarSinTerminar())
    }

    @Test
    fun una_completada_de_la_que_depende_una_operacion_viva_se_conserva() = runBlocking {
        dao.insert(operacion("padre", 1, estado = COMPLETADA, finishedAt = 1, entidad = "p"))
        dao.insert(operacion("hija", 2, estado = PENDIENTE, dependsOn = "padre", entidad = "h"))

        assertEquals(0, dao.purgarTerminadasAntesDe(limite = 1000))
        assertEquals(COMPLETADA, estado("padre"))
    }

    @Test
    fun cuando_la_dependiente_termina_la_dependencia_ya_se_puede_purgar() = runBlocking {
        dao.insert(operacion("padre", 1, estado = COMPLETADA, finishedAt = 1, entidad = "p"))
        dao.insert(operacion("hija", 2, estado = COMPLETADA, dependsOn = "padre", finishedAt = 1, entidad = "h"))

        assertEquals(2, dao.purgarTerminadasAntesDe(limite = 1000))
    }

    // --- Protección del pull y contadores ---

    @Test
    fun las_entidades_con_operaciones_sin_terminar_incluyen_problemas_y_excluyen_terminadas() = runBlocking {
        dao.insert(operacion("a", 1, estado = PENDIENTE, entidad = "pendiente"))
        dao.insert(operacion("b", 1, estado = FALLIDA, entidad = "fallida"))
        dao.insert(operacion("c", 1, estado = CONFLICTO, entidad = "conflicto"))
        dao.insert(operacion("d", 1, estado = BLOQUEADA, entidad = "bloqueada"))
        dao.insert(operacion("e", 1, estado = PROCESANDO, entidad = "procesando"))
        dao.insert(operacion("f", 1, estado = COMPLETADA, entidad = "completada"))
        dao.insert(operacion("g", 1, estado = DESCARTADA, entidad = "descartada"))
        dao.insert(operacion("h", 1, estado = PENDIENTE, entidad = "ficha", tipoEntidad = TipoEntidad.FICHA))

        val protegidas = dao.entidadesConOperacionesSinTerminar(TipoEntidad.PRUEBA)

        assertEquals(
            setOf("pendiente", "fallida", "conflicto", "bloqueada", "procesando"),
            protegidas.toSet(),
        )
    }

    @Test
    fun una_entidad_con_varias_operaciones_sin_terminar_aparece_una_vez() = runBlocking {
        dao.insert(operacion("a", 1))
        dao.insert(operacion("b", 2))
        assertEquals(listOf("e1"), dao.entidadesConOperacionesSinTerminar(TipoEntidad.PRUEBA))
    }

    @Test
    fun contar_sin_terminar_ignora_completadas_y_descartadas() = runBlocking {
        assertEquals(0, dao.contarSinTerminar())
        dao.insert(operacion("a", 1, estado = COMPLETADA, entidad = "e1"))
        dao.insert(operacion("b", 1, estado = DESCARTADA, entidad = "e2"))
        dao.insert(operacion("c", 1, estado = FALLIDA, entidad = "e3"))
        assertEquals(1, dao.contarSinTerminar())
    }

    @Test
    fun buscar_agrupable_solo_ve_pendientes_no_inciertas_y_toma_la_mas_reciente() = runBlocking {
        dao.insert(operacion("vieja", 10))
        dao.insert(operacion("nueva", 20))
        dao.insert(operacion("incierta", 30, uncertain = true))
        dao.insert(operacion("fallida", 40, estado = FALLIDA))

        assertEquals("nueva", dao.buscarAgrupable(TipoEntidad.PRUEBA, "e1")!!.operationId)
        assertNull(dao.buscarAgrupable(TipoEntidad.PRUEBA, "otra"))
        assertNull(dao.buscarAgrupable(TipoEntidad.FICHA, "e1"))
    }
}
