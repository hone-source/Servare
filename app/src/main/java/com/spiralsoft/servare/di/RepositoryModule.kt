package com.spiralsoft.servare.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.spiralsoft.servare.data.repository.DefaultFichaRepository
import com.spiralsoft.servare.data.repository.DefaultPruebaRepository
import com.spiralsoft.servare.domain.repository.FichaRepository
import com.spiralsoft.servare.domain.repository.PruebaRepository

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds abstract fun pruebaRepository(impl: DefaultPruebaRepository): PruebaRepository

    @Binds abstract fun fichaRepository(impl: DefaultFichaRepository): FichaRepository

}
