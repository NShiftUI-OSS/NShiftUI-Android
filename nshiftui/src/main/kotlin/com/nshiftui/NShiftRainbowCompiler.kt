package com.nshiftui

import com.nshiftui.rainbowparser.RainbowParser
import com.nshiftui.rainbowparser.ast.RainbowDocument
import com.nshiftui.rainbowparser.ast.RainbowNode
import com.nshiftui.rainbowparser.ast.RainbowParameter
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
        var spacing = 0.0
        var alignment = NShiftPluginAlignment.Center
        for (parameter in parameters) {
            when (parameter.name) {
                "spacing" -> {
                    spacing = when (val value = parameter.value) {
                        is RainbowValue.IntValue -> value.value.toDouble()
                        is RainbowValue.DoubleValue -> value.value
                        else -> spacing
                    }
                }
                "alignment" -> {
                    val name = (parameter.value as? RainbowValue.Identifier)?.value?.removePrefix(".")
                    alignment = NShiftPluginAlignment.entries.firstOrNull { entry ->
                        entry.name.replaceFirstChar { it.lowercase() } == name
                    } ?: alignment
                }
            }
        }
        return NShiftPluginStyle(alignment = alignment, spacing = spacing)
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
