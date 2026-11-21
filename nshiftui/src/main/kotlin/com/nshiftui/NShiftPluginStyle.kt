package com.nshiftui

sealed class NShiftPluginDimension {
    data object Fill : NShiftPluginDimension()
    data class Value(val value: Double) : NShiftPluginDimension()

    val doubleValue: Double
        get() =
            when (this) {
                Fill -> Double.POSITIVE_INFINITY
                is Value -> value
            }
}

enum class NShiftPluginAlignment {
    TopLeading,
    Top,
    TopTrailing,
    Leading,
    Center,
    Trailing,
    BottomLeading,
    Bottom,
    BottomTrailing,
}

data class NShiftPluginFrame(
    val width: Double? = null,
    val height: Double? = null,
    val minWidth: Double? = null,
    val maxWidth: Double? = null,
    val minHeight: Double? = null,
    val maxHeight: Double? = null,
) {
    val hasFixedWidth: Boolean get() = width != null
    val hasFixedHeight: Boolean get() = height != null
    val hasFlexibleWidth: Boolean get() = minWidth != null || maxWidth != null
    val hasFlexibleHeight: Boolean get() = minHeight != null || maxHeight != null

    companion object {
        fun empty(): NShiftPluginFrame = NShiftPluginFrame()

        fun of(
            width: Double? = null,
            height: Double? = null,
            minWidth: NShiftPluginDimension? = null,
            maxWidth: NShiftPluginDimension? = null,
            minHeight: NShiftPluginDimension? = null,
            maxHeight: NShiftPluginDimension? = null,
        ): NShiftPluginFrame {
            validate(width, height, minWidth, maxWidth, minHeight, maxHeight)
            return NShiftPluginFrame(
                width = width,
                height = height,
                minWidth = minWidth?.doubleValue,
                maxWidth = maxWidth?.doubleValue,
                minHeight = minHeight?.doubleValue,
                maxHeight = maxHeight?.doubleValue,
            )
        }

        private fun validate(
            width: Double?,
            height: Double?,
            minWidth: NShiftPluginDimension?,
            maxWidth: NShiftPluginDimension?,
            minHeight: NShiftPluginDimension?,
            maxHeight: NShiftPluginDimension?,
        ) {
            if (width != null && (minWidth != null || maxWidth != null)) {
                throw NShiftPluginFrameError.FixedWidthConflictsWithFlexibleConstraints
            }
            if (height != null && (minHeight != null || maxHeight != null)) {
                throw NShiftPluginFrameError.FixedHeightConflictsWithFlexibleConstraints
            }
            if (minWidth == NShiftPluginDimension.Fill) {
                throw NShiftPluginFrameError.FillNotAllowedForMinWidth
            }
            if (minHeight == NShiftPluginDimension.Fill) {
                throw NShiftPluginFrameError.FillNotAllowedForMinHeight
            }
            val minW = (minWidth as? NShiftPluginDimension.Value)?.value
            val maxW = (maxWidth as? NShiftPluginDimension.Value)?.value
            if (minW != null && maxW != null && minW > maxW) {
                throw NShiftPluginFrameError.ContradictoryWidthConstraints
            }
            val minH = (minHeight as? NShiftPluginDimension.Value)?.value
            val maxH = (maxHeight as? NShiftPluginDimension.Value)?.value
            if (minH != null && maxH != null && minH > maxH) {
                throw NShiftPluginFrameError.ContradictoryHeightConstraints
            }
        }
    }
}

sealed class NShiftPluginFrameError : Exception() {
    data object FixedWidthConflictsWithFlexibleConstraints : NShiftPluginFrameError()
    data object FixedHeightConflictsWithFlexibleConstraints : NShiftPluginFrameError()
    data object FillNotAllowedForMinWidth : NShiftPluginFrameError()
    data object FillNotAllowedForMinHeight : NShiftPluginFrameError()
    data object ContradictoryWidthConstraints : NShiftPluginFrameError()
    data object ContradictoryHeightConstraints : NShiftPluginFrameError()
}

data class NShiftSafeAreaEdges(
    val top: Boolean = false,
    val bottom: Boolean = false,
    val leading: Boolean = false,
    val trailing: Boolean = false,
) {
    val isEmpty: Boolean
        get() = !top && !bottom && !leading && !trailing

    companion object {
        val none = NShiftSafeAreaEdges()
        val all = NShiftSafeAreaEdges(top = true, bottom = true, leading = true, trailing = true)

        fun parse(tokens: List<String>): NShiftSafeAreaEdges {
            val names = tokens.map { it.removePrefix(".") }
            if (names.contains("all")) return all
            return NShiftSafeAreaEdges(
                top = names.contains("top"),
                bottom = names.contains("bottom"),
                leading = names.contains("leading"),
                trailing = names.contains("trailing"),
            )
        }
    }
}

data class NShiftPluginPadding(
    val top: Int? = null,
    val leading: Int? = null,
    val trailing: Int? = null,
    val horizontal: Int? = null,
    val vertical: Int? = null,
    val bottom: Int? = null,
) {
    val resolvedTop: Int get() = top ?: vertical ?: 0
    val resolvedBottom: Int get() = bottom ?: vertical ?: 0
    val resolvedLeading: Int get() = leading ?: horizontal ?: 0
    val resolvedTrailing: Int get() = trailing ?: horizontal ?: 0
    val isZero: Boolean
        get() = resolvedTop == 0 && resolvedBottom == 0 && resolvedLeading == 0 && resolvedTrailing == 0

    companion object {
        val zero = NShiftPluginPadding()
    }
}

data class NShiftPluginOffset(
    val x: Double = 0.0,
    val y: Double = 0.0,
) {
    companion object {
        val zero = NShiftPluginOffset()
    }
}

data class NShiftPluginBackground(
    val color: String? = null,
    val ignoreSafeArea: NShiftSafeAreaEdges = NShiftSafeAreaEdges.none,
)

enum class NShiftAnimationCurve {
    Linear,
    EaseIn,
    EaseOut,
    EaseInOut,
}

data class NShiftPluginAnimation(
    val isEnabled: Boolean = false,
    val durationMillis: Int = 300,
    val delayMillis: Int = 0,
    val curve: NShiftAnimationCurve = NShiftAnimationCurve.EaseInOut,
)

data class NShiftPluginStyle(
    val frame: NShiftPluginFrame = NShiftPluginFrame.empty(),
    val background: NShiftPluginBackground? = null,
    val padding: NShiftPluginPadding = NShiftPluginPadding.zero,
    val ignoreSafeArea: NShiftSafeAreaEdges = NShiftSafeAreaEdges.none,
    val offset: NShiftPluginOffset = NShiftPluginOffset.zero,
    val animation: NShiftPluginAnimation? = null,
)
