package com.spiralsoft.servare.data.remote

import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import com.spiralsoft.servare.data.sesion.GestorSesion

// Añade el token de la sesión a las peticiones de datos. No se usa en el registro, el inicio de sesión
// ni el health check, que no tienen sesión. Sin token la petición sale igual: el servidor responde 401
class AuthInterceptor @Inject constructor(
    private val gestor: GestorSesion
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = gestor.token() ?: return chain.proceed(chain.request())
        val peticion = chain.request().newBuilder()
            .header("Authorization", "Bearer $token")
            .build()
        return chain.proceed(peticion)
    }
}
