package com.nshiftui

import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun NShiftNodeView(
    node: NShiftPluginNode,
    engine: NShiftEngineTree,
    viewModel: NShiftPluginViewModel? = null,
) {
    val content = engine.makeView(node, viewModel) ?: return
    val frame = node.model.style.frame
    Box(
        modifier = Modifier.nShiftPluginStyle(node.model.style),
        contentAlignment = Alignment.Center,
        propagateMinConstraints = frame.hasFixedWidth || frame.hasFixedHeight,
    ) {
        content()
    }
}

val LocalNShiftColors = compositionLocalOf<(String) -> Color> { { token -> parseNShiftColor(token) ?: Color.Unspecified } }

fun parseNShiftColor(token: String): Color? {
    val hex = token.trim().removePrefix("#")
    if (hex.length != 6 && hex.length != 8) return null
    val value = hex.toLongOrNull(16) ?: return null
    val color = if (hex.length == 6) value or 0xFF000000 else value
    return Color(color.toInt())
}

@Composable
fun NShiftView(model: NShiftPluginModel) {
    NShiftRuntimePrecondition.check(
        NShiftPluginContainerCatalog.shared.contains(model.name),
        "NShiftView requires a plugin model whose name is registered as a plugin container.",
    )
    val engine = remember(model) { makeNShiftEngine(model) }
    NShiftView(engine)
}

/**
 * Creates the engine [NShiftView] renders for [model]. Hosts that must keep a
 * screen's live tree (appended children, view model state) while it is off
 * screen can retain this engine and render it again with [NShiftView].
 */
fun makeNShiftEngine(model: NShiftPluginModel): NShiftEngineTree {
    NShiftRuntimePrecondition.check(
        NShiftPluginContainerCatalog.shared.contains(model.name),
        "NShiftView requires a plugin model whose name is registered as a plugin container.",
    )
    return NShiftEngineResolver.makeEngine(rootModel = model) as? NShiftEngineTree
        ?: NShiftRuntimePrecondition.raise("NShiftView requires an engine that conforms to NShiftEngineTree.")
}

/** Renders an engine created by [makeNShiftEngine], keeping its current tree. */
@Composable
fun NShiftView(engine: NShiftEngineTree) {
    val rootID = engine.rootID
    DisposableEffect(rootID, engine) {
        NShiftPluginContainerRegistry.shared.register(rootID, engine)
        onDispose {
            NShiftPluginContainerRegistry.shared.unregister(rootID)
        }
    }
    NShiftNodeView(node = engine.root, engine = engine)
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

@Composable
fun Modifier.nShiftPluginStyle(style: NShiftPluginStyle): Modifier {
    val animation = style.animation?.takeIf { it.isEnabled }
    val spec = animation?.spec() ?: snap()
    val offsetX by animateDpAsState(style.offset.x.dp, spec, label = "nshift-offset-x")
    val offsetY by animateDpAsState(style.offset.y.dp, spec, label = "nshift-offset-y")
    val frame = style.frame
    var modifier = this
    val padding = style.padding
    if (!padding.isZero) {
        modifier = modifier.padding(
            start = padding.resolvedLeading.dp,
            top = padding.resolvedTop.dp,
            end = padding.resolvedTrailing.dp,
            bottom = padding.resolvedBottom.dp,
        )
    }
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
    val background = style.background
    val colorName = background?.color
    if (!colorName.isNullOrEmpty()) {
        val color = LocalNShiftColors.current(colorName)
        if (color != Color.Unspecified) {
            val edges = background.ignoreSafeArea
            modifier = if (edges.isEmpty) {
                modifier.background(color)
            } else {
                modifier.nShiftBackground(color, edges)
            }
        }
    }
    if (offsetX != 0.dp || offsetY != 0.dp) {
        modifier = modifier.offset(x = offsetX, y = offsetY)
    }
    if (!style.ignoreSafeArea.isEmpty) {
        modifier = modifier.nShiftIgnoreSafeArea(style.ignoreSafeArea)
    }
    return modifier
}

private fun NShiftPluginAnimation.spec(): FiniteAnimationSpec<Dp> {
    val easing = when (curve) {
        NShiftAnimationCurve.Linear -> LinearEasing
        NShiftAnimationCurve.EaseIn -> FastOutLinearInEasing
        NShiftAnimationCurve.EaseOut -> LinearOutSlowInEasing
        NShiftAnimationCurve.EaseInOut -> FastOutSlowInEasing
    }
    return tween(durationMillis = durationMillis, delayMillis = delayMillis, easing = easing)
}

@Composable
private fun Modifier.nShiftBackground(color: Color, edges: NShiftSafeAreaEdges): Modifier {
    val insets = safeAreaPixels(edges)
    return drawBehind {
        drawRect(
            color = color,
            topLeft = Offset(-insets.start.toFloat(), -insets.top.toFloat()),
            size = Size(
                size.width + insets.start + insets.end,
                size.height + insets.top + insets.bottom,
            ),
        )
    }
}

@Composable
private fun Modifier.nShiftIgnoreSafeArea(edges: NShiftSafeAreaEdges): Modifier {
    val insets = safeAreaPixels(edges)
    if (insets.isEmpty) return this
    return layout { measurable, constraints ->
        val maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth + insets.start + insets.end else constraints.maxWidth
        val maxHeight = if (constraints.hasBoundedHeight) constraints.maxHeight + insets.top + insets.bottom else constraints.maxHeight
        val placeable = measurable.measure(constraints.copy(maxWidth = maxWidth, maxHeight = maxHeight))
        val width = (placeable.width - insets.start - insets.end).coerceAtLeast(0)
        val height = (placeable.height - insets.top - insets.bottom).coerceAtLeast(0)
        layout(width, height) {
            placeable.place(-insets.start, -insets.top)
        }
    }
}

private data class SafeAreaPixels(val top: Int, val bottom: Int, val start: Int, val end: Int) {
    val isEmpty: Boolean get() = top == 0 && bottom == 0 && start == 0 && end == 0
}

@Composable
private fun safeAreaPixels(edges: NShiftSafeAreaEdges): SafeAreaPixels {
    if (edges.isEmpty) return SafeAreaPixels(0, 0, 0, 0)
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val insets = WindowInsets.safeDrawing
    return SafeAreaPixels(
        top = if (edges.top) insets.getTop(density) else 0,
        bottom = if (edges.bottom) insets.getBottom(density) else 0,
        start = if (edges.leading) insets.getLeft(density, direction) else 0,
        end = if (edges.trailing) insets.getRight(density, direction) else 0,
    )
}
