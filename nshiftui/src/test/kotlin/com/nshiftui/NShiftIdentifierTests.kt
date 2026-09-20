package com.nshiftui

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class NShiftIdentifierTests : FunSpec({
    test("plugin name stores valid PascalCase ASCII") {
        val name = NShiftPluginName("PrimaryButton")
        name.rawValue shouldBe "PrimaryButton"
        name.toString() shouldBe "PrimaryButton"
    }

    test("plugin name validation") {
        NShiftPluginName.isValid("PrimaryButton") shouldBe true
        NShiftPluginName.isValid("Button") shouldBe true
        NShiftPluginName.isValid("") shouldBe false
        NShiftPluginName.isValid("primaryButton") shouldBe false
        NShiftPluginName.isValid("Primary_Button") shouldBe false
        NShiftPluginName.isValid("Primary1Button") shouldBe false
        NShiftPluginName.isValid("Botao") shouldBe true
        NShiftPluginName.isValid("Botão") shouldBe false
        shouldThrow<NShiftPluginNameError.InvalidName> { NShiftPluginName("primaryButton") }
    }

    test("event name matches plugin name grammar") {
        NShiftEventName("ShowToast").rawValue shouldBe "ShowToast"
        shouldThrow<NShiftEventNameError.InvalidName> { NShiftEventName("showToast") }
    }

    test("slot name PascalCase") {
        NShiftSlotName("NavLeading").rawValue shouldBe "NavLeading"
        shouldThrow<NShiftSlotNameError.InvalidName> { NShiftSlotName("leading") }
    }

    test("trigger onXxx camelCase") {
        val trigger = NShiftTrigger("onTap")
        trigger.rawValue shouldBe "onTap"
        NShiftTrigger.isValid("onTap") shouldBe true
        NShiftTrigger.isValid("onLongPress") shouldBe true
        NShiftTrigger.isValid("onAppear") shouldBe true
        NShiftTrigger.isValid("") shouldBe false
        NShiftTrigger.isValid("on") shouldBe false
        NShiftTrigger.isValid("OnTap") shouldBe false
        NShiftTrigger.isValid("ontap") shouldBe false
        NShiftTrigger.isValid("on_tap") shouldBe false
        NShiftTrigger.isValid("onTap1") shouldBe false
        NShiftTrigger.isValid("tapOn") shouldBe false
        NShiftTrigger.isValid("onToque") shouldBe true
        NShiftTrigger.isValid("onTapé") shouldBe false
        shouldThrow<NShiftTriggerError.InvalidTrigger> { NShiftTrigger("OnTap") }
    }

    test("render id positional paths") {
        val root = NShiftRenderID.root
        val child = root.appendingChild(0)
        val slot = child.appendingSlot(NShiftSlotName("Footer"), 1)
        root.rawValue shouldBe "r"
        child.rawValue shouldBe "r/c/0"
        slot.rawValue shouldBe "r/c/0/s/Footer/1"
    }
})

class NShiftVersionTests : FunSpec({
    test("accepts strict semver core") {
        val version = NShiftVersion.of("1.2.3")
        version.major shouldBe 1
        version.minor shouldBe 2
        version.patch shouldBe 3
        version.rawValue shouldBe "1.2.3"
    }

    test("rejects invalid semver") {
        shouldThrow<NShiftVersionError.InvalidVersion> { NShiftVersion.of("1.2") }
        shouldThrow<NShiftVersionError.InvalidVersion> { NShiftVersion.of("01.0.0") }
        shouldThrow<NShiftVersionError.InvalidVersion> { NShiftVersion.of("1.0.0-beta") }
        NShiftVersion.isValid("1.2.3") shouldBe true
        NShiftVersion.isValid("0.0.0") shouldBe true
        NShiftVersion.isValid("1.2") shouldBe false
        NShiftVersion.isValid("01.0.0") shouldBe false
    }

    test("compares for latest selection") {
        (NShiftVersion.of("1.2.0") < NShiftVersion.of("1.10.0")) shouldBe true
        (NShiftVersion.of("1.10.0") < NShiftVersion.of("2.0.0")) shouldBe true
    }

    test("catalog returns latest plugin") {
        val catalog = NShiftRegisteredVersionCatalog.forTesting()
        catalog.registerPlugin("Button", "1.0.0")
        catalog.registerPlugin("Button", "1.2.0")
        catalog.registerPlugin("Button", "1.10.0")
        catalog.latestPlugin(NShiftPluginName("Button")) shouldBe NShiftVersion(1, 10, 0)
    }

    test("resolver uses latest when unpinned") {
        val catalog = NShiftRegisteredVersionCatalog.forTesting()
        catalog.registerEvent("ShowToast", "1.0.0")
        catalog.registerEvent("ShowToast", "2.1.0")
        val pins = NShiftDSLVersionPins()
        pins.resolveEvent(NShiftEventName("ShowToast"), catalog) shouldBe NShiftVersion(2, 1, 0)
    }

    test("resolver honors use pin") {
        val catalog = NShiftRegisteredVersionCatalog.forTesting()
        catalog.registerPlugin("Screen", "1.0.0")
        catalog.registerPlugin("Screen", "2.0.0")
        val pins = NShiftDSLVersionPins(listOf("Screen" to "1.0.0"))
        pins.resolvePlugin(NShiftPluginName("Screen"), catalog) shouldBe NShiftVersion(1, 0, 0)
    }

    test("registration name joins name and version") {
        NShiftVersion.registrationName(NShiftPluginName("Button"), NShiftVersion.of("1.2.3")) shouldBe "Button@1.2.3"
        NShiftVersion.registrationName(NShiftEventName("ShowToast"), NShiftVersion.of("0.1.0")) shouldBe "ShowToast@0.1.0"
    }

    test("catalog rejects cross-kind name conflicts") {
        val pluginConflict = NShiftRegisteredVersionCatalog.forTesting()
        pluginConflict.registerEvent("Navigate", "1.0.0")
        shouldThrow<NShiftRegisteredVersionCatalogFailure> {
            pluginConflict.registerPlugin("Navigate", "1.0.0")
        }.message shouldBe "Plugin name \"Navigate\" conflicts with a registered event of the same name"

        val eventConflict = NShiftRegisteredVersionCatalog.forTesting()
        eventConflict.registerPlugin("Screen", "1.0.0")
        shouldThrow<NShiftRegisteredVersionCatalogFailure> {
            eventConflict.registerEvent("Screen", "1.0.0")
        }.message shouldBe "Event name \"Screen\" conflicts with a registered plugin of the same name"
    }

    test("resolver throws when missing") {
        val catalog = NShiftRegisteredVersionCatalog.forTesting()
        catalog.registerPlugin("Screen", "1.0.0")
        shouldThrow<NShiftVersionResolutionError.PluginNotRegistered> {
            NShiftVersionResolver.resolvePlugin(NShiftPluginName("Missing"), catalog = catalog)
        }
        shouldThrow<NShiftVersionResolutionError.PluginVersionNotRegistered> {
            NShiftVersionResolver.resolvePlugin(
                NShiftPluginName("Screen"),
                pinned = NShiftVersion.of("9.0.0"),
                catalog = catalog,
            )
        }
        catalog.registerEvent("ShowToast", "1.0.0")
        shouldThrow<NShiftVersionResolutionError.EventNotRegistered> {
            NShiftVersionResolver.resolveEvent(NShiftEventName("Missing"), catalog = catalog)
        }
        shouldThrow<NShiftVersionResolutionError.EventVersionNotRegistered> {
            NShiftVersionResolver.resolveEvent(
                NShiftEventName("ShowToast"),
                pinned = NShiftVersion.of("9.0.0"),
                catalog = catalog,
            )
        }
    }
})
