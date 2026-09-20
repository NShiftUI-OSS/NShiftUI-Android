package com.nshiftui.ksptest

import com.nshiftui.NShiftConfig
import com.nshiftui.NShiftDependencyRegistry
import com.nshiftui.NShiftEventHandler
import com.nshiftui.NShiftEventModel
import com.nshiftui.NShiftEventName
import com.nshiftui.NShiftPluginModel
import com.nshiftui.NShiftPluginName
import com.nshiftui.NShiftPluginStore
import com.nshiftui.NShiftTrigger
import com.nshiftui.NShiftVersion
import com.nshiftui.generated.installNShiftGenerated
import com.nshiftui.resolve
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest

class NShiftKspTests : FunSpec({
    afterEach {
        NShiftConfig.shared.resetForTesting()
    }

    test("installNShiftGenerated registers annotated class and function plugins") {
        val registry = NShiftDependencyRegistry()
        NShiftConfig.shared.startup(registry)
        registry.installNShiftGenerated()
        val store = registry.resolve<NShiftPluginStore>()!!

        val button = NShiftPluginModel(
            name = NShiftPluginName("KspButton"),
            version = NShiftVersion.of("1.0.0"),
            metadata = KspButtonMeta("Go"),
        )
        (store.resolve(button) != null) shouldBe true

        val label = NShiftPluginModel(
            name = NShiftPluginName("KspLabel"),
            version = NShiftVersion.of("1.0.0"),
            metadata = KspButtonMeta("Hi"),
        )
        (store.resolve(label) != null) shouldBe true
    }

    test("installNShiftGenerated registers annotated class and function events") {
        val registry = NShiftDependencyRegistry()
        NShiftConfig.shared.startup(registry)
        registry.installNShiftGenerated()
        val handler = registry.resolve<NShiftEventHandler>()!!
        runTest {
            handler.handleThrowing(
                NShiftTrigger("onTap"),
                listOf(
                    NShiftEventModel(
                        name = NShiftEventName("KspToast"),
                        version = NShiftVersion.of("1.0.0"),
                        trigger = NShiftTrigger("onTap"),
                    ),
                    NShiftEventModel(
                        name = NShiftEventName("KspLog"),
                        version = NShiftVersion.of("2.0.0"),
                        trigger = NShiftTrigger("onTap"),
                    ),
                ),
            )
        }
    }
})
