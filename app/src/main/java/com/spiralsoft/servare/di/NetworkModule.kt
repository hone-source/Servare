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
import com.google.gson.Gson
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import com.spiralsoft.servare.data.remote.RemoteDataSource
import com.spiralsoft.servare.data.remote.RetrofitRemoteDataSource
import com.spiralsoft.servare.data.remote.ServareApi
import com.spiralsoft.servare.data.remote.apiGson

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
    @Provides @Named("baseUrl") fun baseUrl(): String = "https://10.0.2.2:8000/"
    
    @Provides @Singleton fun okHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .callTimeout(5.seconds.toJavaDuration())
        .build()

    /** Mismo JSON para Retrofit y para el `data` de las operaciones de la cola. */
    @Provides @Singleton fun gson(): Gson = apiGson()

    @Provides @Singleton
    fun retrofit(@Named("baseUrl") baseUrl: String, client: OkHttpClient, gson: Gson): Retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()

    @Provides @Singleton fun servareApi(retrofit: Retrofit): ServareApi = retrofit.create(ServareApi::class.java)
}
