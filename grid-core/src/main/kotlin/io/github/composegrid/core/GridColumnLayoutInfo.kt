package io.github.composegrid.core

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Resolved width and x-offset for every column, given a container width and
 * any live resize overrides ([GridState.columnWidthOverrides]).
 *
 * Shared by the rendering engine (to decide what's on-screen and where to
 * place it) and the resize gesture (to hit-test column borders), so both
 * always agree on column geometry. Pure Kotlin — no `@Composable` context
 * required — so it's covered by plain JUnit tests instead of instrumented
 * ones.
 */
class GridColumnLayoutInfo internal constructor(
    private val offsets: FloatArray,
    private val widths: FloatArray,
    val totalWidth: Dp,
) {
    val columnCount: Int get() = widths.size

    fun offset(index: Int): Dp = offsets[index].dp
    fun width(index: Int): Dp = widths[index].dp

    /**
     * Indices of columns whose span intersects the horizontal window
     * `[scrollOffsetX, scrollOffsetX + viewportWidth)`. Takes [Dp] (matching
     * [offset]/[width]/[totalWidth]) rather than a raw pixel [Float] so a
     * caller can't accidentally mix pixel and dp values here — that mismatch
     * previously made the grid go blank past a certain scroll position
     * because pixel-valued scroll offsets were compared against dp-valued
     * column offsets.
     *
     * Returns [IntRange.EMPTY] if no column intersects (e.g. an empty column
     * list, or a zero-width viewport).
     */
    /**
     * How far to scroll horizontally to bring exactly one more column fully
     * into view: the first column past the trailing edge when [forward], or the
     * last one before the leading edge when not. Positive scrolls towards the
     * end, negative towards the start.
     *
     * Returns `0.dp` when every column on that side is already visible, so a
     * caller can treat zero as "nothing to reveal" and stop.
     *
     * Backs keyboard navigation: focus search can only reach composed cells, so
     * moving past the viewport edge means scrolling by exactly one column first
     * rather than by an arbitrary amount.
     */
    fun revealStep(scrollOffsetX: Dp, viewportWidth: Dp, forward: Boolean): Dp {
        if (columnCount == 0) return 0.dp
        val viewStart = scrollOffsetX.value
        val viewEnd = viewStart + viewportWidth.value
        if (forward) {
            for (i in 0 until columnCount) {
                val columnEnd = offsets[i] + widths[i]
                if (columnEnd > viewEnd + EdgeTolerance) return (columnEnd - viewEnd).dp
            }
        } else {
            for (i in columnCount - 1 downTo 0) {
                val columnStart = offsets[i]
                if (columnStart < viewStart - EdgeTolerance) return (columnStart - viewStart).dp
            }
        }
        return 0.dp
    }

    fun visibleColumnRange(scrollOffsetX: Dp, viewportWidth: Dp): IntRange {
        val viewStart = scrollOffsetX.value
        val viewEnd = viewStart + viewportWidth.value
        var first = -1
        var last = -1
        for (i in 0 until columnCount) {
            val start = offsets[i]
            val end = start + widths[i]
            if (end > viewStart && start < viewEnd) {
                if (first == -1) first = i
                last = i
            } else if (first != -1) {
                // Columns are laid out in increasing offset order, so once we've
                // seen the intersecting run end, nothing later can intersect.
                break
            }
        }
        return if (first == -1) IntRange.EMPTY else first..last
    }

    companion object {
        /**
         * Resolves [columns]' widths against [containerWidth]: [GridColumnWidth.Fixed]
         * columns keep their declared width, [GridColumnWidth.Range] columns use
         * their live override from [widthOverrides] (clamped to `[min, max]`) or
         * their initial width, and the remaining space is split among
         * [GridColumnWidth.Weighted] columns proportionally to their weight.
         */
        fun resolve(
            columns: List<GridColumn<*>>,
            containerWidth: Dp,
            widthOverrides: Map<String, Dp> = emptyMap(),
        ): GridColumnLayoutInfo {
            val widths = FloatArray(columns.size)
            var fixedTotal = 0f
            var weightSum = 0f

            columns.forEachIndexed { i, column ->
                when (val w = column.width) {
                    is GridColumnWidth.Fixed -> {
                        widths[i] = w.width.value
                        fixedTotal += widths[i]
                    }

                    is GridColumnWidth.Range -> {
                        val override = widthOverrides[column.id]
                        val resolved = (override ?: w.initial).value
                            .coerceIn(w.min.value, w.max.value)
                        widths[i] = resolved
                        fixedTotal += resolved
                    }

                    is GridColumnWidth.Weighted -> {
                        weightSum += w.weight
                        // Resolved in the pass below, once fixedTotal is final.
                    }
                }
            }

            if (weightSum > 0f) {
                val remaining = (containerWidth.value - fixedTotal).coerceAtLeast(0f)
                columns.forEachIndexed { i, column ->
                    val w = column.width
                    if (w is GridColumnWidth.Weighted) {
                        widths[i] = remaining * (w.weight / weightSum)
                    }
                }
            }

            val offsets = FloatArray(columns.size)
            var cursor = 0f
            for (i in columns.indices) {
                offsets[i] = cursor
                cursor += widths[i]
            }

            return GridColumnLayoutInfo(offsets, widths, cursor.dp)
        }

        /**
         * Slack when judging whether a column edge is already visible, so dp
         * rounding can't make a flush column look like it still needs
         * revealing — which would leave [revealStep] returning a sliver
         * forever instead of reporting "nothing to do".
         */
        private const val EdgeTolerance = 0.5f
    }
}
