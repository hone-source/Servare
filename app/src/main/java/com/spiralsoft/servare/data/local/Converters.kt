package com.spiralsoft.servare.data.local

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import com.spiralsoft.servare.domain.model.ElementoEspacio
import com.spiralsoft.servare.domain.model.Participante
import com.spiralsoft.servare.domain.model.PuntoPoligonal
import com.spiralsoft.servare.domain.model.RegistroArchivos
import com.spiralsoft.servare.domain.model.Ritual

private val gson: Gson = Gson()

private inline fun <reified T> fromJson(json: String): T =
    gson.fromJson(json, object : TypeToken<T>() {}.type)

class Converters {
    @TypeConverter fun fromInstant(v: Instant?): Long? = v?.toEpochMilli()
    @TypeConverter fun toInstant(v: Long?): Instant? = v?.let(Instant::ofEpochMilli)

    // ISO-8601 (2026-10-19, 14:30)
    @TypeConverter fun fromLocalDate(v: LocalDate?): String? = v?.toString()
    @TypeConverter fun toLocalDate(v: String?): LocalDate? = v?.let(LocalDate::parse)
    @TypeConverter fun fromLocalTime(v: LocalTime?): String? = v?.toString()
    @TypeConverter fun toLocalTime(v: String?): LocalTime? = v?.let(LocalTime::parse)

    // Partes de la ficha que nunca se consultan por separado: JSON
    @TypeConverter fun fromRegistro(v: RegistroArchivos): String = gson.toJson(v)
    @TypeConverter fun toRegistro(v: String): RegistroArchivos = fromJson(v)
    @TypeConverter fun fromPoligonal(v: List<PuntoPoligonal>): String = gson.toJson(v)
    @TypeConverter fun toPoligonal(v: String): List<PuntoPoligonal> = fromJson(v)
    @TypeConverter fun fromRituales(v: List<Ritual>): String = gson.toJson(v)
    @TypeConverter fun toRituales(v: String): List<Ritual> = fromJson(v)
    @TypeConverter fun fromElementos(v: List<ElementoEspacio>): String = gson.toJson(v)
    @TypeConverter fun toElementos(v: String): List<ElementoEspacio> = fromJson(v)
    @TypeConverter fun fromParticipantes(v: List<Participante>): String = gson.toJson(v)
    @TypeConverter fun toParticipantes(v: String): List<Participante> = fromJson(v)
}
