package com.nshiftui

data class NShiftPluginModel(
    val id: String? = null,
    val name: NShiftPluginName,
    val version: NShiftVersion,
    val metadata: AnyNShiftMetadata? = null,
    val style: NShiftPluginStyle = NShiftPluginStyle(),
    val children: List<NShiftPluginModel> = emptyList(),
    val slots: Map<NShiftSlotName, List<NShiftPluginModel>> = emptyMap(),
    val events: List<NShiftEventModel> = emptyList(),
) {
    val registrationName: String
        get() = NShiftVersion.registrationName(name, version)

    constructor(
        id: String? = null,
        name: NShiftPluginName,
        version: NShiftVersion,
        metadata: NShiftMetadata,
        style: NShiftPluginStyle = NShiftPluginStyle(),
        children: List<NShiftPluginModel> = emptyList(),
        slots: Map<NShiftSlotName, List<NShiftPluginModel>> = emptyMap(),
        events: List<NShiftEventModel> = emptyList(),
    ) : this(
        id = id,
        name = name,
        version = version,
        metadata = AnyNShiftMetadata(metadata),
        style = style,
        children = children,
        slots = slots,
        events = events,
    )

    fun replacingMetadata(metadata: AnyNShiftMetadata?): NShiftPluginModel =
        copy(metadata = metadata)

    fun replacingEvents(events: List<NShiftEventModel>): NShiftPluginModel =
        copy(events = events)

    fun replacingChildren(children: List<NShiftPluginModel>): NShiftPluginModel =
        copy(children = children)

    fun replacingSlots(slots: Map<NShiftSlotName, List<NShiftPluginModel>>): NShiftPluginModel =
        copy(slots = slots)

    fun replacingSlot(name: NShiftSlotName, models: List<NShiftPluginModel>): NShiftPluginModel {
        val next = slots.toMutableMap()
        next[name] = models
        return replacingSlots(next)
    }
}
