package com.spiralsoft.servare.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.spiralsoft.servare.data.repository.DefaultPruebaRepository
import com.spiralsoft.servare.data.repository.DefaultSesionRepository
import com.spiralsoft.servare.domain.repository.PruebaRepository
import com.spiralsoft.servare.domain.repository.SesionRepository

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds abstract fun pruebaRepository(impl: DefaultPruebaRepository): PruebaRepository

    @Binds abstract fun sesionRepository(impl: DefaultSesionRepository): SesionRepository

}
