package com.nshiftui.internal

internal object NShiftASCII {
    fun isUppercaseASCIILetter(char: Char): Boolean = char.code in 65..90

    fun isLowercaseASCIILetter(char: Char): Boolean = char.code in 97..122

    fun isASCIILetter(char: Char): Boolean =
        isUppercaseASCIILetter(char) || isLowercaseASCIILetter(char)

    fun isASCIIDigit(char: Char): Boolean = char.code in 48..57

    fun isPascalCaseASCII(value: String): Boolean {
        val first = value.firstOrNull() ?: return false
        if (!isUppercaseASCIILetter(first)) {
            return false
        }
        return value.all(::isASCIILetter)
    }

    fun pascalCaseFromCamelCase(value: String): String {
        val first = value.firstOrNull() ?: return value
        return first.uppercaseChar() + value.drop(1)
    }
}
