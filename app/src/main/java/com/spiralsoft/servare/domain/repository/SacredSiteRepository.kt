package com.spiralsoft.servare.domain.repository

import com.spiralsoft.servare.domain.model.*
import kotlinx.coroutines.flow.Flow

interface SacredSiteRepository {
    fun observeSites(): Flow<List<SacredSite>>
    suspend fun save(form: SacredSiteForm): SaveResult
    // D1 también declara: connectivity, resolveConflict, applyRemoteChanges

    suspend fun getSite(entityId: String): SacredSite?
}
