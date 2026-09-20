package com.nshiftui

import com.nshiftui.internal.NShiftASCII

interface NShiftSlotKey {
    val rawValue: String

    val slotName: NShiftSlotName
        get() = NShiftSlotName(NShiftASCII.pascalCaseFromCamelCase(rawValue))
}

data class NShiftSlotMap<Key : NShiftSlotKey>(
    val storage: Map<NShiftSlotName, List<NShiftPluginModel>> = emptyMap(),
) {
    operator fun get(key: Key): List<NShiftPluginModel> = storage[key.slotName].orEmpty()

    val untyped: Map<NShiftSlotName, List<NShiftPluginModel>>
        get() = storage
}
