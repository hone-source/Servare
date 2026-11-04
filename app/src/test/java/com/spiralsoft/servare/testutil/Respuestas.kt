package com.spiralsoft.servare.testutil

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response

// Respuesta de error con el cuerpo dado, como la que entrega Retrofit con un 4xx o 5xx
fun <T> error(codigo: Int, cuerpo: String = ""): Response<T> =
    Response.error(codigo, cuerpo.toResponseBody("application/json".toMediaType()))
