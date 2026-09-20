package com.nshiftui

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest

private class NoopEventHandler : NShiftEventHandler {
    override suspend fun handle(trigger: NShiftTrigger, events: List<NShiftEventModel>) {}

    override suspend fun handleThrowing(trigger: NShiftTrigger, events: List<NShiftEventModel>) {}
}

private fun plugin(
    name: String,
    id: String? = null,
    children: List<NShiftPluginModel> = emptyList(),
    slots: Map<NShiftSlotName, List<NShiftPluginModel>> = emptyMap(),
    events: List<NShiftEventModel> = emptyList(),
    metadata: AnyNShiftMetadata? = null,
): NShiftPluginModel =
    NShiftPluginModel(
        id = id,
        name = NShiftPluginName(name),
        version = NShiftVersion.of("1.0.0"),
        metadata = metadata,
        children = children,
        slots = slots,
        events = events,
    )

private fun event(id: String, name: String, trigger: String, events: List<NShiftEventModel> = emptyList()) =
    NShiftEventModel(
        id = id,
        name = NShiftEventName(name),
        version = NShiftVersion.of("1.0.0"),
        trigger = NShiftTrigger(trigger),
        events = events,
    )

class NShiftEngineTests : FunSpec({
    test("indexes every plugin and event in the tree") {
        val root = plugin(
            name = "Root",
            id = "root",
            children = listOf(
                plugin(
                    name = "Card",
                    id = "card",
                    slots = mapOf(
                        NShiftSlotName("Leading") to listOf(
                            plugin(
                                name = "Icon",
                                id = "card-icon",
                                events = listOf(event("icon-tap", "Tap", "onTap")),
                            ),
                        ),
                    ),
                    events = listOf(event("card-tap", "Tap", "onTap")),
                ),
            ),
            events = listOf(event("root-appear", "Appear", "onAppear")),
        )
        val engine = NShiftDefaultEngine(root, NoopEventHandler())
        engine.node("root").shouldNotBeNull()
        engine.node("card").shouldNotBeNull()
        engine.node("card-icon").shouldNotBeNull()
        engine.eventLocation("root-appear").shouldNotBeNull()
        engine.eventLocation("card-tap").shouldNotBeNull()
        engine.eventLocation("icon-tap").shouldNotBeNull()
        engine.node("missing").shouldBeNull()
    }

    test("replace children preserves sibling render ids") {
        val root = plugin(
            name = "Root",
            id = "root",
            children = listOf(
                plugin("A", id = "a"),
                plugin("B", id = "b"),
            ),
        )
        val engine = NShiftDefaultEngine(root, NoopEventHandler())
        engine.replaceChildren("root", listOf(plugin("C", id = "c"), plugin("D", id = "d")))
        engine.root.children.map { it.model.id } shouldBe listOf("c", "d")
        engine.root.children[0].renderId.rawValue shouldBe "r/c/0"
        engine.root.children[1].renderId.rawValue shouldBe "r/c/1"
        engine.node("a").shouldBeNull()
        engine.node("c").shouldNotBeNull()
    }

    test("replace metadata") {
        data class Meta(val value: String) : NShiftMetadata
        val root = plugin(name = "Root", id = "root", metadata = AnyNShiftMetadata(Meta("old")))
        val engine = NShiftDefaultEngine(root, NoopEventHandler())
        engine.replaceMetadata("root", AnyNShiftMetadata(Meta("new")))
        engine.root.model.metadata?.unwrap<Meta>()?.value shouldBe "new"
    }

    test("handle trigger uses event handler") {
        var seen = emptyList<String>()
        val handler = object : NShiftEventHandler {
            override suspend fun handle(trigger: NShiftTrigger, events: List<NShiftEventModel>) {
                seen = events.filter { it.trigger == trigger }.map { it.id }
            }

            override suspend fun handleThrowing(trigger: NShiftTrigger, events: List<NShiftEventModel>) {}
        }
        val root = plugin(
            name = "Root",
            id = "root",
            events = listOf(event("t1", "Tap", "onTap"), event("a1", "Appear", "onAppear")),
        )
        val engine = NShiftDefaultEngine(root, handler)
        runTest {
            engine.handle(NShiftTrigger("onTap"), "root")
        }
        seen shouldBe listOf("t1")
    }

    test("replace nested event by id") {
        val nested = event("nested", "Nested", "onTap")
        val parent = event("parent", "Parent", "onTap", events = listOf(nested))
        val root = plugin(name = "Root", id = "root", events = listOf(parent))
        val engine = NShiftDefaultEngine(root, NoopEventHandler())
        engine.replaceEvent("nested", event("nested", "Replaced", "onTap"))
        engine.root.model.events.single().events.single().name.rawValue shouldBe "Replaced"
    }
})
