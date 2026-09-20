package com.nshiftui

class NShiftUIAssembly : NShiftDependencyAssembly() {
    override fun assemblePlugins(container: NShiftDependencyContainer) {
        container.register(NShiftPluginStore::class) {
            NShiftDependencyPluginStore(container)
        }
    }

    override fun assembleEvents(container: NShiftDependencyContainer) {
        val eventStore = NShiftDependencyEventStore()
        container.register(NShiftEventStore::class) { eventStore }
        container.register(NShiftEventHandler::class) {
            NShiftDependencyEventHandler(eventStore, container)
        }
    }
}

class NShiftConfig private constructor() {
    var hostContainer: NShiftDependencyContainer? = null
        private set
    var eventHandler: NShiftEventHandler? = null
        private set
    var eventStore: NShiftEventStore? = null
        private set
    var isStarted: Boolean = false
        private set

    fun startup(
        hostContainer: NShiftDependencyContainer,
        assemblies: List<NShiftDependencyAssembly> = emptyList(),
    ) {
        NShiftRuntimePrecondition.check(
            !isStarted,
            "NShiftUI has already been started.",
        )
        this.hostContainer = hostContainer
        val all = listOf(NShiftUIAssembly()) + assemblies
        all.forEach { assembly ->
            assembly.assemblePlugins(hostContainer)
            assembly.assembleEvents(hostContainer)
        }
        eventStore = hostContainer.resolve(NShiftEventStore::class)
        eventHandler = hostContainer.resolve(NShiftEventHandler::class)
        isStarted = true
    }

    fun resetForTesting() {
        hostContainer = null
        eventHandler = null
        eventStore = null
        isStarted = false
        NShiftPluginContainerRegistry.shared.resetForTesting()
        NShiftPluginContainerCatalog.shared.resetForTesting()
        NShiftRegisteredVersionCatalog.shared.resetForTesting()
    }

    companion object {
        val shared = NShiftConfig()
    }
}
