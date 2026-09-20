package com.nshiftui

data class NShiftRenderID(val rawValue: String) {
    override fun toString(): String = rawValue

    fun appendingChild(index: Int): NShiftRenderID =
        NShiftRenderID("$rawValue/c/$index")

    fun appendingSlot(name: NShiftSlotName, index: Int): NShiftRenderID =
        NShiftRenderID("$rawValue/s/${name.rawValue}/$index")

    companion object {
        val root = NShiftRenderID("r")
    }
}
