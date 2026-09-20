package com.nshiftui

class NShiftRegisteredVersionCatalogFailure(message: String) : IllegalStateException(message)

internal object NShiftRegisteredVersionCatalogFailureHandler {
    @Volatile
    private var handler: (String) -> Nothing = { message ->
        throw NShiftRegisteredVersionCatalogFailure(message)
    }

    fun replace(replacement: (String) -> Nothing) {
        handler = replacement
    }

    fun restore() {
        handler = { message ->
            throw NShiftRegisteredVersionCatalogFailure(message)
        }
    }

    fun raise(message: String): Nothing = handler(message)
}

class NShiftRegisteredVersionCatalog internal constructor() {
    private val lock = Any()
    private val pluginVersions = mutableMapOf<String, MutableSet<NShiftVersion>>()
    private val eventVersions = mutableMapOf<String, MutableSet<NShiftVersion>>()

    fun registerPlugin(name: NShiftPluginName, version: NShiftVersion) {
        synchronized(lock) {
            if (eventVersions[name.rawValue] != null) {
                NShiftRegisteredVersionCatalogFailureHandler.raise(
                    "Plugin name \"${name.rawValue}\" conflicts with a registered event of the same name",
                )
            }
            pluginVersions.getOrPut(name.rawValue) { mutableSetOf() }.add(version)
        }
    }

    fun registerEvent(name: NShiftEventName, version: NShiftVersion) {
        synchronized(lock) {
            if (pluginVersions[name.rawValue] != null) {
                NShiftRegisteredVersionCatalogFailureHandler.raise(
                    "Event name \"${name.rawValue}\" conflicts with a registered plugin of the same name",
                )
            }
            eventVersions.getOrPut(name.rawValue) { mutableSetOf() }.add(version)
        }
    }

    fun latestPlugin(named: NShiftPluginName): NShiftVersion? =
        synchronized(lock) { pluginVersions[named.rawValue]?.maxOrNull() }

    fun latestEvent(named: NShiftEventName): NShiftVersion? =
        synchronized(lock) { eventVersions[named.rawValue]?.maxOrNull() }

    fun versions(forPlugin: NShiftPluginName): Set<NShiftVersion> =
        synchronized(lock) { pluginVersions[forPlugin.rawValue]?.toSet().orEmpty() }

    fun versions(forEvent: NShiftEventName): Set<NShiftVersion> =
        synchronized(lock) { eventVersions[forEvent.rawValue]?.toSet().orEmpty() }

    fun registerPlugin(name: String, version: String) {
        registerPlugin(NShiftPluginName(name), NShiftVersion.of(version))
    }

    fun registerEvent(name: String, version: String) {
        registerEvent(NShiftEventName(name), NShiftVersion.of(version))
    }

    fun resetForTesting() {
        synchronized(lock) {
            pluginVersions.clear()
            eventVersions.clear()
        }
    }

    companion object {
        val shared = NShiftRegisteredVersionCatalog()

        fun forTesting(): NShiftRegisteredVersionCatalog = NShiftRegisteredVersionCatalog()
    }
}
