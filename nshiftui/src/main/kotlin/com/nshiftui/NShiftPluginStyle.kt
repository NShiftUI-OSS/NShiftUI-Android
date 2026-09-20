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

data class NShiftPluginStyle(
    val frame: NShiftPluginFrame = NShiftPluginFrame.empty(),
    val alignment: NShiftPluginAlignment = NShiftPluginAlignment.Center,
    val spacing: Double = 0.0,
)
