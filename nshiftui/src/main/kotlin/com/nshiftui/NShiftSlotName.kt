package com.nshiftui

import com.nshiftui.internal.NShiftASCII

data class NShiftSlotName(val rawValue: String) {
    init {
        if (!isValid(rawValue)) {
            throw NShiftSlotNameError.InvalidName(rawValue)
        }
    }

    override fun toString(): String = rawValue

    companion object {
        fun isValid(value: String): Boolean = NShiftASCII.isPascalCaseASCII(value)
    }
}

sealed class NShiftSlotNameError : Exception() {
    data class InvalidName(val name: String) : NShiftSlotNameError() {
        override val message: String = "Invalid slot name: $name"
    }
}
