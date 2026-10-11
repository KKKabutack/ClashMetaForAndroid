package com.github.kr328.clash.core.util

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject

/**
 * Serializes a [JsonObject] natively when the target format is JSON
 * (so it embeds as a raw object in override JSON consumed by the Go side),
 * and as its JSON string representation for any other encoder
 * (so it survives [Parcelizer]-based Parcel marshalling).
 */
object JsonObjectCompatSerializer : KSerializer<JsonObject> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("JsonObjectCompat", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: JsonObject) {
        if (encoder is JsonEncoder) {
            encoder.encodeJsonElement(value)
        } else {
            encoder.encodeString(value.toString())
        }
    }

    override fun deserialize(decoder: Decoder): JsonObject {
        if (decoder is JsonDecoder) {
            return decoder.decodeJsonElement() as? JsonObject
                ?: throw SerializationException("Expected JsonObject")
        }
        return Json.parseToJsonElement(decoder.decodeString()) as? JsonObject
            ?: throw SerializationException("Expected JsonObject")
    }
}

object JsonObjectListCompatSerializer : KSerializer<List<JsonObject>> by ListSerializer(
    JsonObjectCompatSerializer
)
