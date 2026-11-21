package com.nshiftui

import com.nshiftui.rainbowparser.ast.RainbowParameter
import kotlin.reflect.KClass
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.serializer

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class NShiftJSONTagged(val parameters: Array<String>)

object NShiftRainbowCatalog {
    private val decoders = linkedMapOf<String, NShiftRainbowMetadataDecoding>()
    private val slotMarkers = linkedSetOf<String>()
    private val json = Json { ignoreUnknownKeys = true }

    fun adopt(name: String, metadata: KClass<out NShiftMetadata>?, slots: KClass<*>?) {
        if (metadata != null && metadata != NShiftEmptyMetadata::class) {
            decoders[name] = SerializableMetadataDecoder(name, metadata)
        }
        if (slots != null && slots != Unit::class) {
            val constants = slots.java.enumConstants ?: return
            for (constant in constants) {
                if (constant is NShiftSlotKey) {
                    slotMarkers += constant.slotName.rawValue
                }
            }
        }
    }

    fun decoder(name: String): NShiftRainbowMetadataDecoding? = decoders[name]

    fun isSlot(name: String): Boolean = slotMarkers.contains(name)

    fun resetForTesting() {
        decoders.clear()
        slotMarkers.clear()
    }

    @OptIn(InternalSerializationApi::class)
    private class SerializableMetadataDecoder(
        override val pluginName: String,
        private val metadata: KClass<out NShiftMetadata>,
    ) : NShiftRainbowMetadataDecoding {
        override fun decode(parameters: List<RainbowParameter>): AnyNShiftMetadata? {
            return try {
                val tagged = metadata.java.getAnnotation(NShiftJSONTagged::class.java)
                    ?.parameters
                    ?.toSet()
                    .orEmpty()
                val element = NShiftRainbowJSON.dictionary(parameters, tagged).toJsonElement()
                @Suppress("UNCHECKED_CAST")
                val value = json.decodeFromJsonElement(
                    (metadata as KClass<NShiftMetadata>).serializer(),
                    element,
                )
                AnyNShiftMetadata(value)
            } catch (error: Throwable) {
                if (parameters.isEmpty()) null else throw error
            }
        }
    }
}

private fun Any?.toJsonElement(): JsonElement =
    when (this) {
        null -> JsonNull
        is String -> JsonPrimitive(this)
        is Boolean -> JsonPrimitive(this)
        is Int -> JsonPrimitive(this)
        is Long -> JsonPrimitive(this)
        is Double -> JsonPrimitive(this)
        is Float -> JsonPrimitive(this.toDouble())
        is Map<*, *> -> JsonObject(
            entries.mapNotNull { (key, value) ->
                (key as? String)?.let { it to value.toJsonElement() }
            }.toMap(),
        )
        is List<*> -> JsonArray(map { it.toJsonElement() })
        else -> JsonPrimitive(toString())
    }
