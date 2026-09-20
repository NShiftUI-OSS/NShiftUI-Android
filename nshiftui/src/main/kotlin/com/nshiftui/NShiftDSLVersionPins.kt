package com.nshiftui

data class NShiftDSLVersionPins(
    private val pins: Map<String, NShiftVersion> = emptyMap(),
) {
    constructor(uses: List<Pair<String, String>>) : this(
        uses.associate { (name, version) -> name to NShiftVersion.of(version) },
    )

    fun pinnedVersion(forPlugin: NShiftPluginName): NShiftVersion? = pins[forPlugin.rawValue]

    fun pinnedVersion(forEvent: NShiftEventName): NShiftVersion? = pins[forEvent.rawValue]

    fun resolvePlugin(
        named: NShiftPluginName,
        catalog: NShiftRegisteredVersionCatalog = NShiftRegisteredVersionCatalog.shared,
    ): NShiftVersion =
        NShiftVersionResolver.resolvePlugin(
            named = named,
            pinned = pinnedVersion(named),
            catalog = catalog,
        )

    fun resolveEvent(
        named: NShiftEventName,
        catalog: NShiftRegisteredVersionCatalog = NShiftRegisteredVersionCatalog.shared,
    ): NShiftVersion =
        NShiftVersionResolver.resolveEvent(
            named = named,
            pinned = pinnedVersion(named),
            catalog = catalog,
        )
}
