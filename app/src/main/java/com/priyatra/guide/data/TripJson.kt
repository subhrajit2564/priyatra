package com.priyatra.guide.data

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import java.lang.reflect.Type
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object TripJson {
    val gson: Gson = GsonBuilder()
        .registerTypeAdapter(LocalDate::class.java, LocalDateAdapter)
        .registerTypeAdapter(LocalDateTime::class.java, LocalDateTimeAdapter)
        .registerTypeAdapter(ZoneId::class.java, ZoneIdAdapter)
        .create()

    private object LocalDateAdapter : JsonSerializer<LocalDate>, JsonDeserializer<LocalDate> {
        override fun serialize(
            src: LocalDate,
            typeOfSrc: Type,
            context: JsonSerializationContext,
        ): JsonPrimitive = JsonPrimitive(src.toString())

        override fun deserialize(
            json: JsonElement,
            typeOfT: Type,
            context: JsonDeserializationContext,
        ): LocalDate {
            if (!json.isJsonPrimitive) throw JsonParseException("Expected string date")
            return LocalDate.parse(json.asString)
        }
    }

    private object LocalDateTimeAdapter : JsonSerializer<LocalDateTime>, JsonDeserializer<LocalDateTime> {
        private val fmt: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

        override fun serialize(
            src: LocalDateTime,
            typeOfSrc: Type,
            context: JsonSerializationContext,
        ): JsonPrimitive = JsonPrimitive(src.format(fmt))

        override fun deserialize(
            json: JsonElement,
            typeOfT: Type,
            context: JsonDeserializationContext,
        ): LocalDateTime {
            if (!json.isJsonPrimitive) throw JsonParseException("Expected string datetime")
            return LocalDateTime.parse(json.asString, fmt)
        }
    }

    private object ZoneIdAdapter : JsonSerializer<ZoneId>, JsonDeserializer<ZoneId> {
        override fun serialize(
            src: ZoneId,
            typeOfSrc: Type,
            context: JsonSerializationContext,
        ): JsonPrimitive = JsonPrimitive(src.id)

        override fun deserialize(
            json: JsonElement,
            typeOfT: Type,
            context: JsonDeserializationContext,
        ): ZoneId = ZoneId.of(json.asString)
    }
}
