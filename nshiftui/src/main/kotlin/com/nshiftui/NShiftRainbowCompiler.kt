package com.nshiftui

import com.nshiftui.rainbowparser.RainbowParser
import com.nshiftui.rainbowparser.ast.RainbowDocument
import com.nshiftui.rainbowparser.ast.RainbowNode
import com.nshiftui.rainbowparser.ast.RainbowParameter
import com.nshiftui.rainbowparser.ast.RainbowSlot
import com.nshiftui.rainbowparser.ast.RainbowTrigger
import com.nshiftui.rainbowparser.ast.RainbowUseDeclaration
import com.nshiftui.rainbowparser.ast.RainbowValue
data class NShiftRainbowCompileConfiguration(
    val slotMarkers: Set<String> = setOf("NavLeading", "NavCenter", "NavTrailing"),
    val defaultVersion: NShiftVersion? = null,
    val metadataDecoders: Map<String, NShiftRainbowMetadataDecoding> = emptyMap(),
    val catalog: NShiftRegisteredVersionCatalog = NShiftRegisteredVersionCatalog.shared,
) {
    fun registeringMetadataDecoder(
        decoder: NShiftRainbowMetadataDecoding,
    ): NShiftRainbowCompileConfiguration =
        copy(metadataDecoders = metadataDecoders + (decoder.pluginName to decoder))

    companion object {
        val default: NShiftRainbowCompileConfiguration = NShiftRainbowCompileConfiguration()
    }
}

class NShiftRainbowCompiler(
    private val configuration: NShiftRainbowCompileConfiguration = NShiftRainbowCompileConfiguration.default,
) {
    fun compile(source: String): NShiftPluginModel = compile(RainbowParser().decode(source))

    fun compile(document: RainbowDocument): NShiftPluginModel {
        val root = document.nodes.firstOrNull() ?: throw NShiftRainbowCompileError.EmptyDocument
        val pins = makePins(document.uses)
        return compilePlugin(root, pins)
    }

    fun compileAll(source: String): List<NShiftPluginModel> = compileAll(RainbowParser().decode(source))

    fun compileAll(document: RainbowDocument): List<NShiftPluginModel> {
        val pins = makePins(document.uses)
        return document.nodes.map { compilePlugin(it, pins) }
    }

    private fun makePins(uses: List<RainbowUseDeclaration>): NShiftDSLVersionPins =
        try {
            NShiftDSLVersionPins(uses.map { it.name to it.version })
        } catch (error: Throwable) {
            throw NShiftRainbowCompileError.InvalidVersion(error.message ?: error.toString())
        }

    private fun compilePlugin(node: RainbowNode, pins: NShiftDSLVersionPins): NShiftPluginModel {
        val pluginName = try {
            NShiftPluginName(node.name)
        } catch (_: NShiftPluginNameError) {
            throw NShiftRainbowCompileError.InvalidPluginName(node.name)
        }
        val version = resolveVersion(node.name, pins, isEvent = false)
        val children = node.children.map { compilePlugin(it, pins) }
        val slots = compileSlots(node.slots, pins)
        val events = compileModifierEvents(node.events, pins)
        val metadata = decodeMetadata(node.name, node.parameters)
        val style = decodeStyle(node.style)
        return NShiftPluginModel(
            id = node.id,
            name = pluginName,
            version = version,
            metadata = metadata,
            style = style,
            children = children,
            slots = slots,
            events = events,
        )
    }

    private fun compileSlots(
        slots: List<com.nshiftui.rainbowparser.ast.RainbowSlot>,
        pins: NShiftDSLVersionPins,
    ): Map<NShiftSlotName, List<NShiftPluginModel>> {
        val compiled = mutableMapOf<NShiftSlotName, MutableList<NShiftPluginModel>>()
        for (slot in slots) {
            val slotName = try {
                NShiftSlotName(slot.name)
            } catch (_: NShiftSlotNameError) {
                throw NShiftRainbowCompileError.InvalidSlotName(slot.name)
            }
            val plugins = slot.plugins.map { compilePlugin(it, pins) }
            compiled.getOrPut(slotName) { mutableListOf() }.addAll(plugins)
        }
        return compiled
    }

    private fun compileModifierEvents(
        triggers: List<com.nshiftui.rainbowparser.ast.RainbowTrigger>,
        pins: NShiftDSLVersionPins,
    ): List<NShiftEventModel> {
        val events = mutableListOf<NShiftEventModel>()
        for (triggerNode in triggers) {
            val trigger = triggerFrom(triggerNode.name)
                ?: throw NShiftRainbowCompileError.InvalidTriggerName(triggerNode.name)
            for (eventNode in triggerNode.events) {
                events.add(compileEvent(eventNode, trigger, pins))
            }
        }
        return events
    }

    private fun compileEvent(
        node: RainbowNode,
        trigger: NShiftTrigger,
        pins: NShiftDSLVersionPins,
    ): NShiftEventModel {
        val eventName = try {
            NShiftEventName(node.name)
        } catch (_: NShiftEventNameError) {
            throw NShiftRainbowCompileError.InvalidEventName(node.name)
        }
        val version = resolveVersion(node.name, pins, isEvent = true)
        val metadata = decodeMetadata(node.name, node.parameters)
        return NShiftEventModel(
            id = node.id ?: "",
            name = eventName,
            version = version,
            metadata = metadata,
            trigger = trigger,
            slots = compileSlots(node.slots, pins),
            events = compileModifierEvents(node.events, pins),
        )
    }

    private fun decodeMetadata(pluginName: String, parameters: List<RainbowParameter>): AnyNShiftMetadata? {
        val decoder = configuration.metadataDecoders[pluginName]
            ?: NShiftRainbowCatalog.decoder(pluginName)
            ?: return null
        return decoder.decode(parameters)
    }

    private fun decodeStyle(parameters: List<RainbowParameter>): NShiftPluginStyle {
        var width: Double? = null
        var height: Double? = null
        var minWidth: Double? = null
        var maxWidth: Double? = null
        var minHeight: Double? = null
        var maxHeight: Double? = null
        var background: NShiftPluginBackground? = null
        var ignoreSafeArea = NShiftSafeAreaEdges.none
        var offset = NShiftPluginOffset.zero
        var animation: NShiftPluginAnimation? = null
        for (parameter in parameters) {
            when (parameter.name) {
                "width" -> width = number(parameter.value)
                "height" -> height = number(parameter.value)
                "minWidth" -> minWidth = number(parameter.value)
                "maxWidth" -> maxWidth = number(parameter.value)
                "minHeight" -> minHeight = number(parameter.value)
                "maxHeight" -> maxHeight = number(parameter.value)
                "background" -> background = background(parameter.value)
                "ignoreSafeArea" -> ignoreSafeArea = edges(parameter.value)
                "offset" -> offset = offset(parameter.value)
                "animation" -> animation = animation(parameter.value)
            }
        }
        val frame = try {
            NShiftPluginFrame.of(
                width = width,
                height = height,
                minWidth = minWidth?.let { NShiftPluginDimension.Value(it) },
                maxWidth = maxWidth?.let { NShiftPluginDimension.Value(it) },
                minHeight = minHeight?.let { NShiftPluginDimension.Value(it) },
                maxHeight = maxHeight?.let { NShiftPluginDimension.Value(it) },
            )
        } catch (_: NShiftPluginFrameError) {
            NShiftPluginFrame.empty()
        }
        return NShiftPluginStyle(
            frame = frame,
            background = background,
            ignoreSafeArea = ignoreSafeArea,
            offset = offset,
            animation = animation,
        )
    }

    private fun number(value: RainbowValue): Double? =
        when (value) {
            is RainbowValue.IntValue -> value.value.toDouble()
            is RainbowValue.DoubleValue -> value.value
            else -> null
        }

    private fun token(value: RainbowValue): String? =
        when (value) {
            is RainbowValue.Identifier -> value.value.removePrefix(".")
            is RainbowValue.StringValue -> value.value.removePrefix(".")
            else -> null
        }

    private fun edges(value: RainbowValue): NShiftSafeAreaEdges =
        when (value) {
            is RainbowValue.ArrayValue -> NShiftSafeAreaEdges.parse(value.values.mapNotNull(::token))
            else -> NShiftSafeAreaEdges.parse(listOfNotNull(token(value)))
        }

    private fun background(value: RainbowValue): NShiftPluginBackground? {
        val entries = (value as? RainbowValue.ObjectValue)?.entries ?: return null
        var color: String? = null
        var ignoreSafeArea = NShiftSafeAreaEdges.none
        for (entry in entries) {
            when (entry.key) {
                "color" -> color = token(entry.value)
                "ignoreSafeArea" -> ignoreSafeArea = edges(entry.value)
            }
        }
        if (color == null && ignoreSafeArea.isEmpty) return null
        return NShiftPluginBackground(color = color, ignoreSafeArea = ignoreSafeArea)
    }

    private fun offset(value: RainbowValue): NShiftPluginOffset {
        val entries = (value as? RainbowValue.ObjectValue)?.entries ?: return NShiftPluginOffset.zero
        var x = 0.0
        var y = 0.0
        for (entry in entries) {
            when (entry.key) {
                "x" -> x = number(entry.value) ?: x
                "y" -> y = number(entry.value) ?: y
            }
        }
        return NShiftPluginOffset(x = x, y = y)
    }

    private fun animation(value: RainbowValue): NShiftPluginAnimation? {
        val entries = (value as? RainbowValue.ObjectValue)?.entries ?: return null
        var isEnabled = false
        var durationMillis = 300
        var delayMillis = 0
        var curve = NShiftAnimationCurve.EaseInOut
        for (entry in entries) {
            when (entry.key) {
                "isEnabled" -> isEnabled = (entry.value as? RainbowValue.BoolValue)?.value ?: isEnabled
                "duration" -> durationMillis = number(entry.value)?.toInt() ?: durationMillis
                "delay" -> delayMillis = number(entry.value)?.toInt() ?: delayMillis
                "curve" -> {
                    curve = when (token(entry.value)) {
                        "linear" -> NShiftAnimationCurve.Linear
                        "easeIn" -> NShiftAnimationCurve.EaseIn
                        "easeOut" -> NShiftAnimationCurve.EaseOut
                        else -> NShiftAnimationCurve.EaseInOut
                    }
                }
            }
        }
        return NShiftPluginAnimation(
            isEnabled = isEnabled,
            durationMillis = durationMillis,
            delayMillis = delayMillis,
            curve = curve,
        )
    }

    private fun resolveVersion(name: String, pins: NShiftDSLVersionPins, isEvent: Boolean): NShiftVersion {
        if (isEvent) {
            val eventName = try {
                NShiftEventName(name)
            } catch (_: NShiftEventNameError) {
                throw NShiftRainbowCompileError.InvalidEventName(name)
            }
            pins.pinnedVersion(eventName)?.let { return it }
            configuration.catalog.latestEvent(eventName)?.let { return it }
            configuration.defaultVersion?.let { return it }
            throw NShiftRainbowCompileError.MissingVersion(name)
        }
        val pluginName = try {
            NShiftPluginName(name)
        } catch (_: NShiftPluginNameError) {
            throw NShiftRainbowCompileError.InvalidPluginName(name)
        }
        pins.pinnedVersion(pluginName)?.let { return it }
        configuration.catalog.latestPlugin(pluginName)?.let { return it }
        configuration.defaultVersion?.let { return it }
        throw NShiftRainbowCompileError.MissingVersion(name)
    }

    private fun triggerFrom(nodeName: String): NShiftTrigger? {
        if (!nodeName.startsWith("On") || nodeName.length <= 2) {
            return null
        }
        val rawValue = "on" + nodeName.drop(2)
        return try {
            NShiftTrigger(rawValue)
        } catch (_: NShiftTriggerError) {
            null
        }
    }
}
