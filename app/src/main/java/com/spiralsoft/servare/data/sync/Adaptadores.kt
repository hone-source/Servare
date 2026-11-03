package com.spiralsoft.servare.data.sync

import javax.inject.Inject
import javax.inject.Singleton
import com.spiralsoft.servare.domain.model.TipoEntidad

// Los adaptadores por tipo de registro, registrados en SyncModule
@Singleton
class Adaptadores @Inject constructor(
    porTipo: Map<TipoEntidad, @JvmSuppressWildcards AdaptadorEntidad>
) {
    // Falla al construirse, no en el primer ciclo: un tipo de registro sin adaptador es un error de programación
    init {
        val faltan = TipoEntidad.entries.filter { it !in porTipo }
        check(faltan.isEmpty()) { "Faltan adaptadores en SyncModule para: $faltan" }
    }

    // En el orden de TipoEntidad: es el orden en que se descargan
    val todos: List<AdaptadorEntidad> = TipoEntidad.entries.map { porTipo.getValue(it) }

    operator fun get(tipo: TipoEntidad): AdaptadorEntidad = todos[tipo.ordinal]
}
