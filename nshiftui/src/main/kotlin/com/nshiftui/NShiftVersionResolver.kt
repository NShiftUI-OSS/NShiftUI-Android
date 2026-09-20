package com.nshiftui

sealed class NShiftVersionResolutionError : Exception() {
    data class PluginNotRegistered(val name: NShiftPluginName) : NShiftVersionResolutionError()
    data class EventNotRegistered(val name: NShiftEventName) : NShiftVersionResolutionError()
    data class PluginVersionNotRegistered(
        val name: NShiftPluginName,
        val version: NShiftVersion,
    ) : NShiftVersionResolutionError()
    data class EventVersionNotRegistered(
        val name: NShiftEventName,
        val version: NShiftVersion,
    ) : NShiftVersionResolutionError()
}

object NShiftVersionResolver {
    fun resolvePlugin(
        named: NShiftPluginName,
        pinned: NShiftVersion? = null,
        catalog: NShiftRegisteredVersionCatalog = NShiftRegisteredVersionCatalog.shared,
    ): NShiftVersion {
        if (pinned != null) {
            if (pinned !in catalog.versions(forPlugin = named)) {
                throw NShiftVersionResolutionError.PluginVersionNotRegistered(named, pinned)
            }
            return pinned
        }
        return catalog.latestPlugin(named)
            ?: throw NShiftVersionResolutionError.PluginNotRegistered(named)
    }

    fun resolveEvent(
        named: NShiftEventName,
        pinned: NShiftVersion? = null,
        catalog: NShiftRegisteredVersionCatalog = NShiftRegisteredVersionCatalog.shared,
    ): NShiftVersion {
        if (pinned != null) {
            if (pinned !in catalog.versions(forEvent = named)) {
                throw NShiftVersionResolutionError.EventVersionNotRegistered(named, pinned)
            }
            return pinned
        }
        return catalog.latestEvent(named)
            ?: throw NShiftVersionResolutionError.EventNotRegistered(named)
    }
}
