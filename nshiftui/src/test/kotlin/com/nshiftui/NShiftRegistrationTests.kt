package com.nshiftui

import androidx.compose.runtime.Composable
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest

private data class ButtonMeta(val title: String) : NShiftMetadata

private class ButtonPlugin : NShiftPlugin("Button", "1.0.0", ButtonMeta::class) {
    @Composable
    override fun Content() {
    }
}

private class ScreenViewModel : NShiftPluginViewModel

private class ScreenPlugin : NShiftPluginContainer<ScreenViewModel>(
    name = "Screen",
    version = "1.0.0",
    viewModelType = ScreenViewModel::class,
) {
    @Composable
    override fun Content() {
    }
}

private class ToastEvent : NShiftEvent("ShowToast", "1.0.0") {
    var executed = false

    override suspend fun execute() {
        executed = true
    }
}

class NShiftPluginEventRegistrationTests : FunSpec({
    afterEach {
        NShiftConfig.shared.resetForTesting()
    }

    test("registerPlugin binds metadata and skips when unwrap fails") {
        val registry = NShiftDependencyRegistry()
        NShiftConfig.shared.startup(registry)
        registry.registerPlugin<ButtonPlugin>()
        val store = registry.resolve<NShiftPluginStore>()!!
        val ok = NShiftPluginModel(
            name = NShiftPluginName("Button"),
            version = NShiftVersion.of("1.0.0"),
            metadata = ButtonMeta("Go"),
        )
        (store.resolve(ok) != null) shouldBe true
        val missing = NShiftPluginModel(
            name = NShiftPluginName("Button"),
            version = NShiftVersion.of("1.0.0"),
        )
        store.resolve(missing) shouldBe null
    }

    test("registerPlugin container requires view model") {
        val registry = NShiftDependencyRegistry()
        registry.register<ScreenViewModel> { ScreenViewModel() }
        NShiftConfig.shared.startup(registry)
        registry.registerPlugin<ScreenPlugin>()
        NShiftPluginContainerCatalog.shared.contains(NShiftPluginName("Screen")) shouldBe true
        val store = registry.resolve<NShiftPluginStore>()!!
        val model = NShiftPluginModel(
            name = NShiftPluginName("Screen"),
            version = NShiftVersion.of("1.0.0"),
        )
        (store.resolve(model) != null) shouldBe true
    }

    test("registerEvent executes") {
        val registry = NShiftDependencyRegistry()
        NShiftConfig.shared.startup(registry)
        registry.registerEvent<ToastEvent>()
        val handler = registry.resolve<NShiftEventHandler>()!!
        val model = NShiftEventModel(
            name = NShiftEventName("ShowToast"),
            version = NShiftVersion.of("1.0.0"),
            trigger = NShiftTrigger("onTap"),
        )
        runTest {
            handler.handleThrowing(NShiftTrigger("onTap"), listOf(model))
        }
    }

    test("startup is idempotent-protected") {
        val registry = NShiftDependencyRegistry()
        NShiftConfig.shared.startup(registry)
        var failed = false
        try {
            NShiftConfig.shared.startup(registry)
        } catch (_: NShiftRuntimePreconditionFailure) {
            failed = true
        }
        failed shouldBe true
    }
})
