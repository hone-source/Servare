package com.spiralsoft.servare.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.spiralsoft.servare.data.remote.RemoteDataSource
import com.spiralsoft.servare.data.remote.RetrofitRemoteDataSource
import com.spiralsoft.servare.data.remote.ServareApi
import com.spiralsoft.servare.data.remote.apiJson

@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkBindsModule {
    @Binds abstract fun remoteDataSource(impl: RetrofitRemoteDataSource): RemoteDataSource
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkProvidesModule {
    // 10.0.2.2 = el localhost desde la PC visto desde el emulador.
    // TODO: pasar a un archivo aparte
    @Provides @Named("baseUrl") fun baseUrl(): String = "https://10.0.2.2:8000/api/"
    
    @Provides @Singleton fun okHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .callTimeout(5.seconds.toJavaDuration())
        .build()

    /** Mismo JSON para Retrofit y para el `data` de las operaciones de la cola. */
    @Provides @Singleton fun json(): Json = apiJson()

    @Provides @Singleton
    fun retrofit(@Named("baseUrl") baseUrl: String, client: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json; charset=utf-8".toMediaType()))
            .build()

    @Provides @Singleton fun servareApi(retrofit: Retrofit): ServareApi = retrofit.create(ServareApi::class.java)
}
