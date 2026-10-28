package com.spiralsoft.servare.data.sesion

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import com.spiralsoft.servare.data.sync.SyncScheduler
import com.spiralsoft.servare.domain.model.EstadoSesion

// Gestor de sesión: único dueño de la sesión del usuario. Con sesión previa la app abre
// y opera sin conexión, si el token expiró, sigue funcionando con lo guardado y la cola se pausa
@Singleton
class GestorSesion @Inject constructor(
    private val almacen: AlmacenSesion,
    private val syncScheduler: SyncScheduler,
    private val clock: Clock
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _estado = MutableStateFlow<EstadoSesion>(EstadoSesion.Cargando)
    val estado: StateFlow<EstadoSesion> = _estado.asStateFlow()

    // Copia en memoria del token: lo lee el interceptor desde el hilo de red, sin esperar al almacén
    @Volatile
    private var token: String? = null

    init {
        scope.launch {
            val guardada = almacen.leer()
            when {
                guardada == null -> _estado.value = EstadoSesion.SinSesion
                // Expiró sin conexión: no hace falta preguntarle al servidor para saberlo
                guardada.expiraEn <= clock.millis() -> _estado.value = EstadoSesion.Expirada(guardada.correo)
                else -> abrir(guardada.token, guardada.correo)
            }
        }
    }

    // El token para enviar con las peticiones de datos. null sin sesión o con la sesión expirada
    fun token(): String? = token

    // false si no se pudo guardar en el dispositivo: el estado no cambia
    suspend fun guardar(token: String, correo: String, expiraEn: Long): Boolean {
        if (!almacen.guardar(SesionGuardada(token, correo, expiraEn))) return false
        abrir(token, correo)
        return true
    }

    // El servidor rechazó el token (401/403). No es culpa de ninguna operación: los datos y la cola se
    // conservan, el motor deja de sincronizar y la interfaz pide iniciar sesión
    fun expirar() {
        _estado.update { if (it is EstadoSesion.ConSesion) EstadoSesion.Expirada(it.correo) else it }
        if (_estado.value is EstadoSesion.Expirada) token = null
    }

    // false si no se pudo borrar del dispositivo: la sesión sigue abierta. No toca los datos locales
    suspend fun cerrar(): Boolean {
        if (!almacen.borrar()) return false
        token = null
        _estado.value = EstadoSesion.SinSesion
        return true
    }

    // Sin sesión el motor no sincroniza: al abrirla se pide un ciclo para enviar lo pendiente y descargar
    private fun abrir(token: String, correo: String) {
        this.token = token
        _estado.value = EstadoSesion.ConSesion(correo)
        syncScheduler.requestSync()
    }
}
