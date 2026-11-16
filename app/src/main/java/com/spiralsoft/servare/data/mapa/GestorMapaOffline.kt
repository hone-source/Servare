package com.spiralsoft.servare.data.mapa

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import org.maplibre.android.MapLibre
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import com.spiralsoft.servare.domain.model.EstadoRegionMapa
import com.spiralsoft.servare.domain.model.RegionMapa

// El servidor de teselas no es el backend de Servare: su alcance no lo cubre el health check
class ErrorDescargaMapa(message: String?) : Exception(message)

// Guarda regiones del mapa en el almacén de MapLibre para verlas sin red.
// Cada región se identifica por su nombre, guardado en los metadatos
@Singleton
class GestorMapaOffline @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // OfflineManager exige que MapLibre esté inicializado, getInstance es idempotente
    private fun manager(): OfflineManager {
        MapLibre.getInstance(context)
        return OfflineManager.getInstance(context)
    }

    suspend fun estado(region: RegionMapa): EstadoRegionMapa {
        val guardada = buscar(region) ?: return EstadoRegionMapa.NoDescargada
        return guardada.estado().aEstado(descargando = false)
    }

    // Descarga la región (o continúa lo que faltaba) y emite el avance, termina al completarse.
    // Si el colector se cancela la descarga se pausa y lo descargado se conserva
    fun descargar(region: RegionMapa): Flow<EstadoRegionMapa> = callbackFlow {
        val guardada = buscar(region) ?: crear(region)
        guardada.setObserver(object : OfflineRegion.OfflineRegionObserver {
            override fun onStatusChanged(status: OfflineRegionStatus) {
                trySend(status.aEstado(descargando = true))
                if (status.isComplete) close()
            }

            override fun onError(error: OfflineRegionError) {
                close(ErrorDescargaMapa(error.message))
            }

            override fun mapboxTileCountLimitExceeded(limit: Long) {
                close(ErrorDescargaMapa("Límite de $limit teselas excedido"))
            }
        })
        guardada.setDownloadState(OfflineRegion.STATE_ACTIVE)
        awaitClose {
            guardada.setObserver(null)
            guardada.setDownloadState(OfflineRegion.STATE_INACTIVE)
        }
    }

    suspend fun eliminar(region: RegionMapa) {
        val guardada = buscar(region) ?: return
        suspendCancellableCoroutine { cont ->
            guardada.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
                override fun onDelete() = cont.resume(Unit)
                override fun onError(error: String) = cont.resumeWithException(ErrorDescargaMapa(error))
            })
        }
    }

    private suspend fun buscar(region: RegionMapa): OfflineRegion? =
        suspendCancellableCoroutine { cont ->
            manager().listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
                override fun onList(offlineRegions: Array<OfflineRegion>?) {
                    cont.resume(offlineRegions?.firstOrNull { String(it.metadata) == region.nombre })
                }

                override fun onError(error: String) = cont.resumeWithException(ErrorDescargaMapa(error))
            })
        }

    private suspend fun crear(region: RegionMapa): OfflineRegion =
        suspendCancellableCoroutine { cont ->
            val limites = LatLngBounds.from(region.norte, region.este, region.sur, region.oeste)
            val definicion = OfflineTilePyramidRegionDefinition(
                ESTILO, limites, region.zoomMin, region.zoomMax, context.resources.displayMetrics.density
            )
            manager().createOfflineRegion(definicion, region.nombre.toByteArray(),
                object : OfflineManager.CreateOfflineRegionCallback {
                    override fun onCreate(offlineRegion: OfflineRegion) = cont.resume(offlineRegion)
                    override fun onError(error: String) = cont.resumeWithException(ErrorDescargaMapa(error))
                })
        }

    private suspend fun OfflineRegion.estado(): OfflineRegionStatus =
        suspendCancellableCoroutine { cont ->
            getStatus(object : OfflineRegion.OfflineRegionStatusCallback {
                override fun onStatus(status: OfflineRegionStatus?) {
                    if (status != null) cont.resume(status)
                    else cont.resumeWithException(ErrorDescargaMapa("Sin estado"))
                }

                override fun onError(error: String?) = cont.resumeWithException(ErrorDescargaMapa(error))
            })
        }

    private fun OfflineRegionStatus.aEstado(descargando: Boolean): EstadoRegionMapa = when {
        isComplete -> EstadoRegionMapa.Completa(completedResourceSize)
        descargando -> EstadoRegionMapa.Descargando(completedResourceCount, requiredResourceCount)
        completedResourceCount == 0L -> EstadoRegionMapa.NoDescargada
        else -> EstadoRegionMapa.Parcial(completedResourceCount, requiredResourceCount)
    }

    companion object {
        // Mapa de OpenStreetMap en teselas vectoriales, público y sin clave.
        // El proveedor definitivo de teselas está por decidir
        const val ESTILO = "https://tiles.openfreemap.org/styles/liberty"
    }
}
