package com.spiralsoft.servare.di

import dagger.Binds
import dagger.MapKey
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap
import com.spiralsoft.servare.data.sync.AdaptadorEntidad
import com.spiralsoft.servare.data.sync.AdaptadorFicha
import com.spiralsoft.servare.data.sync.AdaptadorPrueba
import com.spiralsoft.servare.domain.model.TipoEntidad

@MapKey
annotation class ClaveTipoEntidad(val value: TipoEntidad)

// Un adaptador por cada valor de TipoEntidad: Adaptadores lo comprueba al construirse
@Module
@InstallIn(SingletonComponent::class)
abstract class SyncModule {

    @Binds @IntoMap @ClaveTipoEntidad(TipoEntidad.PRUEBA)
    abstract fun adaptadorPrueba(impl: AdaptadorPrueba): AdaptadorEntidad

    @Binds @IntoMap @ClaveTipoEntidad(TipoEntidad.FICHA)
    abstract fun adaptadorFicha(impl: AdaptadorFicha): AdaptadorEntidad
}
