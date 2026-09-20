package com.nshiftui

import java.util.UUID

data class NShiftEventModel(
    val id: String = UUID.randomUUID().toString(),
    val name: NShiftEventName,
    val version: NShiftVersion,
    val metadata: AnyNShiftMetadata? = null,
    val trigger: NShiftTrigger,
    val slots: Map<NShiftSlotName, List<NShiftPluginModel>> = emptyMap(),
    val events: List<NShiftEventModel> = emptyList(),
) {
    val registrationName: String
        get() = NShiftVersion.registrationName(name, version)

    constructor(
        id: String = UUID.randomUUID().toString(),
        name: NShiftEventName,
        version: NShiftVersion,
        metadata: NShiftMetadata,
        trigger: NShiftTrigger,
        slots: Map<NShiftSlotName, List<NShiftPluginModel>> = emptyMap(),
        events: List<NShiftEventModel> = emptyList(),
    ) : this(
        id = id,
        name = name,
        version = version,
        metadata = AnyNShiftMetadata(metadata),
        trigger = trigger,
        slots = slots,
        events = events,
    )
}
