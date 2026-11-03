package com.spiralsoft.servare.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import com.spiralsoft.servare.data.local.entity.ElementoEspacioEntity
import com.spiralsoft.servare.data.local.entity.FichaEntity
import com.spiralsoft.servare.data.local.entity.ParticipanteEntity
import com.spiralsoft.servare.data.local.entity.PuntoPoligonalEntity
import com.spiralsoft.servare.data.local.entity.RitualEntity
import com.spiralsoft.servare.domain.model.FichaResumen

@Dao
interface FichaDao {
    // Lee también la cola y las eliminaciones: el Flow emite cuando cambia cualquiera de las tres tablas
    @Query(
        """
        SELECT f.entityId, f.nombre, f.tipoEspacio, f.comunidad, f.fechaLevantamiento,
            EXISTS(
                SELECT 1 FROM operaciones o
                WHERE o.entityType = 'FICHA' AND o.entityId = f.entityId
                AND o.status IN ('PENDIENTE', 'PROCESANDO', 'REINTENTABLE')
            ) AS pendiente
        FROM fichas f
        WHERE NOT EXISTS (
            SELECT 1 FROM eliminaciones e
            WHERE e.entityType = 'FICHA' AND e.entityId = f.entityId
        )
        ORDER BY f.createdAt DESC
        """
    )
    fun observarResumenes(): Flow<List<FichaResumen>>

    // Excluye las marcadas como eliminadas
    @Query(
        """
        SELECT * FROM fichas f
        WHERE f.entityId = :entityId
        AND NOT EXISTS (
            SELECT 1 FROM eliminaciones e
            WHERE e.entityType = 'FICHA' AND e.entityId = f.entityId
        )
        """
    )
    suspend fun findActivaById(entityId: String): FichaEntity?

    // Con sus tablas, para abrirla en el formulario
    @Transaction
    @Query(
        """
        SELECT * FROM fichas f
        WHERE f.entityId = :entityId
        AND NOT EXISTS (
            SELECT 1 FROM eliminaciones e
            WHERE e.entityType = 'FICHA' AND e.entityId = f.entityId
        )
        """
    )
    suspend fun findCompletaActivaById(entityId: String): FichaCompleta?

    // Incluye las marcadas como eliminadas, un CREAR pendiente debe enviarse aunque se haya borrado después
    @Query("SELECT * FROM fichas WHERE entityId = :entityId")
    suspend fun findById(entityId: String): FichaEntity?

    // Con sus tablas, para enviarla o compararla con la del servidor
    @Transaction
    @Query("SELECT * FROM fichas WHERE entityId = :entityId")
    suspend fun findCompletaById(entityId: String): FichaCompleta?

    // Las que el servidor ya conoció, si dejan de venir en el pull, se borraron allí.
    // Sin versión son fichas que el servidor nunca confirmó, esas no se tocan
    @Query("SELECT * FROM fichas WHERE version IS NOT NULL")
    suspend fun confirmadasPorElServidor(): List<FichaEntity>

    // Solo la versión: no pisa una edición hecha mientras la operación estaba en vuelo
    @Query("UPDATE fichas SET version = :version WHERE entityId = :entityId")
    suspend fun actualizarVersion(entityId: String, version: Int)

    // La ficha se vuelve a crear en el servidor: la versión que conocía ya no existe
    @Query("UPDATE fichas SET version = NULL WHERE entityId = :entityId")
    suspend fun limpiarVersion(entityId: String)

    @Query("DELETE FROM fichas WHERE entityId = :entityId")
    suspend fun deleteById(entityId: String)

    @Insert
    suspend fun insertFicha(ficha: FichaEntity)

    @Update
    suspend fun updateFicha(ficha: FichaEntity)

    // Pull: crea la ficha bajada del servidor o actualiza la que ya estaba
    @Upsert
    suspend fun upsertFicha(ficha: FichaEntity)

    @Insert
    suspend fun insertPoligonal(filas: List<PuntoPoligonalEntity>)

    @Insert
    suspend fun insertRituales(filas: List<RitualEntity>)

    @Insert
    suspend fun insertElementos(filas: List<ElementoEspacioEntity>)

    @Insert
    suspend fun insertParticipantes(filas: List<ParticipanteEntity>)

    // Update y upsert no borran en cascada, así que las filas anteriores se quitan antes de escribir las nuevas
    @Query("DELETE FROM ficha_poligonal WHERE fichaId = :entityId")
    suspend fun borrarPoligonal(entityId: String)

    @Query("DELETE FROM ficha_rituales WHERE fichaId = :entityId")
    suspend fun borrarRituales(entityId: String)

    @Query("DELETE FROM ficha_elementos WHERE fichaId = :entityId")
    suspend fun borrarElementos(entityId: String)

    @Query("DELETE FROM ficha_participantes WHERE fichaId = :entityId")
    suspend fun borrarParticipantes(entityId: String)

    @Transaction
    suspend fun insert(ficha: FichaCompleta) {
        insertFicha(ficha.ficha)
        insertTablas(ficha)
    }

    @Transaction
    suspend fun update(ficha: FichaCompleta) {
        updateFicha(ficha.ficha)
        reemplazarTablas(ficha)
    }

    @Transaction
    suspend fun upsert(ficha: FichaCompleta) {
        upsertFicha(ficha.ficha)
        reemplazarTablas(ficha)
    }

    private suspend fun reemplazarTablas(ficha: FichaCompleta) {
        val id = ficha.ficha.entityId
        borrarPoligonal(id)
        borrarRituales(id)
        borrarElementos(id)
        borrarParticipantes(id)
        insertTablas(ficha)
    }

    private suspend fun insertTablas(ficha: FichaCompleta) {
        insertPoligonal(ficha.poligonal)
        insertRituales(ficha.rituales)
        insertElementos(ficha.elementos)
        insertParticipantes(ficha.participantes)
    }
}
