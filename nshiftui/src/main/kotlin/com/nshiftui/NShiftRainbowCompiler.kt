package com.nshiftui

import com.nshiftui.rainbowparser.RainbowParser
import com.nshiftui.rainbowparser.ast.RainbowDocument
import com.nshiftui.rainbowparser.ast.RainbowNode
import com.nshiftui.rainbowparser.ast.RainbowParameter
import com.nshiftui.rainbowparser.ast.RainbowUseDeclaration
import com.nshiftui.rainbowparser.ast.RainbowValue
import java.util.UUID

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
        val partitioned = partitionChildren(node.children, pins)
        val metadata = decodeMetadata(node.name, node.parameters)
        val id = stringParameter("id", node.parameters)
        val style = decodeStyle(node.parameters)
        return NShiftPluginModel(
            id = id,
            name = pluginName,
            version = version,
            metadata = metadata,
            style = style,
            children = partitioned.children,
            slots = partitioned.slots,
            events = partitioned.events,
        )
    }

    private data class Partitioned(
        val children: List<NShiftPluginModel>,
        val slots: Map<NShiftSlotName, List<NShiftPluginModel>>,
        val events: List<NShiftEventModel>,
    )

    private fun partitionChildren(nodes: List<RainbowNode>, pins: NShiftDSLVersionPins): Partitioned {
        val children = mutableListOf<NShiftPluginModel>()
        val slots = mutableMapOf<NShiftSlotName, MutableList<NShiftPluginModel>>()
        val events = mutableListOf<NShiftEventModel>()
        for (node in nodes) {
            if (configuration.slotMarkers.contains(node.name)) {
                val slotName = try {
                    NShiftSlotName(node.name)
                } catch (_: NShiftSlotNameError) {
                    throw NShiftRainbowCompileError.InvalidSlotName(node.name)
                }
                val compiled = node.children.map { compilePlugin(it, pins) }
                slots.getOrPut(slotName) { mutableListOf() }.addAll(compiled)
                continue
            }
            val trigger = triggerFrom(node.name)
            if (trigger != null) {
                events.addAll(compileEvents(node, trigger, pins))
                continue
            }
            children.add(compilePlugin(node, pins))
        }
        return Partitioned(children, slots, events)
    }

    private fun compileEvents(
        triggerNode: RainbowNode,
        trigger: NShiftTrigger,
        pins: NShiftDSLVersionPins,
    ): List<NShiftEventModel> =
        triggerNode.children.map { compileEvent(it, trigger, pins) }

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
        val nestedEvents = mutableListOf<NShiftEventModel>()
        val slots = mutableMapOf<NShiftSlotName, MutableList<NShiftPluginModel>>()
        for (child in node.children) {
            val nestedTrigger = triggerFrom(child.name)
            if (nestedTrigger != null) {
                nestedEvents.addAll(compileEvents(child, nestedTrigger, pins))
                continue
            }
            if (configuration.slotMarkers.contains(child.name)) {
                val slotName = try {
                    NShiftSlotName(child.name)
                } catch (_: NShiftSlotNameError) {
                    throw NShiftRainbowCompileError.InvalidSlotName(child.name)
                }
                val compiled = child.children.map { compilePlugin(it, pins) }
                slots.getOrPut(slotName) { mutableListOf() }.addAll(compiled)
                continue
            }
            nestedEvents.add(compileEvent(child, trigger, pins))
        }
        val metadata = decodeMetadata(node.name, node.parameters)
        val id = stringParameter("id", node.parameters) ?: UUID.randomUUID().toString()
        return NShiftEventModel(
            id = id,
            name = eventName,
            version = version,
            metadata = metadata,
            trigger = trigger,
            slots = slots,
            events = nestedEvents,
        )
    }

    private fun decodeMetadata(pluginName: String, parameters: List<RainbowParameter>): AnyNShiftMetadata? {
        val decoder = configuration.metadataDecoders[pluginName] ?: return null
        return decoder.decode(parameters)
    }

    private fun decodeStyle(parameters: List<RainbowParameter>): NShiftPluginStyle {
        val styleParameter = parameters.firstOrNull { it.name == "style" } ?: return NShiftPluginStyle()
        val entries = (styleParameter.value as? RainbowValue.ObjectValue)?.entries ?: return NShiftPluginStyle()
        var spacing = 0.0
        for (entry in entries) {
            if (entry.key == "spacing") {
                spacing = when (val value = entry.value) {
                    is RainbowValue.IntValue -> value.value.toDouble()
                    is RainbowValue.DoubleValue -> value.value
                    else -> spacing
                }
            }
        }
        return NShiftPluginStyle(spacing = spacing)
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

    private fun stringParameter(name: String, parameters: List<RainbowParameter>): String? {
        val parameter = parameters.firstOrNull { it.name == name } ?: return null
        return (parameter.value as? RainbowValue.StringValue)?.value
    }
}
