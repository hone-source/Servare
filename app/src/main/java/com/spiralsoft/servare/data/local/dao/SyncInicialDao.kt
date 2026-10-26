package com.spiralsoft.servare.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import com.spiralsoft.servare.data.local.entity.SyncInicialEntity
import com.spiralsoft.servare.domain.model.TipoEntidad

@Dao
interface SyncInicialDao {
    // Si ya estaba marcada se conserva la fecha de la primera descarga
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun marcarCompletada(sync: SyncInicialEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM sync_inicial WHERE entityType = :entityType)")
    suspend fun estaCompletada(entityType: TipoEntidad): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM sync_inicial WHERE entityType = :entityType)")
    fun observarCompletada(entityType: TipoEntidad): Flow<Boolean>
}
