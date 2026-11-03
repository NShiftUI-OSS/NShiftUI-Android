package com.nshiftui

import androidx.compose.runtime.Composable

class NShiftPluginScope(@PublishedApi internal val plugin: NShiftPlugin) {
    val model: NShiftPluginModel get() = plugin.model
    val resolver: NShiftDependencyResolver get() = plugin.resolver
    val eventHandler: NShiftEventHandler get() = plugin.eventHandler
    val engine: NShiftEngine? get() = plugin.engine
    val renderID: NShiftRenderID get() = plugin.renderID
    val viewModel: NShiftPluginViewModel? get() = plugin.viewModel

    inline fun <reified T : NShiftMetadata> metadata(): T = plugin.metadata()

    @Composable
    fun children(
        transform: @Composable (NShiftPluginModel, NShiftPluginNode) -> Unit = { _, node ->
            NShiftNodeView(node = node, engine = pluginEngineTree(), viewModel = viewModel)
        },
    ) {
        plugin.children(transform)
    }

    @Composable
    fun slots(
        name: NShiftSlotName,
        transform: @Composable (NShiftPluginModel, NShiftPluginNode) -> Unit = { _, node ->
            NShiftNodeView(node = node, engine = pluginEngineTree(), viewModel = viewModel)
        },
    ) {
        plugin.slots(name, transform)
    }

    @Composable
    fun <Key : NShiftSlotKey> slots(
        key: Key,
        transform: @Composable (NShiftPluginModel, NShiftPluginNode) -> Unit = { _, node ->
            NShiftNodeView(node = node, engine = pluginEngineTree(), viewModel = viewModel)
        },
    ) {
        plugin.slots(key, transform)
    }

    fun pluginEngineTree(): NShiftEngineTree =
        engine as? NShiftEngineTree
            ?: error("Plugin children/slots require an NShiftEngineTree")
}

class NShiftEventScope(@PublishedApi internal val event: NShiftEvent) {
    val model: NShiftEventModel get() = event.model
    val resolver: NShiftDependencyResolver get() = event.resolver

    inline fun <reified T : NShiftMetadata> metadata(): T = event.metadata()
}
