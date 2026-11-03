package com.nshiftui

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun NShiftNodeView(
    node: NShiftPluginNode,
    engine: NShiftEngineTree,
    viewModel: NShiftPluginViewModel? = null,
) {
    val content = engine.makeView(node, viewModel)
    if (content != null) {
        content()
    }
}

@Composable
fun NShiftView(model: NShiftPluginModel) {
    NShiftRuntimePrecondition.check(
        NShiftPluginContainerCatalog.shared.contains(model.name),
        "NShiftView requires a plugin model whose name is registered as a plugin container.",
    )
    val rootID = model.id ?: NShiftRenderID.root.rawValue
    val engine = remember(model) { NShiftEngineResolver.makeEngine(rootModel = model) }
    val tree = engine as? NShiftEngineTree
        ?: NShiftRuntimePrecondition.raise("NShiftView requires an engine that conforms to NShiftEngineTree.")
    DisposableEffect(rootID, engine) {
        NShiftPluginContainerRegistry.shared.register(rootID, engine)
        onDispose {
            NShiftPluginContainerRegistry.shared.unregister(rootID)
        }
    }
    NShiftNodeView(node = tree.root, engine = tree)
}

val NShiftPluginAlignment.composeAlignment: Alignment
    get() =
        when (this) {
            NShiftPluginAlignment.TopLeading -> Alignment.TopStart
            NShiftPluginAlignment.Top -> Alignment.TopCenter
            NShiftPluginAlignment.TopTrailing -> Alignment.TopEnd
            NShiftPluginAlignment.Leading -> Alignment.CenterStart
            NShiftPluginAlignment.Center -> Alignment.Center
            NShiftPluginAlignment.Trailing -> Alignment.CenterEnd
            NShiftPluginAlignment.BottomLeading -> Alignment.BottomStart
            NShiftPluginAlignment.Bottom -> Alignment.BottomCenter
            NShiftPluginAlignment.BottomTrailing -> Alignment.BottomEnd
        }

fun Modifier.nShiftPluginStyle(style: NShiftPluginStyle): Modifier {
    val frame = style.frame
    var modifier = this
    if (frame.hasFixedWidth || frame.hasFixedHeight) {
        frame.width?.let { modifier = modifier.width(it.dp) }
        frame.height?.let { modifier = modifier.height(it.dp) }
    }
    if (frame.hasFlexibleWidth || frame.hasFlexibleHeight) {
        val minW = frame.minWidth
        val maxW = frame.maxWidth
        val minH = frame.minHeight
        val maxH = frame.maxHeight
        when {
            maxW == Double.POSITIVE_INFINITY -> modifier = modifier.fillMaxWidth()
            minW != null || maxW != null ->
                modifier = modifier.widthIn(
                    min = minW?.dp ?: Dp.Unspecified,
                    max = maxW?.dp ?: Dp.Unspecified,
                )
        }
        when {
            maxH == Double.POSITIVE_INFINITY -> modifier = modifier.fillMaxHeight()
            minH != null || maxH != null ->
                modifier = modifier.heightIn(
                    min = minH?.dp ?: Dp.Unspecified,
                    max = maxH?.dp ?: Dp.Unspecified,
                )
        }
    }
    return modifier
}
