package com.nshiftui

import com.nshiftui.internal.NShiftASCII

data class NShiftEventName(val rawValue: String) {
    init {
        if (!isValid(rawValue)) {
            throw NShiftEventNameError.InvalidName(rawValue)
        }
    }

    override fun toString(): String = rawValue

    companion object {
        fun isValid(value: String): Boolean = NShiftASCII.isPascalCaseASCII(value)
    }
}

sealed class NShiftEventNameError : Exception() {
    data class InvalidName(val name: String) : NShiftEventNameError() {
        override val message: String = "Invalid event name: $name"
    }
}
