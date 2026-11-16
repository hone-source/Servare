package com.spiralsoft.servare.data.ubicacion

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.location.LocationManagerCompat
import androidx.core.util.Consumer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executor
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import com.spiralsoft.servare.domain.model.ResultadoUbicacion
import kotlin.time.Duration.Companion.milliseconds

// Posición actual del dispositivo, una sola lectura. Usa el GPS del sistema, sin servicios de Google
@Singleton
class FuenteUbicacion @Inject constructor(
    @ApplicationContext private val context: Context
) {
    @SuppressLint("MissingPermission")
    suspend fun actual(): ResultadoUbicacion {
        val precisa = concedido(Manifest.permission.ACCESS_FINE_LOCATION)
        if (!precisa && !concedido(Manifest.permission.ACCESS_COARSE_LOCATION)) return ResultadoUbicacion.SinPermiso

        val manager = context.getSystemService(LocationManager::class.java)
        // Con la ubicación aproximada el sistema solo deja usar la red
        val proveedor = when {
            precisa && manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> return ResultadoUbicacion.UbicacionApagada
        }

        val posicion = withTimeoutOrNull(TIEMPO_MAXIMO_MS.milliseconds) {
            suspendCancellableCoroutine<Location?> { cont ->
                val cancelacion = CancellationSignal()
                cont.invokeOnCancellation { cancelacion.cancel() }
                LocationManagerCompat.getCurrentLocation(
                    manager, proveedor, cancelacion, { it.run() }, { cont.resume(it) }
                )
            }
        } ?: return ResultadoUbicacion.SinPosicion

        return ResultadoUbicacion.Obtenida(
            posicion.latitude, posicion.longitude,
            posicion.takeIf { it.hasAccuracy() }?.accuracy
        )
    }

    private fun concedido(permiso: String) =
        context.checkSelfPermission(permiso) == PackageManager.PERMISSION_GRANTED

    private companion object {
        // Un primer fix del GPS en frío puede tardar, pasado esto se avisa en vez de dejar esperando
        const val TIEMPO_MAXIMO_MS = 20_000L
    }
}
