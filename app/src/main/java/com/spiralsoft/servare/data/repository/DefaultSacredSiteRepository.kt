package com.spiralsoft.servare.data.repository

import androidx.room.withTransaction
import com.spiralsoft.servare.data.connectivity.ConnectivityMonitor
import com.spiralsoft.servare.data.connectivity.ConnectivityState
import com.spiralsoft.servare.data.local.AppDatabase
import com.spiralsoft.servare.data.local.dao.*
import com.spiralsoft.servare.data.local.entity.*
import com.spiralsoft.servare.data.sync.SyncScheduler
import com.spiralsoft.servare.domain.model.*
import com.spiralsoft.servare.domain.repository.SacredSiteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Clock
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

@Serializable
private data class SiteData(
    val name: String, val description: String, val type: String,
    val latitude: Double, val longitude: Double
)

class DefaultSacredSiteRepository @Inject constructor(
    private val database: AppDatabase,
    private val siteDao: SacredSiteDao,
    private val operationDao: OperationDao,
    private val monitor: ConnectivityMonitor,
    private val scheduler: SyncScheduler,
    private val clock: Clock = Clock.systemUTC()
) : SacredSiteRepository {

    val connectivity: StateFlow<ConnectivityState> = monitor.state

    override fun observeSites(): Flow<List<SacredSite>> =
        combine(siteDao.observeAll(), operationDao.observeUnsyncedEntityIds()) { sites, pending ->
            val pendingSet = pending.toSet()
            sites.map {
                SacredSite(
                    it.entityId, it.name, it.description, it.type,
                    it.latitude, it.longitude, pendingSync = it.entityId in pendingSet
                )
            }
        }

    override suspend fun save(form: SacredSiteForm): SaveResult {
        val missing = buildList {
            if (form.name.isBlank()) add("name")
            if (form.type.isBlank()) add("type")
        }
        if (missing.isNotEmpty()) return SaveResult.Invalid(missing)

        val id = form.entityId ?: UUID.randomUUID().toString()

        // cambio + operación PENDIENTE en UNA misma transacción
        database.withTransaction {
            val existing = siteDao.findById(id)
            siteDao.upsert(
                SacredSiteEntity(
                    entityId = id, name = form.name, description = form.description,
                    type = form.type, latitude = form.latitude, longitude = form.longitude,
                    serverVersion = existing?.serverVersion ?: 0
                )
            )
            operationDao.insert(
                OperationEntity(
                    operationId = "op-" + UUID.randomUUID(),
                    type = if (existing == null) OperationType.CREATE else OperationType.UPDATE,
                    entityId = id,
                    data = Json.encodeToString(
                        SiteData(
                            form.name,
                            form.description,
                            form.type,
                            form.latitude,
                            form.longitude
                        )
                    ),
                    createdAt = Instant.now(clock),
                    baseVersion = existing?.serverVersion
                )
            )
        }
        // scheduler.requestSync() aquí, fuera de la transacción
        return SaveResult.Saved(id)
    }
    override suspend fun getSite(entityId: String): SacredSite? =
        siteDao.findById(entityId)?.let {
            SacredSite(
                it.entityId, it.name, it.description, it.type,
                it.latitude, it.longitude,
                pendingSync = siteDao.hasPendingOperations(entityId)
            )
        }

}
