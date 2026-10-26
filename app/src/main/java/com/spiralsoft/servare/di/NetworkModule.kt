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
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import com.spiralsoft.servare.BuildConfig
import com.spiralsoft.servare.data.remote.AuthApi
import com.spiralsoft.servare.data.remote.AuthInterceptor
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

    // Los datos van con el token de la sesión.
    // Cliente derivado, el compartido, que usa el health check, no lo lleva
    @Provides @Singleton
    fun pruebaApi(retrofit: Retrofit, okHttpClient: OkHttpClient, authInterceptor: AuthInterceptor): PruebaApi {
        val cliente = okHttpClient.newBuilder().addInterceptor(authInterceptor).build()
        return retrofit.newBuilder().client(cliente).build().create(PruebaApi::class.java)
    }

    // Registrarse espera al servidor con el usuario mirando: la llamada completa se acota a 5s.
    // Cliente propio derivado del compartido, no afecta a PruebaApi
    @Provides @Singleton
    fun authApi(retrofit: Retrofit, okHttpClient: OkHttpClient): AuthApi {
        val cliente = okHttpClient.newBuilder()
            .callTimeout(TIMEOUT_AUTH_S, TimeUnit.SECONDS)
            .build()
        return retrofit.newBuilder().client(cliente).build().create(AuthApi::class.java)
    }

    private const val TIMEOUT_AUTH_S = 5L
}
