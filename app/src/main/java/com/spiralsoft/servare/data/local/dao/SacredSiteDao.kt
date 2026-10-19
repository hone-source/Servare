package com.spiralsoft.servare.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.spiralsoft.servare.data.local.entity.SacredSiteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SacredSiteDao {
    @Query("SELECT * FROM sacred_sites ORDER BY name")
    fun observeAll(): Flow<List<SacredSiteEntity>>

    @Query("SELECT * FROM sacred_sites WHERE entityId = :entityId")
    suspend fun findById(entityId: String): SacredSiteEntity?

    @Upsert
    suspend fun upsert(site: SacredSiteEntity)

    @Query("""
        SELECT EXISTS(SELECT 1 FROM operations
        WHERE entityId = :entityId
        AND status NOT IN ('COMPLETADA','DESCARTADA'))
    """)
    suspend fun hasPendingOperations(entityId: String): Boolean
}
