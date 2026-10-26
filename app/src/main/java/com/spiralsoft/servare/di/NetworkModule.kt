package com.spiralsoft.servare.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import javax.inject.Singleton
import com.spiralsoft.servare.BuildConfig
import com.spiralsoft.servare.data.remote.PruebaApi

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides @Singleton
    fun baseUrl(): HttpUrl = BuildConfig.API_BASE_URL.toHttpUrl()

    // Cliente base compartido, cada uso deriva el suyo con newBuilder()
    @Provides @Singleton
    fun okHttpClient(): OkHttpClient = OkHttpClient()

    @Provides @Singleton
    fun retrofit(baseUrl: HttpUrl, okHttpClient: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides @Singleton
    fun pruebaApi(retrofit: Retrofit): PruebaApi = retrofit.create(PruebaApi::class.java)
}
