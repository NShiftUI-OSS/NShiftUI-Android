package com.nshiftui

import com.nshiftui.rainbowparser.ast.RainbowParameter
import com.nshiftui.rainbowparser.ast.RainbowValue

sealed class NShiftRainbowCompileError : Exception() {
    data object EmptyDocument : NShiftRainbowCompileError()
    data class InvalidPluginName(val name: String) : NShiftRainbowCompileError()
    data class InvalidEventName(val name: String) : NShiftRainbowCompileError()
    data class InvalidSlotName(val name: String) : NShiftRainbowCompileError()
    data class InvalidTriggerName(val name: String) : NShiftRainbowCompileError()
    data class InvalidVersion(val version: String) : NShiftRainbowCompileError()
    data class MissingVersion(val name: String) : NShiftRainbowCompileError()
    data class MetadataDecodingFailed(val plugin: String, val failure: String) : NShiftRainbowCompileError()
    data class UnsupportedTaggedValue(val language: String) : NShiftRainbowCompileError()

    override val message: String
        get() =
            when (this) {
                EmptyDocument -> "Rainbow document has no root nodes"
                is InvalidPluginName -> "Invalid plugin name: $name"
                is InvalidEventName -> "Invalid event name: $name"
                is InvalidSlotName -> "Invalid slot name: $name"
                is InvalidTriggerName -> "Invalid trigger name: $name"
                is InvalidVersion -> "Invalid version: $version"
                is MissingVersion ->
                    "Missing version pin for $name (add use $name@x.y.z or register in catalog)"
                is MetadataDecodingFailed -> "Failed to decode metadata for $plugin: $failure"
                is UnsupportedTaggedValue -> "Unsupported tagged value @$language in Rainbow parameters"
            }
}

object NShiftRainbowJSON {
    fun dictionary(from: List<RainbowParameter>): Map<String, Any?> {
        val result = linkedMapOf<String, Any?>()
        for (parameter in from) {
            if (parameter.name == "id" || parameter.name == "style") {
                continue
            }
            result[parameter.name] = jsonValue(parameter.value)
        }
        return result
    }

    fun jsonValue(value: RainbowValue): Any? =
        when (value) {
            is RainbowValue.StringValue -> value.value
            is RainbowValue.IntValue ->
                if (value.value in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) {
                    value.value.toInt()
                } else {
                    value.value
                }
            is RainbowValue.DoubleValue -> value.value
            is RainbowValue.BoolValue -> value.value
            is RainbowValue.Identifier -> value.value
            RainbowValue.Null -> null
            is RainbowValue.ArrayValue -> value.values.map(::jsonValue)
            is RainbowValue.ObjectValue -> {
                val objectValue = linkedMapOf<String, Any?>()
                for (entry in value.entries) {
                    objectValue[entry.key] = jsonValue(entry.value)
                }
                objectValue
            }
            is RainbowValue.Tagged ->
                throw NShiftRainbowCompileError.UnsupportedTaggedValue(value.language.rawValue)
        }

    fun <T> decode(from: List<RainbowParameter>, mapper: (Map<String, Any?>) -> T): T =
        mapper(dictionary(from))
}

interface NShiftRainbowMetadataDecoding {
    val pluginName: String

    fun decode(parameters: List<RainbowParameter>): AnyNShiftMetadata?
}

class NShiftRainbowMapMetadataDecoder(
    override val pluginName: String,
    private val mapper: (Map<String, Any?>) -> NShiftMetadata,
) : NShiftRainbowMetadataDecoding {
    override fun decode(parameters: List<RainbowParameter>): AnyNShiftMetadata? {
        if (parameters.isEmpty()) {
            return null
        }
        return try {
            AnyNShiftMetadata(mapper(NShiftRainbowJSON.dictionary(parameters)))
        } catch (error: Throwable) {
            throw NShiftRainbowCompileError.MetadataDecodingFailed(pluginName, error.message ?: error.toString())
        }
    }
}
