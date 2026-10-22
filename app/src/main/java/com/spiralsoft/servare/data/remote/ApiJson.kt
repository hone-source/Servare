package com.spiralsoft.servare.data.remote

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeParseException

/**
 * JSON del API: enums por nombre, fechas `YYYY-MM-DD` y horas `HH:MM[:SS]` (ISO-8601).
 * Se envían todos los campos menos los nulos. Al leer se toleran campos
 * desconocidos, y un `null` en un campo no nulo o un enum desconocido toman el valor por defecto.
 * Lo usan Retrofit y el repositorio al guardar el `data` de cada operación.
 */
fun apiJson(): Json = Json {
    encodeDefaults = true
    explicitNulls = false
    ignoreUnknownKeys = true
    coerceInputValues = true
    serializersModule = SerializersModule {
        contextual(LocalDate::class, IsoSerializer("java.time.LocalDate", LocalDate::parse))
        contextual(LocalTime::class, IsoSerializer("java.time.LocalTime", LocalTime::parse))
    }
}

/** Texto ISO-8601; un valor mal formado es un error de lectura como cualquier otro. */
private class IsoSerializer<T : Any>(name: String, private val parse: (CharSequence) -> T) : KSerializer<T> {
    override val descriptor = PrimitiveSerialDescriptor(name, PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: T) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): T = try {
        parse(decoder.decodeString())
    } catch (e: DateTimeParseException) {
        throw SerializationException(e.message, e)
    }
}
