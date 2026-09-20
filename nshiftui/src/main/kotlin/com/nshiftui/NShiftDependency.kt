package com.nshiftui

import kotlin.reflect.KClass

enum class NShiftDependencyScope {
    Transient,
    Singleton,
}

data class NShiftDependencyKey(
    val service: KClass<*>,
    val name: String?,
    val argumentTypes: List<KClass<*>>,
)

fun interface NShiftDependencyFactory {
    fun resolve(arguments: List<Any>): Any?
}

interface NShiftDependencyRegistering {
    fun <Service : Any> register(
        serviceType: KClass<Service>,
        name: String? = null,
        scope: NShiftDependencyScope = NShiftDependencyScope.Transient,
        argumentTypes: List<KClass<*>> = emptyList(),
        factory: (List<Any>) -> Service?,
    )
}

interface NShiftDependencyContainer : NShiftDependencyResolver, NShiftDependencyRegistering

open class NShiftDependencyAssembly {
    open fun assemblePlugins(container: NShiftDependencyContainer) {}

    open fun assembleEvents(container: NShiftDependencyContainer) {}

    fun assemble(container: NShiftDependencyContainer) {
        assemblePlugins(container)
        assembleEvents(container)
    }
}

class NShiftDependencyRegistry : NShiftDependencyContainer {
    private data class Registration(
        val factory: NShiftDependencyFactory,
        val scope: NShiftDependencyScope,
    )

    private val lock = Any()
    private val registrations = mutableMapOf<NShiftDependencyKey, Registration>()
    private val singletons = mutableMapOf<NShiftDependencyKey, Any>()

    override fun <Service : Any> register(
        serviceType: KClass<Service>,
        name: String?,
        scope: NShiftDependencyScope,
        argumentTypes: List<KClass<*>>,
        factory: (List<Any>) -> Service?,
    ) {
        val key = NShiftDependencyKey(serviceType, name, argumentTypes)
        synchronized(lock) {
            registrations[key] = Registration(NShiftDependencyFactory { args -> factory(args) }, scope)
            singletons.remove(key)
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun <Service : Any> resolve(
        serviceType: KClass<Service>,
        name: String?,
        argumentTypes: List<KClass<*>>,
        arguments: List<Any>,
    ): Service? {
        val key = NShiftDependencyKey(serviceType, name, argumentTypes)
        val registration = synchronized(lock) { registrations[key] } ?: return null
        return when (registration.scope) {
            NShiftDependencyScope.Transient ->
                registration.factory.resolve(arguments) as Service?
            NShiftDependencyScope.Singleton -> {
                synchronized(lock) { singletons[key] }?.let { return it as Service }
                val made = registration.factory.resolve(arguments) ?: return null
                synchronized(lock) {
                    val existing = singletons[key]
                    if (existing != null) {
                        existing as Service
                    } else {
                        singletons[key] = made
                        made as Service
                    }
                }
            }
        }
    }

    fun apply(assembly: NShiftDependencyAssembly) {
        assembly.assemble(this)
    }

    fun apply(assemblies: List<NShiftDependencyAssembly>) {
        assemblies.forEach(::apply)
    }
}

fun <Service : Any> NShiftDependencyRegistering.register(
    serviceType: KClass<Service>,
    name: String? = null,
    scope: NShiftDependencyScope = NShiftDependencyScope.Transient,
    factory: () -> Service,
) {
    register(serviceType, name, scope, emptyList()) { args ->
        if (args.isNotEmpty()) null else factory()
    }
}

inline fun <reified Service : Any> NShiftDependencyRegistering.register(
    name: String? = null,
    scope: NShiftDependencyScope = NShiftDependencyScope.Transient,
    noinline factory: () -> Service,
) {
    register(Service::class, name, scope, factory)
}

inline fun <reified Service : Any, reified A1 : Any> NShiftDependencyRegistering.register(
    name: String? = null,
    scope: NShiftDependencyScope = NShiftDependencyScope.Transient,
    noinline factory: (A1) -> Service,
) {
    register(Service::class, name, scope, listOf(A1::class)) { args ->
        val a1 = args.singleOrNull() as? A1 ?: return@register null
        factory(a1)
    }
}

inline fun <reified Service : Any, reified A1 : Any, reified A2 : Any> NShiftDependencyRegistering.register(
    name: String? = null,
    scope: NShiftDependencyScope = NShiftDependencyScope.Transient,
    noinline factory: (A1, A2) -> Service,
) {
    register(Service::class, name, scope, listOf(A1::class, A2::class)) { args ->
        if (args.size != 2) return@register null
        val a1 = args[0] as? A1 ?: return@register null
        val a2 = args[1] as? A2 ?: return@register null
        factory(a1, a2)
    }
}
