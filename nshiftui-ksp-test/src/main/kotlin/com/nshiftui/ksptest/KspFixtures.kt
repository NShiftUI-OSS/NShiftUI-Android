package com.nshiftui.ksptest

import androidx.compose.runtime.Composable
import com.nshiftui.NShiftEvent as NShiftEventBase
import com.nshiftui.NShiftEventScope
import com.nshiftui.NShiftMetadata
import com.nshiftui.NShiftPlugin as NShiftPluginBase
import com.nshiftui.NShiftPluginScope
import com.nshiftui.macro.NShiftEvent
import com.nshiftui.macro.NShiftPlugin

data class KspButtonMeta(val title: String) : NShiftMetadata

@NShiftPlugin
class KspButtonPlugin : NShiftPluginBase("KspButton", "1.0.0", KspButtonMeta::class) {
    @Composable
    override fun Content() {
    }
}

@NShiftPlugin(name = "KspLabel", version = "1.0.0", metadata = KspButtonMeta::class)
@Composable
fun KspLabel(scope: NShiftPluginScope) {
    scope.metadata<KspButtonMeta>()
}

@NShiftEvent
class KspToastEvent : NShiftEventBase("KspToast", "1.0.0") {
    override suspend fun execute() {
    }
}

@NShiftEvent(name = "KspLog", version = "2.0.0")
suspend fun KspLog(scope: NShiftEventScope) {
    scope.model
}
