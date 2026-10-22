package com.spiralsoft.servare.data.remote

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import java.time.LocalDate
import java.time.LocalTime

/**
 * JSON del API: enums por nombre, fechas `YYYY-MM-DD` y horas `HH:MM[:SS]` (ISO-8601).
 * Sin estos adaptadores Gson serializaría los campos internos de LocalDate/LocalTime.
 * Lo usan Retrofit y el repositorio al guardar el `data` de cada operación.
 */
fun apiGson(): Gson = GsonBuilder()
    .registerTypeAdapter(LocalDate::class.java, LocalDateAdapter.nullSafe())
    .registerTypeAdapter(LocalTime::class.java, LocalTimeAdapter.nullSafe())
    .create()

private object LocalDateAdapter : TypeAdapter<LocalDate>() {
    override fun write(out: JsonWriter, value: LocalDate) { out.value(value.toString()) }
    override fun read(reader: JsonReader): LocalDate = LocalDate.parse(reader.nextString())
}

private object LocalTimeAdapter : TypeAdapter<LocalTime>() {
    override fun write(out: JsonWriter, value: LocalTime) { out.value(value.toString()) }
    override fun read(reader: JsonReader): LocalTime = LocalTime.parse(reader.nextString())
}
