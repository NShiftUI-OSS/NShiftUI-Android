package com.nshiftui

import com.nshiftui.internal.NShiftASCII

data class NShiftVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
) : Comparable<NShiftVersion> {
    val rawValue: String
        get() = "$major.$minor.$patch"

    override fun toString(): String = rawValue

    override fun compareTo(other: NShiftVersion): Int {
        if (major != other.major) return major.compareTo(other.major)
        if (minor != other.minor) return minor.compareTo(other.minor)
        return patch.compareTo(other.patch)
    }

    companion object {
        fun parse(rawValue: String): NShiftVersion? {
            val parts = rawValue.split(".", limit = 4)
            if (parts.size != 3) {
                return null
            }
            val major = parseNumericIdentifier(parts[0]) ?: return null
            val minor = parseNumericIdentifier(parts[1]) ?: return null
            val patch = parseNumericIdentifier(parts[2]) ?: return null
            return NShiftVersion(major, minor, patch)
        }

        fun isValid(value: String): Boolean = parse(value) != null

        fun of(rawValue: String): NShiftVersion =
            parse(rawValue) ?: throw NShiftVersionError.InvalidVersion(rawValue)

        fun registrationName(plugin: NShiftPluginName, version: NShiftVersion): String =
            "${plugin.rawValue}@${version.rawValue}"

        fun registrationName(event: NShiftEventName, version: NShiftVersion): String =
            "${event.rawValue}@${version.rawValue}"

        private fun parseNumericIdentifier(value: String): Int? {
            if (value.isEmpty() || !value.all(NShiftASCII::isASCIIDigit)) {
                return null
            }
            if (value.length > 1 && value.first() == '0') {
                return null
            }
            return value.toIntOrNull()
        }
    }
}

sealed class NShiftVersionError : Exception() {
    data class InvalidVersion(val version: String) : NShiftVersionError() {
        override val message: String = "Invalid version: $version"
    }
}
