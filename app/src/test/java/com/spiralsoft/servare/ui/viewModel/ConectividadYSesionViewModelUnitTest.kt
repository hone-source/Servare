package com.spiralsoft.servare.ui.viewModel

import com.spiralsoft.servare.domain.model.AvisoConectividad
import com.spiralsoft.servare.domain.model.EstadoConectividad
import com.spiralsoft.servare.domain.model.EstadoSesion
import com.spiralsoft.servare.testutil.ReglaDispatcherPrincipal
import com.spiralsoft.servare.testutil.SesionRepositoryFalso
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import com.spiralsoft.servare.notification.ServicioNotificacionEstado

// Solo exponen un estado: la navegación y el aviso de conexión deciden con él
class ConectividadYSesionViewModelUnitTest {
    @get:Rule val principal = ReglaDispatcherPrincipal()

    @Test
    fun el_aviso_de_conectividad_es_el_del_servicio_en_cada_momento() {
        val aviso = MutableStateFlow(AvisoConectividad(EstadoConectividad.SIN_RED, "Sin conexión", "x"))
        val servicio = mockk<ServicioNotificacionEstado>().also { every { it.conectividad } returns aviso }
        val vm = ConectividadViewModel(servicio)

        assertEquals("Sin conexión", vm.aviso.value.titulo)
        aviso.value = AvisoConectividad(EstadoConectividad.CONECTADO, "Conectado", "y")
        assertEquals(EstadoConectividad.CONECTADO, vm.aviso.value.estado)
    }

    @Test
    fun el_estado_de_sesion_sigue_al_repositorio() {
        val repo = SesionRepositoryFalso()
        val vm = SesionViewModel(repo)

        assertEquals(EstadoSesion.SinSesion, vm.estado.value)
        repo.estado.value = EstadoSesion.Expirada("a@b.c")
        assertEquals(EstadoSesion.Expirada("a@b.c"), vm.estado.value)
    }
}
