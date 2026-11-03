package com.nshiftui

import androidx.compose.runtime.Composable
import kotlin.reflect.KClass

interface NShiftPluginViewModel

interface NShiftDependencyResolver {
    fun <Service : Any> resolve(
        serviceType: KClass<Service>,
        name: String? = null,
        argumentTypes: List<KClass<*>> = emptyList(),
        arguments: List<Any> = emptyList(),
    ): Service?
}

inline fun <reified Service : Any> NShiftDependencyResolver.resolve(
    name: String? = null,
): Service? = resolve(Service::class, name)

inline fun <reified Service : Any> NShiftDependencyResolver.resolve(
    name: String? = null,
    arg1: Any,
    arg1Type: KClass<*> = arg1::class,
): Service? = resolve(Service::class, name, listOf(arg1Type), listOf(arg1))

inline fun <reified Service : Any> NShiftDependencyResolver.resolve(
    name: String? = null,
    arg1: Any,
    arg2: Any,
    arg1Type: KClass<*> = arg1::class,
    arg2Type: KClass<*> = arg2::class,
): Service? =
    resolve(Service::class, name, listOf(arg1Type, arg2Type), listOf(arg1, arg2))

fun <Service : Any> NShiftDependencyResolver.resolveOrThrow(
    serviceType: KClass<Service>,
    name: String? = null,
    argumentTypes: List<KClass<*>> = emptyList(),
    arguments: List<Any> = emptyList(),
): Service =
    resolve(serviceType, name, argumentTypes, arguments)
        ?: error("Unwrapped resolve failed for ${serviceType.qualifiedName}")

inline fun <reified Service : Any> NShiftDependencyResolver.resolveOrThrow(
    name: String? = null,
): Service = resolveOrThrow(Service::class, name)

interface NShiftPluginStore {
    fun resolve(
        plugin: NShiftPluginModel,
        renderID: NShiftRenderID = NShiftRenderID.root,
        viewModel: NShiftPluginViewModel? = null,
    ): (@Composable () -> Unit)?
}

class NShiftEmptyPluginStore : NShiftPluginStore {
    override fun resolve(
        plugin: NShiftPluginModel,
        renderID: NShiftRenderID,
        viewModel: NShiftPluginViewModel?,
    ): (@Composable () -> Unit)? = null
}

interface NShiftEventStore {
    fun resolve(trigger: NShiftTrigger, events: List<NShiftEventModel>): List<NShiftEventModel>
}

interface NShiftEventHandler {
    suspend fun handle(trigger: NShiftTrigger, events: List<NShiftEventModel>)

    suspend fun handleThrowing(trigger: NShiftTrigger, events: List<NShiftEventModel>)
}

data class NShiftEventHandlingError(
    val eventID: String,
    val eventName: String,
    val trigger: String,
    val reason: NShiftEventHandlingFailureReason,
) : Exception() {
    constructor(event: NShiftEventModel, reason: NShiftEventHandlingFailureReason) : this(
        eventID = event.id,
        eventName = event.name.rawValue,
        trigger = event.trigger.rawValue,
        reason = reason,
    )

    override val message: String
        get() =
            "Failed to handle event '$eventName' (id: $eventID, trigger: $trigger): ${reason.description}"
}

sealed class NShiftEventHandlingFailureReason {
    data object ActionNotFound : NShiftEventHandlingFailureReason()
    data class ActionFailed(val message: String) : NShiftEventHandlingFailureReason()
    data object InitializationFailed : NShiftEventHandlingFailureReason()

    val description: String
        get() =
            when (this) {
                ActionNotFound -> "event action was not registered"
                is ActionFailed -> "event action failed with error: $message"
                InitializationFailed -> "event init? returned nil"
            }
}

class NShiftEventInitializationError : Exception()

class NShiftPluginContainerCatalog private constructor() {
    private val names = mutableSetOf<NShiftPluginName>()

    fun register(name: NShiftPluginName) {
        names.add(name)
    }

    fun contains(name: NShiftPluginName): Boolean = names.contains(name)

    fun resetForTesting() {
        names.clear()
    }

    companion object {
        val shared = NShiftPluginContainerCatalog()
    }
}

class NShiftPluginContainerRegistry private constructor() {
    private val engines = mutableMapOf<String, NShiftEngine>()

    fun register(rootID: String, engine: NShiftEngine) {
        engines[rootID] = engine
    }

    fun unregister(rootID: String) {
        engines.remove(rootID)
    }

    fun engine(rootID: String): NShiftEngine? = engines[rootID]

    fun resetForTesting() {
        engines.clear()
    }

    companion object {
        val shared = NShiftPluginContainerRegistry()
    }
}
