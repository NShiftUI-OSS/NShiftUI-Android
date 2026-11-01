package com.nshiftui

import com.nshiftui.internal.NShiftASCII

data class NShiftTrigger(val rawValue: String) {
    init {
        if (!isValid(rawValue)) {
            throw NShiftTriggerError.InvalidTrigger(rawValue)
        }
    }

    override fun toString(): String = rawValue

    companion object {
        fun isValid(value: String): Boolean {
            if (value.length <= 2 || !value.startsWith("on")) {
                return false
            }
            if (!value.all { NShiftASCII.isASCIILetter(it) || NShiftASCII.isASCIIDigit(it) }) {
                return false
            }

            var index = 2
            while (index < value.length) {
                if (!NShiftASCII.isUppercaseASCIILetter(value[index])) {
                    return false
                }
                index += 1
                while (index < value.length && NShiftASCII.isLowercaseASCIILetter(value[index])) {
                    index += 1
                }
                while (index < value.length && NShiftASCII.isASCIIDigit(value[index])) {
                    index += 1
                }
            }
            return true
        }
    }
}

sealed class NShiftTriggerError : Exception() {
    data class InvalidTrigger(val trigger: String) : NShiftTriggerError() {
        override val message: String = "Invalid trigger: $trigger"
    }
}
