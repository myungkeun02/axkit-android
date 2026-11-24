package dev.axkit.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

/** Every field the server may leave out is optional and unknown fields are ignored, so a newer server keeps working. */
val ProtocolJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
    isLenient = true
    encodeDefaults = true
}

object ISODates {
    val seoul: ZoneId = ZoneId.of("Asia/Seoul")

    /** ISO 8601 with or without fractions or an offset; a bare `YYYY-MM-DD` is midnight in Seoul. */
    fun parse(text: String?): Instant? {
        if (text.isNullOrBlank()) return null
        runCatching { return Instant.parse(text) }
        runCatching { return OffsetDateTime.parse(text).toInstant() }
        runCatching { return LocalDate.parse(text).atStartOfDay(seoul).toInstant() }
        return null
    }

    fun format(instant: Instant): String = instant.toString()
}

object InstantSerializer : KSerializer<Instant> {
    override val descriptor = PrimitiveSerialDescriptor("Instant", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): Instant =
        ISODates.parse(decoder.decodeString()) ?: throw IllegalArgumentException("date")
    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeString(value.toString())
}

typealias IsoInstant = @kotlinx.serialization.Serializable(with = InstantSerializer::class) Instant
