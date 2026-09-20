package com.nshiftui

internal data class NShiftEventLocation(
    val ownerNodeID: String,
    val path: List<Int>,
)

open class NShiftDefaultEngine(
    rootModel: NShiftPluginModel,
    private val eventHandler: NShiftEventHandler,
    private val pluginStore: NShiftPluginStore = NShiftEmptyPluginStore(),
) : NShiftEngineTree {
    override val root: NShiftPluginNode = NShiftPluginNode(model = rootModel)

    private val nodesByServerID = mutableMapOf<String, NShiftPluginNode>()
    private val nodesByRenderID = mutableMapOf<NShiftRenderID, NShiftPluginNode>()
    private val eventsByID = mutableMapOf<String, NShiftEventLocation>()

    override val rootID: String
        get() = root.model.id ?: root.renderId.rawValue

    init {
        indexSubtree(root)
    }

    override fun makeView(node: NShiftPluginNode): (@androidx.compose.runtime.Composable () -> Unit)? =
        pluginStore.resolve(node.model, node.renderId)

    override fun node(id: String): NShiftPluginNode? =
        nodesByServerID[id] ?: nodesByRenderID[NShiftRenderID(id)]

    override fun node(renderID: NShiftRenderID): NShiftPluginNode? = nodesByRenderID[renderID]

    internal fun eventLocation(id: String): NShiftEventLocation? = eventsByID[id]

    override suspend fun handle(trigger: NShiftTrigger, pluginID: String) {
        val node = node(pluginID) ?: return
        eventHandler.handle(trigger, node.model.events)
    }

    override fun replaceMetadata(pluginID: String, metadata: AnyNShiftMetadata?) {
        val node = node(pluginID) ?: return
        node.model = node.model.replacingMetadata(metadata)
    }

    override fun replacePlugin(pluginID: String, model: NShiftPluginModel) {
        val node = node(pluginID) ?: return
        deindexSubtree(node)
        node.model = model
        node.replaceChildren(model.children)
        node.replaceAllSlotChildren(model.slots)
        indexSubtree(node)
    }

    override fun replaceChildren(pluginID: String, children: List<NShiftPluginModel>) {
        val node = node(pluginID) ?: return
        node.children.forEach(::deindexSubtree)
        node.model = node.model.replacingChildren(children)
        node.replaceChildren(children)
        node.children.forEach(::indexSubtree)
    }

    override fun replaceSlots(pluginID: String, slots: Map<NShiftSlotName, List<NShiftPluginModel>>) {
        val node = node(pluginID) ?: return
        node.slotChildren.values.flatten().forEach(::deindexSubtree)
        node.model = node.model.replacingSlots(slots)
        node.replaceAllSlotChildren(slots)
        node.slotChildren.values.flatten().forEach(::indexSubtree)
    }

    override fun replaceSlot(pluginID: String, name: NShiftSlotName, models: List<NShiftPluginModel>) {
        val node = node(pluginID) ?: return
        node.slotChildren[name]?.forEach(::deindexSubtree)
        node.model = node.model.replacingSlot(name, models)
        node.replaceSlotChildren(name, models)
        node.slotChildren[name]?.forEach(::indexSubtree)
    }

    override fun replaceEvents(pluginID: String, events: List<NShiftEventModel>) {
        val node = node(pluginID) ?: return
        removeEvents(node.renderId)
        node.model = node.model.replacingEvents(events)
        indexEvents(node)
    }

    override fun replaceEvent(eventID: String, event: NShiftEventModel) {
        val location = eventsByID[eventID] ?: return
        val node = nodesByRenderID[NShiftRenderID(location.ownerNodeID)] ?: return
        val forest = node.model.events.toMutableList()
        if (!replaceEvent(forest, location.path, event)) {
            return
        }
        removeEvents(node.renderId)
        node.model = node.model.replacingEvents(forest)
        indexEvents(node)
    }

    private fun indexSubtree(node: NShiftPluginNode) {
        nodesByRenderID[node.renderId] = node
        node.model.id?.let { nodesByServerID[it] = node }
        indexEvents(node)
        node.children.forEach(::indexSubtree)
        node.slotChildren.values.flatten().forEach(::indexSubtree)
    }

    private fun deindexSubtree(node: NShiftPluginNode) {
        nodesByRenderID.remove(node.renderId)
        node.model.id?.let { nodesByServerID.remove(it) }
        removeEvents(node.renderId)
        node.children.forEach(::deindexSubtree)
        node.slotChildren.values.flatten().forEach(::deindexSubtree)
    }

    private fun indexEvents(node: NShiftPluginNode) {
        fun walk(models: List<NShiftEventModel>, path: List<Int>) {
            models.forEachIndexed { offset, model ->
                val childPath = path + offset
                eventsByID[model.id] = NShiftEventLocation(node.renderId.rawValue, childPath)
                walk(model.events, childPath)
            }
        }
        walk(node.model.events, emptyList())
    }

    private fun removeEvents(ownerRenderID: NShiftRenderID) {
        val owner = ownerRenderID.rawValue
        eventsByID.entries.removeAll { it.value.ownerNodeID == owner }
    }

    private fun replaceEvent(
        forest: MutableList<NShiftEventModel>,
        path: List<Int>,
        event: NShiftEventModel,
    ): Boolean {
        val first = path.firstOrNull() ?: return false
        if (first !in forest.indices) return false
        if (path.size == 1) {
            forest[first] = event
            return true
        }
        val children = forest[first].events.toMutableList()
        if (!replaceEvent(children, path.drop(1), event)) {
            return false
        }
        val current = forest[first]
        forest[first] = current.copy(events = children)
        return true
    }
}

internal object NShiftEngineResolver {
    fun makeEngine(
        rootModel: NShiftPluginModel,
        config: NShiftConfig = NShiftConfig.shared,
    ): NShiftEngine {
        val hostContainer = config.hostContainer
        val eventHandler = config.eventHandler
        if (hostContainer == null || eventHandler == null) {
            NShiftRuntimePrecondition.raise("NShiftUI must be started before creating an engine.")
        }
        val engine = resolveEngine(rootModel, hostContainer, eventHandler)
        hostContainer.register(
            NShiftEngine::class,
            name = null,
            scope = NShiftDependencyScope.Singleton,
        ) { args ->
            if (args.isNotEmpty()) null else engine
        }
        return engine
    }

    fun resolveEngine(
        rootModel: NShiftPluginModel,
        hostContainer: NShiftDependencyContainer,
        eventHandler: NShiftEventHandler,
    ): NShiftEngine {
        val expectedRootID = rootModel.id ?: NShiftRenderID.root.rawValue
        val custom = hostContainer.resolve(
            NShiftEngine::class,
            name = null,
            argumentTypes = listOf(NShiftPluginModel::class),
            arguments = listOf(rootModel),
        )
        if (custom != null && custom.rootID == expectedRootID) {
            return custom
        }
        val pluginStore = hostContainer.resolve(NShiftPluginStore::class) ?: NShiftEmptyPluginStore()
        return NShiftDefaultEngine(rootModel, eventHandler, pluginStore)
    }
}
