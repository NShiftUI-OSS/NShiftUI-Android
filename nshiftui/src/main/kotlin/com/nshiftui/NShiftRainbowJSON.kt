package com.nshiftui

import com.nshiftui.rainbowparser.ast.EmbeddedLanguage
import com.nshiftui.rainbowparser.ast.RainbowParameter
import com.nshiftui.rainbowparser.ast.RainbowTaggedBody
import com.nshiftui.rainbowparser.ast.RainbowValue

data class NShiftRainbowMetadataDecodingContext(
    val compilePlugins: (String) -> List<NShiftPluginModel>,
    /**
     * The plugin's children as a standalone Rainbow document (with the screen's
     * `use` declarations). Template plugins such as a `ForEach` keep it and
     * compile it later, once per data item.
     */
    val childrenSource: () -> String = { "" },
)

data class NShiftRainbowMetadataDecodeResult(
    val metadata: AnyNShiftMetadata?,
    val slots: Map<NShiftSlotName, List<NShiftPluginModel>> = emptyMap(),
    /** When true the compiler skips the children: the plugin consumed them as a template. */
    val consumesChildren: Boolean = false,
)

interface NShiftRainbowPluginMetadata : NShiftMetadata {
    fun decodeRainbowMetadata(
        context: NShiftRainbowMetadataDecodingContext,
    ): NShiftRainbowMetadataDecodeResult
}

sealed class NShiftRainbowCompileError : Exception() {
    data object EmptyDocument : NShiftRainbowCompileError()
    data class InvalidPluginName(val name: String) : NShiftRainbowCompileError()
    data class InvalidEventName(val name: String) : NShiftRainbowCompileError()
    data class InvalidSlotName(val name: String) : NShiftRainbowCompileError()
    data class InvalidTriggerName(val name: String) : NShiftRainbowCompileError()
    data class InvalidVersion(val version: String) : NShiftRainbowCompileError()
    data class MissingVersion(val name: String) : NShiftRainbowCompileError()
    data class MetadataDecodingFailed(val plugin: String, val failure: String) : NShiftRainbowCompileError()
    data class TaggedJSONRequired(val name: String) : NShiftRainbowCompileError()
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
                is TaggedJSONRequired -> "Parameter $name must be a @JSON(...) value"
                is UnsupportedTaggedValue -> "Unsupported tagged value @$language in Rainbow parameters"
            }
}

object NShiftRainbowJSON {
    fun dictionary(
        from: List<RainbowParameter>,
        jsonTaggedParameters: Set<String> = emptySet(),
    ): Map<String, Any?> {
        val result = linkedMapOf<String, Any?>()
        for (parameter in from) {
            if (parameter.value is RainbowValue.Plugins) {
                continue
            }
            if (parameter.name in jsonTaggedParameters) {
                val tagged = parameter.value as? RainbowValue.Tagged
                val text = (tagged?.body as? RainbowTaggedBody.Text)?.value
                if (tagged?.language != EmbeddedLanguage.JSON || text == null) {
                    throw NShiftRainbowCompileError.TaggedJSONRequired(parameter.name)
                }
                result[parameter.name] = text
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
                    if (entry.value is RainbowValue.Plugins) {
                        continue
                    }
                    objectValue[entry.key] = jsonValue(entry.value)
                }
                objectValue
            }
            is RainbowValue.Tagged ->
                (value.body as? RainbowTaggedBody.Text)?.value
                    ?: throw NShiftRainbowCompileError.UnsupportedTaggedValue(value.language.rawValue)
            is RainbowValue.Plugins ->
                throw NShiftRainbowCompileError.UnsupportedTaggedValue("plugins")
        }

    fun <T> decode(from: List<RainbowParameter>, mapper: (Map<String, Any?>) -> T): T =
        mapper(dictionary(from))
}

interface NShiftRainbowMetadataDecoding {
    val pluginName: String

    fun decode(parameters: List<RainbowParameter>): AnyNShiftMetadata?

    fun decode(
        parameters: List<RainbowParameter>,
        context: NShiftRainbowMetadataDecodingContext,
    ): NShiftRainbowMetadataDecodeResult =
        NShiftRainbowMetadataDecodeResult(decode(parameters))
}

class NShiftRainbowMapMetadataDecoder(
    override val pluginName: String,
    private val jsonTaggedParameters: Set<String> = emptySet(),
    private val mapper: (Map<String, Any?>) -> NShiftMetadata,
) : NShiftRainbowMetadataDecoding {
    override fun decode(parameters: List<RainbowParameter>): AnyNShiftMetadata? {
        return decode(
            parameters = parameters,
            context = NShiftRainbowMetadataDecodingContext(compilePlugins = { emptyList() }),
        ).metadata
    }

    override fun decode(
        parameters: List<RainbowParameter>,
        context: NShiftRainbowMetadataDecodingContext,
    ): NShiftRainbowMetadataDecodeResult {
        if (parameters.isEmpty()) {
            return NShiftRainbowMetadataDecodeResult(metadata = null)
        }
        return try {
            val value = mapper(NShiftRainbowJSON.dictionary(parameters, jsonTaggedParameters))
            if (value is NShiftRainbowPluginMetadata) {
                return value.decodeRainbowMetadata(context)
            }
            NShiftRainbowMetadataDecodeResult(
                metadata = AnyNShiftMetadata(value),
            )
        } catch (error: NShiftRainbowCompileError) {
            throw error
        } catch (error: Throwable) {
            throw NShiftRainbowCompileError.MetadataDecodingFailed(pluginName, error.message ?: error.toString())
        }
    }
}
