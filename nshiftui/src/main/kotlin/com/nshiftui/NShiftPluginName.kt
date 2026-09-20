package com.nshiftui

import com.nshiftui.internal.NShiftASCII

data class NShiftPluginName(val rawValue: String) {
    init {
        if (!isValid(rawValue)) {
            throw NShiftPluginNameError.InvalidName(rawValue)
        }
    }

    override fun toString(): String = rawValue

    companion object {
        fun isValid(value: String): Boolean = NShiftASCII.isPascalCaseASCII(value)
    }
}

sealed class NShiftPluginNameError : Exception() {
    data class InvalidName(val name: String) : NShiftPluginNameError() {
        override val message: String = "Invalid plugin name: $name"
    }
}
