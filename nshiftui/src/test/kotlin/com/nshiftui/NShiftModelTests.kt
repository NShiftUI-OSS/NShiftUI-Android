package com.nshiftui

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class NShiftPluginStyleTests : FunSpec({
    test("defaults") {
        val style = NShiftPluginStyle()
        style.frame shouldBe NShiftPluginFrame()
        style.alignment shouldBe NShiftPluginAlignment.Center
        style.spacing shouldBe 0.0
    }

    test("stores spacing") {
        NShiftPluginStyle(spacing = 16.0).spacing shouldBe 16.0
    }

    test("fixed frame") {
        val frame = NShiftPluginFrame.of(width = 120.0, height = 48.0)
        frame.width shouldBe 120.0
        frame.height shouldBe 48.0
        frame.hasFixedWidth shouldBe true
        frame.hasFlexibleWidth shouldBe false
    }

    test("flexible fill") {
        val frame = NShiftPluginFrame.of(
            minWidth = NShiftPluginDimension.Value(44.0),
            maxWidth = NShiftPluginDimension.Fill,
            maxHeight = NShiftPluginDimension.Fill,
        )
        frame.minWidth shouldBe 44.0
        frame.maxWidth shouldBe Double.POSITIVE_INFINITY
        frame.maxHeight shouldBe Double.POSITIVE_INFINITY
    }

    test("rejects conflicts") {
        shouldThrow<NShiftPluginFrameError.FixedWidthConflictsWithFlexibleConstraints> {
            NShiftPluginFrame.of(width = 100.0, maxWidth = NShiftPluginDimension.Fill)
        }
        shouldThrow<NShiftPluginFrameError.FixedHeightConflictsWithFlexibleConstraints> {
            NShiftPluginFrame.of(height = 50.0, minHeight = NShiftPluginDimension.Value(20.0))
        }
        shouldThrow<NShiftPluginFrameError.FillNotAllowedForMinWidth> {
            NShiftPluginFrame.of(minWidth = NShiftPluginDimension.Fill)
        }
        shouldThrow<NShiftPluginFrameError.FillNotAllowedForMinHeight> {
            NShiftPluginFrame.of(minHeight = NShiftPluginDimension.Fill)
        }
        shouldThrow<NShiftPluginFrameError.ContradictoryWidthConstraints> {
            NShiftPluginFrame.of(
                minWidth = NShiftPluginDimension.Value(80.0),
                maxWidth = NShiftPluginDimension.Value(40.0),
            )
        }
    }
})

class NShiftPluginModelTests : FunSpec({
    test("replacing children slots events metadata") {
        val root = NShiftPluginModel(
            name = NShiftPluginName("Screen"),
            version = NShiftVersion.of("1.0.0"),
        )
        val child = NShiftPluginModel(
            name = NShiftPluginName("Button"),
            version = NShiftVersion.of("1.0.0"),
        )
        val withChild = root.replacingChildren(listOf(child))
        withChild.children.single().name.rawValue shouldBe "Button"
        val slot = NShiftSlotName("Leading")
        val withSlot = withChild.replacingSlot(slot, listOf(child))
        withSlot.slots[slot]?.single()?.name?.rawValue shouldBe "Button"
        val event = NShiftEventModel(
            name = NShiftEventName("ShowToast"),
            version = NShiftVersion.of("1.0.0"),
            trigger = NShiftTrigger("onTap"),
        )
        val withEvents = withSlot.replacingEvents(listOf(event))
        withEvents.events.single().name.rawValue shouldBe "ShowToast"
        val meta = AnyNShiftMetadata(NShiftEmptyMetadata())
        withEvents.replacingMetadata(meta).metadata shouldBe meta
    }

    test("plugin node indexes children and slots") {
        val model = NShiftPluginModel(
            name = NShiftPluginName("Screen"),
            version = NShiftVersion.of("1.0.0"),
            children = listOf(
                NShiftPluginModel(name = NShiftPluginName("Text"), version = NShiftVersion.of("1.0.0")),
            ),
            slots = mapOf(
                NShiftSlotName("NavLeading") to listOf(
                    NShiftPluginModel(name = NShiftPluginName("Icon"), version = NShiftVersion.of("1.0.0")),
                ),
            ),
        )
        val node = NShiftPluginNode(model)
        node.renderId.rawValue shouldBe "r"
        node.children.single().renderId.rawValue shouldBe "r/c/0"
        node.slotChildren[NShiftSlotName("NavLeading")]?.single()?.renderId?.rawValue shouldBe "r/s/NavLeading/0"
    }
})
