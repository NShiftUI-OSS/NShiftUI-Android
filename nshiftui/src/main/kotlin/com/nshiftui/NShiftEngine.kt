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

interface NShiftEngineTree : NShiftEngine {
    val root: NShiftPluginNode

    fun node(id: String): NShiftPluginNode?

    fun node(renderID: NShiftRenderID): NShiftPluginNode?

    fun makeView(
        node: NShiftPluginNode,
        viewModel: NShiftPluginViewModel? = null,
    ): (@androidx.compose.runtime.Composable () -> Unit)?
}
