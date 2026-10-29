package com.spiralsoft.servare.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import com.spiralsoft.servare.data.local.entity.OperationEntity
import com.spiralsoft.servare.domain.model.CausaConflicto
import com.spiralsoft.servare.domain.model.TipoEntidad

@Dao
interface OperationDao {
    @Insert
    suspend fun insert(operacion: OperationEntity)

    // Operación aún no enviada: un nuevo guardado de la entidad no necesita otra,
    // porque al enviarse se lee el estado actual del registro
    @Query(
        """
        SELECT * FROM operaciones
        WHERE entityType = :entityType AND entityId = :entityId
        AND status = 'PENDIENTE' AND uncertain = 0
        ORDER BY createdAt DESC
        LIMIT 1
        """
    )
    suspend fun buscarAgrupable(entityType: TipoEntidad, entityId: String): OperationEntity?

    // --- Selección (motor de sincronización) ---

    // Las que quedaron en PROCESANDO al interrumpirse el ciclo: resultado incierto, no cuenta como intento
    @Query("UPDATE operaciones SET status = 'PENDIENTE', uncertain = 1 WHERE status = 'PROCESANDO'")
    suspend fun recuperarHuerfanas(): Int

    // Vence nextAttemptAt (el llamador ya confirmó que el servidor es alcanzable)
    @Query(
        """
        UPDATE operaciones SET status = 'PENDIENTE'
        WHERE status = 'REINTENTABLE' AND nextAttemptAt <= :ahora
        """
    )
    suspend fun liberarReintentables(ahora: Long): Int

    // Siguiente PENDIENTE elegible: la más antigua de su entidad (ninguna anterior sin terminar,
    // incluidas REINTENTABLE aún no vencidas, para no adelantar un cambio) y con dependsOn COMPLETADA
    @Query(
        """
        SELECT * FROM operaciones o
        WHERE o.status = 'PENDIENTE'
        AND NOT EXISTS (
            SELECT 1 FROM operaciones previa
            WHERE previa.entityType = o.entityType AND previa.entityId = o.entityId
            AND previa.createdAt < o.createdAt
            AND previa.status NOT IN ('COMPLETADA', 'DESCARTADA')
        )
        AND (o.dependsOn IS NULL OR EXISTS (
            SELECT 1 FROM operaciones dep
            WHERE dep.operationId = o.dependsOn AND dep.status = 'COMPLETADA'
        ))
        ORDER BY o.createdAt ASC
        LIMIT 1
        """
    )
    suspend fun siguienteElegible(): OperationEntity?

    // --- Transiciones de estado ---

    @Query("UPDATE operaciones SET status = 'PROCESANDO', lastAttemptAt = :ahora WHERE operationId = :operationId")
    suspend fun marcarProcesando(operationId: String, ahora: Long)

    @Query("UPDATE operaciones SET status = 'COMPLETADA', uncertain = 0, lastError = NULL WHERE operationId = :operationId")
    suspend fun marcarCompletada(operationId: String)

    // Sin respuesta o conexión perdida: vuelve a la cola sin sumar intento
    @Query("UPDATE operaciones SET status = 'PENDIENTE', uncertain = 1 WHERE operationId = :operationId")
    suspend fun marcarIncierta(operationId: String)

    // Timeout o 5xx con servidor alcanzable: el intento cuenta
    @Query(
        """
        UPDATE operaciones
        SET status = 'REINTENTABLE', attemptCount = :intentos, nextAttemptAt = :proximo, lastError = :error
        WHERE operationId = :operationId
        """
    )
    suspend fun marcarReintentable(operationId: String, intentos: Int, proximo: Long, error: String)

    // Error definitivo (422, 413, regla de negocio) o 5 intentos agotados
    @Query(
        """
        UPDATE operaciones
        SET status = 'FALLIDA', attemptCount = :intentos, nextAttemptAt = NULL, lastError = :error
        WHERE operationId = :operationId
        """
    )
    suspend fun marcarFallida(operationId: String, intentos: Int, error: String)

    // Versión distinta o 404/410.
    // El 409 trae el registro vigente del servidor: se guarda para compararlo
    @Query(
        """
        UPDATE operaciones
        SET status = 'CONFLICTO', nextAttemptAt = NULL, lastError = :error,
            conflictCause = :causa, serverSnapshot = :snapshot
        WHERE operationId = :operationId
        """
    )
    suspend fun marcarConflicto(operationId: String, error: String, causa: CausaConflicto, snapshot: String?)

    // Las posteriores de la misma entidad y las que dependen de ella pasan a BLOQUEADA (se conservan)
    @Query(
        """
        UPDATE operaciones SET status = 'BLOQUEADA'
        WHERE status IN ('PENDIENTE', 'REINTENTABLE') AND operationId != :operationId
        AND ((entityType = :entityType AND entityId = :entityId AND createdAt > :createdAt)
             OR dependsOn = :operationId)
        """
    )
    suspend fun bloquearDependientes(operationId: String, entityType: TipoEntidad, entityId: String, createdAt: Long)

    // Encadenamiento de versiones: la siguiente edición offline parte de la versión que devolvió el servidor
    @Query(
        """
        UPDATE operaciones SET baseVersion = :version
        WHERE entityType = :entityType AND entityId = :entityId AND createdAt > :createdAt
        AND type != 'CREAR' AND status IN ('PENDIENTE', 'REINTENTABLE', 'BLOQUEADA')
        """
    )
    suspend fun encadenarBaseVersion(entityType: TipoEntidad, entityId: String, createdAt: Long, version: Int)

    // 401/403: la operación no se envió ni falló, la cola se pausa y sigue como estaba
    @Query("UPDATE operaciones SET status = 'PENDIENTE' WHERE operationId = :operationId")
    suspend fun marcarPendiente(operationId: String)

    // --- Acciones del usuario (docs/2.2.md). Cada una exige el estado esperado y devuelve las filas
    // cambiadas: 0 significa que la operación ya no está en ese estado ---

    @Query("SELECT * FROM operaciones WHERE operationId = :operationId")
    suspend fun buscar(operationId: String): OperationEntity?

    // FALLIDA → PENDIENTE con el mismo operationId y los 5 intentos de nuevo
    @Query(
        """
        UPDATE operaciones
        SET status = 'PENDIENTE', attemptCount = 0, nextAttemptAt = NULL, lastError = NULL
        WHERE operationId = :operationId AND status = 'FALLIDA'
        """
    )
    suspend fun reintentar(operationId: String): Int

    // Conservar la mía: CONFLICTO → PENDIENTE sobre la versión del servidor. Una CREAR que chocó con un
    // registro que ya existía pasa a ser una ACTUALIZAR de esa versión
    @Query(
        """
        UPDATE operaciones
        SET status = 'PENDIENTE', attemptCount = 0, nextAttemptAt = NULL, lastError = NULL,
            conflictCause = NULL, serverSnapshot = NULL, baseVersion = :version,
            type = CASE WHEN type = 'CREAR' THEN 'ACTUALIZAR' ELSE type END
        WHERE operationId = :operationId AND status = 'CONFLICTO'
        """
    )
    suspend fun reanudarSobreVersion(operationId: String, version: Int): Int

    // El usuario abandona su cambio
    @Query(
        """
        UPDATE operaciones SET status = 'DESCARTADA', nextAttemptAt = NULL
        WHERE operationId = :operationId AND status IN ('FALLIDA', 'CONFLICTO')
        """
    )
    suspend fun descartar(operationId: String): Int

    // Las BLOQUEADAS por esta operación vuelven a la cola al resolverla...
    @Query(
        """
        UPDATE operaciones SET status = 'PENDIENTE'
        WHERE status = 'BLOQUEADA' AND operationId != :operationId
        AND ((entityType = :entityType AND entityId = :entityId AND createdAt > :createdAt)
             OR dependsOn = :operationId)
        """
    )
    suspend fun liberarBloqueadas(operationId: String, entityType: TipoEntidad, entityId: String, createdAt: Long)

    // ...y se descartan con ella si el usuario la abandona. Incluye las PENDIENTE y REINTENTABLE que el usuario
    // creó después sobre el mismo registro: sin esto, enviarían un contenido que ya no corresponde
    @Query(
        """
        UPDATE operaciones SET status = 'DESCARTADA'
        WHERE status IN ('BLOQUEADA', 'PENDIENTE', 'REINTENTABLE') AND operationId != :operationId
        AND ((entityType = :entityType AND entityId = :entityId AND createdAt > :createdAt)
             OR dependsOn = :operationId)
        """
    )
    suspend fun descartarPosteriores(operationId: String, entityType: TipoEntidad, entityId: String, createdAt: Long)

    // Protección del pull: registros con operaciones sin terminar (incluidas FALLIDA, CONFLICTO y BLOQUEADA,
    // que se conservan hasta que el usuario decida) no se sobrescriben con datos del servidor
    @Query(
        """
        SELECT DISTINCT entityId FROM operaciones
        WHERE entityType = :entityType AND status NOT IN ('COMPLETADA', 'DESCARTADA')
        """
    )
    suspend fun entidadesConOperacionesSinTerminar(entityType: TipoEntidad): List<String>

    // Cuándo programar el próximo ciclo si quedan reintentos por vencer
    @Query("SELECT MIN(nextAttemptAt) FROM operaciones WHERE status = 'REINTENTABLE'")
    suspend fun proximoIntento(): Long?

    // Lo que requiere atención del usuario, de la más antigua a la más reciente. Se conserva hasta que decida
    @Query(
        """
        SELECT o.operationId, o.type, o.status, o.entityType, o.entityId, o.lastError,
               o.conflictCause, o.serverSnapshot,
               p.titulo AS titulo, p.texto AS texto, p.numero AS numero,
               (SELECT COUNT(*) FROM operaciones x
                WHERE x.entityType = o.entityType AND x.entityId = o.entityId AND x.createdAt > o.createdAt
                AND x.status IN ('BLOQUEADA', 'PENDIENTE', 'REINTENTABLE')) AS posteriores
        FROM operaciones o
        LEFT JOIN pruebas p ON o.entityType = 'PRUEBA' AND p.entityId = o.entityId
        WHERE o.status IN ('FALLIDA', 'CONFLICTO', 'BLOQUEADA')
        ORDER BY o.createdAt ASC
        """
    )
    fun observarConProblema(): Flow<List<OperacionConProblema>>

    // Cambios que el servidor aún no confirma y el usuario no abandonó: cerrar sesión los borraría
    @Query("SELECT COUNT(*) FROM operaciones WHERE status NOT IN ('COMPLETADA', 'DESCARTADA')")
    suspend fun contarSinTerminar(): Int
}
