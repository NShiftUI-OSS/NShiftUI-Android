package com.nshiftui

interface NShiftMetadata

class NShiftEmptyMetadata : NShiftMetadata {
    override fun equals(other: Any?): Boolean = other is NShiftEmptyMetadata

    override fun hashCode(): Int = javaClass.hashCode()
}

interface NShiftTokenMetadata {
    val rawValue: String
}

class AnyNShiftMetadata(val base: NShiftMetadata) {
    inline fun <reified T : NShiftMetadata> unwrap(): T? = base as? T

    fun <T : NShiftMetadata> unwrap(type: Class<T>): T? =
        type.takeIf { it.isInstance(base) }?.cast(base)

    override fun equals(other: Any?): Boolean =
        other is AnyNShiftMetadata && base == other.base

    override fun hashCode(): Int = base.hashCode()
}
