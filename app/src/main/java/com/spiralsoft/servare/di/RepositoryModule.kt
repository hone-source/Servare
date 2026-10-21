package com.spiralsoft.servare.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.spiralsoft.servare.data.repository.ConectividadRepository
import com.spiralsoft.servare.data.repository.DefaultConectividadRepository
import com.spiralsoft.servare.data.repository.DefaultFichaRepository
import com.spiralsoft.servare.data.repository.FichaRepository

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds abstract fun fichaRepository(impl: DefaultFichaRepository): FichaRepository
    @Binds abstract fun conectividadRepository(impl: DefaultConectividadRepository): ConectividadRepository
}
