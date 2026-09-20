package com.nshiftui.macro

import com.nshiftui.NShiftEmptyMetadata
import com.nshiftui.NShiftMetadata
import kotlin.reflect.KClass

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class NShiftPlugin(
    val name: String = "",
    val version: String = "",
    val metadata: KClass<out NShiftMetadata> = NShiftEmptyMetadata::class,
    val container: Boolean = false,
)

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class NShiftEvent(
    val name: String = "",
    val version: String = "",
    val metadata: KClass<out NShiftMetadata> = NShiftEmptyMetadata::class,
)
