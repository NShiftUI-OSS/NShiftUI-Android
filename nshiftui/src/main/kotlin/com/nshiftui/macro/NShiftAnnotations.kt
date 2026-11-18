package com.nshiftui.macro

import com.nshiftui.NShiftEmptyMetadata
import com.nshiftui.NShiftMetadata
import kotlin.reflect.KClass

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class NShiftPlugin(
    val name: String = "",
    val version: String = "",
    val metadata: KClass<out NShiftMetadata> = NShiftEmptyMetadata::class,
    val container: Boolean = false,
    val slots: KClass<*> = Unit::class,
)

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class NShiftEvent(
    val name: String = "",
    val version: String = "",
    val metadata: KClass<out NShiftMetadata> = NShiftEmptyMetadata::class,
    val slots: KClass<*> = Unit::class,
)
