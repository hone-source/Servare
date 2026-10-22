package com.spiralsoft.servare.data.local

import androidx.room.TypeConverter
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import com.spiralsoft.servare.domain.model.ElementoEspacio
import com.spiralsoft.servare.domain.model.Participante
import com.spiralsoft.servare.domain.model.PuntoPoligonal
import com.spiralsoft.servare.domain.model.RegistroArchivos
import com.spiralsoft.servare.domain.model.Ritual

// Se guardan todos los campos, si cambia un valor por defecto, lo guardado no cambia.
// Al leer, un campo quitado se ignora y un enum que ya no existe toma el valor por defecto.
private val json = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
    coerceInputValues = true
}

class Converters {
    @TypeConverter fun fromInstant(v: Instant?): Long? = v?.toEpochMilli()
    @TypeConverter fun toInstant(v: Long?): Instant? = v?.let(Instant::ofEpochMilli)

    // ISO-8601 (2026-10-19, 14:30)
    @TypeConverter fun fromLocalDate(v: LocalDate?): String? = v?.toString()
    @TypeConverter fun toLocalDate(v: String?): LocalDate? = v?.let(LocalDate::parse)
    @TypeConverter fun fromLocalTime(v: LocalTime?): String? = v?.toString()
    @TypeConverter fun toLocalTime(v: String?): LocalTime? = v?.let(LocalTime::parse)

    // Partes de la ficha que nunca se consultan por separado: JSON
    @TypeConverter fun fromRegistro(v: RegistroArchivos): String = json.encodeToString(v)
    @TypeConverter fun toRegistro(v: String): RegistroArchivos = json.decodeFromString(v)
    @TypeConverter fun fromPoligonal(v: List<PuntoPoligonal>): String = json.encodeToString(v)
    @TypeConverter fun toPoligonal(v: String): List<PuntoPoligonal> = json.decodeFromString(v)
    @TypeConverter fun fromRituales(v: List<Ritual>): String = json.encodeToString(v)
    @TypeConverter fun toRituales(v: String): List<Ritual> = json.decodeFromString(v)
    @TypeConverter fun fromElementos(v: List<ElementoEspacio>): String = json.encodeToString(v)
    @TypeConverter fun toElementos(v: String): List<ElementoEspacio> = json.decodeFromString(v)
    @TypeConverter fun fromParticipantes(v: List<Participante>): String = json.encodeToString(v)
    @TypeConverter fun toParticipantes(v: String): List<Participante> = json.decodeFromString(v)
}
