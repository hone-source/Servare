package com.spiralsoft.servare.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import javax.inject.Singleton
import com.spiralsoft.servare.BuildConfig

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides @Singleton
    fun baseUrl(): HttpUrl = BuildConfig.API_BASE_URL.toHttpUrl()

    // Cliente base compartido, cada uso deriva el suyo con newBuilder()
    @Provides @Singleton
    fun okHttpClient(): OkHttpClient = OkHttpClient()
}
