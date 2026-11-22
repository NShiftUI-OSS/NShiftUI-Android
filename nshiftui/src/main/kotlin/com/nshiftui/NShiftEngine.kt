package com.nshiftui

interface NShiftEngine {
    val rootID: String
    var pluginViewModel: NShiftPluginViewModel?

    suspend fun handle(trigger: NShiftTrigger, pluginID: String)

    fun replaceMetadata(pluginID: String, metadata: AnyNShiftMetadata?)
    fun replacePlugin(pluginID: String, model: NShiftPluginModel)
    fun replaceChildren(pluginID: String, children: List<NShiftPluginModel>)
    fun replaceSlots(pluginID: String, slots: Map<NShiftSlotName, List<NShiftPluginModel>>)
    fun replaceSlot(pluginID: String, name: NShiftSlotName, models: List<NShiftPluginModel>)
    fun replaceEvents(pluginID: String, events: List<NShiftEventModel>)
    fun replaceEvent(eventID: String, event: NShiftEventModel)
}

inline fun <reified T : NShiftPluginViewModel> NShiftEngine.optionalPluginViewModel(): T? =
    pluginViewModel as? T

interface NShiftEngineTree : NShiftEngine {
    val root: NShiftPluginNode

    fun node(id: String): NShiftPluginNode?

    fun node(renderID: NShiftRenderID): NShiftPluginNode?

    fun makeView(
        node: NShiftPluginNode,
        viewModel: NShiftPluginViewModel? = null,
    ): (@androidx.compose.runtime.Composable () -> Unit)?

    /** Inserts [children] at [index] keeping the sibling nodes before it. */
    fun insertChildren(pluginID: String, index: Int, children: List<NShiftPluginModel>) {
        val node = node(pluginID) ?: return
        val next = node.model.children.toMutableList()
        next.addAll(index.coerceIn(0, next.size), children)
        replaceChildren(pluginID, next)
    }

    /** Appends [models] to the slot [name] keeping the existing slot nodes. */
    fun appendSlot(pluginID: String, name: NShiftSlotName, models: List<NShiftPluginModel>) {
        val node = node(pluginID) ?: return
        replaceSlot(pluginID, name, node.model.slots[name].orEmpty() + models)
    }
}
