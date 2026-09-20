package com.nshiftui

import androidx.compose.runtime.Composable
import kotlin.reflect.KClass

class NShiftRegisteredPluginView(
    val content: @Composable (NShiftPluginModel, NShiftRenderID) -> Unit,
)

class NShiftEventAction(
    private val executeHandler: suspend (NShiftEventModel) -> Unit,
) {
    suspend fun execute(event: NShiftEventModel) {
        executeHandler(event)
    }
}

class NShiftDependencyPluginStore(
    private val resolver: NShiftDependencyResolver,
) : NShiftPluginStore {
    override fun resolve(
        plugin: NShiftPluginModel,
        renderID: NShiftRenderID,
    ): (@Composable () -> Unit)? {
        val registered = resolver.resolve(
            NShiftRegisteredPluginView::class,
            plugin.registrationName,
            listOf(NShiftPluginModel::class, NShiftRenderID::class),
            listOf(plugin, renderID),
        ) ?: return null
        return {
            registered.content(plugin, renderID)
        }
    }
}

class NShiftDependencyEventStore : NShiftEventStore {
    override fun resolve(
        trigger: NShiftTrigger,
        events: List<NShiftEventModel>,
    ): List<NShiftEventModel> = events.filter { it.trigger == trigger }
}

class NShiftDependencyEventHandler(
    private val eventStore: NShiftEventStore,
    private val resolver: NShiftDependencyResolver,
) : NShiftEventHandler {
    override suspend fun handle(trigger: NShiftTrigger, events: List<NShiftEventModel>) {
        for (event in eventStore.resolve(trigger, events)) {
            runCatching { resolveAction(event)?.execute(event) }
        }
    }

    override suspend fun handleThrowing(trigger: NShiftTrigger, events: List<NShiftEventModel>) {
        for (event in eventStore.resolve(trigger, events)) {
            val action = resolveAction(event)
                ?: throw NShiftEventHandlingError(event, NShiftEventHandlingFailureReason.ActionNotFound)
            try {
                action.execute(event)
            } catch (_: NShiftEventInitializationError) {
                throw NShiftEventHandlingError(event, NShiftEventHandlingFailureReason.InitializationFailed)
            } catch (error: NShiftEventHandlingError) {
                throw error
            } catch (error: Throwable) {
                throw NShiftEventHandlingError(
                    event,
                    NShiftEventHandlingFailureReason.ActionFailed(error.message ?: error.toString()),
                )
            }
        }
    }

    private fun resolveAction(event: NShiftEventModel): NShiftEventAction? =
        resolver.resolve(NShiftEventAction::class, event.registrationName)
}

fun NShiftDependencyRegistering.registerPlugin(
    name: NShiftPluginName,
    version: NShiftVersion,
    isContainer: Boolean,
    factory: (
        NShiftPluginModel,
        NShiftDependencyResolver,
        NShiftEventHandler,
        NShiftEngine?,
        NShiftRenderID,
    ) -> NShiftPlugin?,
) {
    val registrationName = NShiftVersion.registrationName(name, version)
    if (isContainer) {
        NShiftPluginContainerCatalog.shared.register(name)
    }
    NShiftRegisteredVersionCatalog.shared.registerPlugin(name, version)
    val container = this as NShiftDependencyContainer
    register(
        NShiftRegisteredPluginView::class,
        registrationName,
        NShiftDependencyScope.Transient,
        listOf(NShiftPluginModel::class, NShiftRenderID::class),
    ) { arguments ->
        if (arguments.size != 2) return@register null
        val model = arguments[0] as? NShiftPluginModel ?: return@register null
        val renderID = arguments[1] as? NShiftRenderID ?: return@register null
        val eventHandler = container.resolveOrThrow(NShiftEventHandler::class)
        val engine = container.resolve(NShiftEngine::class)
        val plugin = factory(model, container, eventHandler, engine, renderID) ?: return@register null
        NShiftRegisteredPluginView { _, _ -> plugin.Content() }
    }
}

inline fun <reified P : NShiftPlugin> NShiftDependencyRegistering.registerPlugin() {
    val constructor = P::class.java.getDeclaredConstructor()
    constructor.isAccessible = true
    val sample = constructor.newInstance()
    registerPlugin(
        name = sample.name,
        version = sample.version,
        isContainer = sample is NShiftPluginContainer<*>,
    ) { model, resolver, eventHandler, engine, renderID ->
        val plugin = constructor.newInstance()
        if (plugin.bind(model, resolver, eventHandler, engine, renderID)) plugin else null
    }
}

fun NShiftDependencyRegistering.registerEvent(
    name: NShiftEventName,
    version: NShiftVersion,
    factory: (NShiftEventModel, NShiftDependencyResolver) -> NShiftEvent?,
) {
    val registrationName = NShiftVersion.registrationName(name, version)
    NShiftRegisteredVersionCatalog.shared.registerEvent(name, version)
    val container = this as NShiftDependencyContainer
    register(NShiftEventAction::class, registrationName) {
        NShiftEventAction { model ->
            val event = factory(model, container) ?: throw NShiftEventInitializationError()
            event.execute()
        }
    }
}

inline fun <reified E : NShiftEvent> NShiftDependencyRegistering.registerEvent() {
    val constructor = E::class.java.getDeclaredConstructor()
    constructor.isAccessible = true
    val sample = constructor.newInstance()
    registerEvent(sample.name, sample.version) { model, resolver ->
        val event = constructor.newInstance()
        if (event.bind(model, resolver)) event else null
    }
}

fun NShiftDependencyRegistering.registerEngine(
    factory: (NShiftPluginModel) -> NShiftEngine,
) {
    register(
        NShiftEngine::class,
        name = null,
        scope = NShiftDependencyScope.Transient,
        argumentTypes = listOf(NShiftPluginModel::class),
    ) { arguments ->
        val rootModel = arguments.singleOrNull() as? NShiftPluginModel ?: return@register null
        factory(rootModel)
    }
}

abstract class NShiftPlugin(
    val name: NShiftPluginName,
    val version: NShiftVersion,
    private val metadataType: KClass<out NShiftMetadata>? = null,
) {
    constructor(
        name: String,
        version: String,
        metadata: KClass<out NShiftMetadata>? = null,
    ) : this(NShiftPluginName(name), NShiftVersion.of(version), metadata)

    lateinit var model: NShiftPluginModel
        protected set
    lateinit var resolver: NShiftDependencyResolver
        protected set
    lateinit var eventHandler: NShiftEventHandler
        protected set
    var engine: NShiftEngine? = null
        protected set
    lateinit var renderID: NShiftRenderID
        protected set
    var metadataValue: NShiftMetadata? = null
        protected set

    inline fun <reified T : NShiftMetadata> metadata(): T = metadataValue as T

    @Composable
    abstract fun Content()

    @Composable
    fun children(transform: @Composable (NShiftPluginModel, NShiftPluginNode) -> Unit = { _, node ->
        NShiftNodeView(node = node, engine = requireEngineTree())
    }) {
        val tree = requireEngineTree()
        val parent = tree.node(renderID) ?: return
        parent.children.forEach { child ->
            androidx.compose.runtime.key(child.renderId.rawValue) {
                transform(child.model, child)
            }
        }
    }

    @Composable
    fun slots(
        name: NShiftSlotName,
        transform: @Composable (NShiftPluginModel, NShiftPluginNode) -> Unit = { _, node ->
            NShiftNodeView(node = node, engine = requireEngineTree())
        },
    ) {
        val tree = requireEngineTree()
        val parent = tree.node(renderID) ?: return
        val nodes = parent.slotChildren[name].orEmpty()
        nodes.forEach { child ->
            androidx.compose.runtime.key(child.renderId.rawValue) {
                transform(child.model, child)
            }
        }
    }

    @Composable
    fun <Key : NShiftSlotKey> slots(
        key: Key,
        transform: @Composable (NShiftPluginModel, NShiftPluginNode) -> Unit = { _, node ->
            NShiftNodeView(node = node, engine = requireEngineTree())
        },
    ) {
        slots(key.slotName, transform)
    }

    fun bind(
        model: NShiftPluginModel,
        resolver: NShiftDependencyResolver,
        eventHandler: NShiftEventHandler,
        engine: NShiftEngine?,
        renderID: NShiftRenderID,
    ): Boolean {
        if (metadataType != null) {
            val unwrapped = model.metadata?.unwrap(metadataType.java) ?: return false
            metadataValue = unwrapped
        } else {
            metadataValue = model.metadata?.base
        }
        this.model = model
        this.resolver = resolver
        this.eventHandler = eventHandler
        this.engine = engine
        this.renderID = renderID
        return onBind()
    }

    protected open fun onBind(): Boolean = true

    protected fun requireEngineTree(): NShiftEngineTree =
        engine as? NShiftEngineTree
            ?: error("Plugin children/slots require an NShiftEngineTree")
}

abstract class NShiftPluginWithMetadata<M : NShiftMetadata>(
    name: String,
    version: String,
    metadataType: KClass<M>,
) : NShiftPlugin(name, version, metadataType) {
    val metadata: M
        get() = metadataValue as M
}

abstract class NShiftPluginContainer<VM : NShiftPluginViewModel>(
    name: String,
    version: String,
    metadata: KClass<out NShiftMetadata>? = null,
    private val viewModelType: KClass<VM>,
) : NShiftPlugin(name, version, metadata) {
    lateinit var viewModel: VM
        protected set

    override fun onBind(): Boolean {
        val resolved = resolver.resolve(viewModelType) ?: return false
        viewModel = resolved
        return true
    }
}

abstract class NShiftEvent(
    val name: NShiftEventName,
    val version: NShiftVersion,
    private val metadataType: KClass<out NShiftMetadata>? = null,
) {
    constructor(
        name: String,
        version: String,
        metadata: KClass<out NShiftMetadata>? = null,
    ) : this(NShiftEventName(name), NShiftVersion.of(version), metadata)

    lateinit var model: NShiftEventModel
        protected set
    lateinit var resolver: NShiftDependencyResolver
        protected set
    var metadataValue: NShiftMetadata? = null
        protected set

    inline fun <reified T : NShiftMetadata> metadata(): T = metadataValue as T

    abstract suspend fun execute()

    fun bind(model: NShiftEventModel, resolver: NShiftDependencyResolver): Boolean {
        if (metadataType != null) {
            val unwrapped = model.metadata?.unwrap(metadataType.java) ?: return false
            metadataValue = unwrapped
        } else {
            metadataValue = model.metadata?.base
        }
        this.model = model
        this.resolver = resolver
        return true
    }
}

abstract class NShiftEventWithMetadata<M : NShiftMetadata>(
    name: String,
    version: String,
    metadataType: KClass<M>,
) : NShiftEvent(name, version, metadataType) {
    val metadata: M
        get() = metadataValue as M
}
