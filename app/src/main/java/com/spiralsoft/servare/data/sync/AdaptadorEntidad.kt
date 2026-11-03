package com.spiralsoft.servare.data.sync

import com.spiralsoft.servare.data.local.entity.OperationEntity
import com.spiralsoft.servare.data.remote.ResultadoEnvio
import com.spiralsoft.servare.data.remote.ResultadoPull
import com.spiralsoft.servare.domain.model.CampoComparado
import com.spiralsoft.servare.domain.model.TipoEntidad

// Totalidad de lo que el motor, el resolutor de operaciones y los avisos necesitan saber de un tipo de registro:
// su tabla, su recurso remoto y cómo se compara con el del servidor. Agregar un tipo de registro es
// escribir un adaptador y registrarlo en SyncModule, sin tocar a quienes lo usan
//
// Piezas de un tipo de registro nuevo (el molde completo es PRUEBA, FICHA es el caso con tablas hijas):
//  1. domain/model/TipoEntidad: el valor nuevo. Adaptadores falla al arrancar si falta su adaptador
//  2. data/local: entidad (con version: Int?), DAO (findById, findActivaById, confirmadasPorElServidor,
//     actualizarVersion, limpiarVersion, deleteById, upsert) y registro en AppDatabase y DatabaseModule
//  3. data/remote: Api (GET lista, POST/PUT/DELETE con Idempotency-Key), DTO y RemoteDataSource con
//     TraductorRespuestas. En NetworkModule, un provider de la Api con AuthInterceptor
//  4. data/sync: un adaptador que extienda AdaptadorBase y su @Binds @IntoMap en di/SyncModule
//  5. data/repository: repositorio sobre EscrituraOutbox (crear, actualizar, eliminar) y su @Binds en RepositoryModule,
//     la interfaz de domain/repository extiende RepositorioSincronizable
//  6. ui: ViewModel de lista sobre ListaSincronizableViewModel, su formulario, pantallas y rutas en navigation
// El motor, el resolutor, los avisos de problemas y la consulta de la cola no se tocan
interface AdaptadorEntidad {
    val tipo: TipoEntidad

    // El contenido se lee de la tabla en el momento del envío: siempre va lo más reciente
    suspend fun enviar(op: OperationEntity): ResultadoEnvio

    // Pull: descarga los registros del servidor y aplica los que no estén protegidos
    suspend fun descargar(): Descarga

    // null si el registro ya no está en el dispositivo
    suspend fun titulo(entityId: String): String?

    suspend fun existe(entityId: String): Boolean

    suspend fun borrar(entityId: String)

    // Solo la versión: no pisa una edición hecha mientras la operación estaba en vuelo
    suspend fun actualizarVersion(entityId: String, version: Int)

    // El registro se vuelve a crear en el servidor: la versión que conocía ya no existe
    suspend fun limpiarVersion(entityId: String)

    // El registro vigente que trajo el 409 (JSON), null si no se puede leer
    fun leerServidor(entityId: String, snapshot: String): RegistroServidor?
}

// eliminadas: títulos de los registros que se quitaron del dispositivo por estar borrados en el servidor
class Descarga(val pull: ResultadoPull<*>, val eliminadas: List<String> = emptyList())

// La versión del servidor de un registro en conflicto
class RegistroServidor(
    val version: Int?,
    // Sus campos ya puestos junto a los del dispositivo, para que el usuario decida
    val comparacion: suspend () -> List<CampoComparado>,
    // Reemplaza el registro del dispositivo por el del servidor
    val aplicar: suspend () -> Unit
)
