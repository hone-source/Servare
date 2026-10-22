package com.spiralsoft.servare.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.spiralsoft.servare.domain.repository.ConectividadRepository
import com.spiralsoft.servare.domain.repository.DefaultConectividadRepository
import com.spiralsoft.servare.domain.repository.DefaultFichaRepository
import com.spiralsoft.servare.domain.repository.DefaultSincronizacionRepository
import com.spiralsoft.servare.domain.repository.FichaRepository
import com.spiralsoft.servare.domain.repository.SincronizacionRepository

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds abstract fun fichaRepository(impl: DefaultFichaRepository): FichaRepository
    @Binds abstract fun conectividadRepository(impl: DefaultConectividadRepository): ConectividadRepository
    @Binds abstract fun sincronizacionRepository(impl: DefaultSincronizacionRepository): SincronizacionRepository
}
