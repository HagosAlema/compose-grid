package io.github.composegrid.core

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Vertical geometry for the grid's rows: where each row starts, how tall it is,
 * and which rows intersect the viewport. The row-axis counterpart of
 * [GridColumnLayoutInfo], and likewise pure Kotlin so it's covered by plain
 * JUnit tests rather than instrumented ones.
 *
 * Two representations, because they have very different costs:
 *
 *  - [uniform] stores a single height and computes everything arithmetically.
 *    No allocation regardless of row count, and [visibleRowRange] is O(1).
 *  - [variable] precomputes cumulative offsets, so lookups are O(1) and
 *    [visibleRowRange] is O(log n) by binary search — at the cost of one float
 *    per row.
 *
 * A grid with a million uniform rows therefore costs nothing extra, and only
 * callers who actually need per-row heights pay for the array.
 */
class GridRowLayoutInfo private constructor(
    val rowCount: Int,
    /** Height of every row, when uniform; `NaN` when [offsets] drives instead. */
    private val uniformHeight: Float,
    /** Cumulative start offsets, length `rowCount + 1`; null when uniform. */
    private val offsets: FloatArray?,
) {
    /** Total height of all rows — the scrollable content height. */
    val totalHeight: Dp
        get() = if (offsets != null) offsets[rowCount].dp else (rowCount * uniformHeight).dp

    fun offset(index: Int): Dp =
        if (offsets != null) offsets[index].dp else (index * uniformHeight).dp

    fun height(index: Int): Dp =
        if (offsets != null) (offsets[index + 1] - offsets[index]).dp else uniformHeight.dp

    /**
     * Indices of rows intersecting the vertical window
     * `[scrollOffsetY, scrollOffsetY + viewportHeight)`.
     *
     * Takes [Dp] rather than raw pixels for the same reason
     * [GridColumnLayoutInfo.visibleColumnRange] does — mixing pixel scroll
     * offsets with dp geometry silently blanks the grid past a certain scroll
     * position, and the type stops that at the call site.
     *
     * Returns [IntRange.EMPTY] when nothing intersects: no rows, a zero-height
     * viewport, or rows with no height.
     */
    fun visibleRowRange(scrollOffsetY: Dp, viewportHeight: Dp): IntRange {
        if (rowCount == 0 || viewportHeight.value <= 0f) return IntRange.EMPTY
        val viewStart = scrollOffsetY.value
        val viewEnd = viewStart + viewportHeight.value

        if (offsets == null) {
            if (uniformHeight <= 0f) return IntRange.EMPTY
            val first = floor(viewStart / uniformHeight).toInt().coerceIn(0, rowCount - 1)
            val last = (ceil(viewEnd / uniformHeight).toInt() - 1).coerceIn(0, rowCount - 1)
            return if (first > last) IntRange.EMPTY else first..last
        }

        if (offsets[rowCount] <= 0f) return IntRange.EMPTY
        val first = lastRowStartingAtOrBefore(viewStart)
        val last = lastRowStartingBefore(viewEnd)
        return if (first > last) IntRange.EMPTY else first..last
    }

    /** Binary search: greatest `i` with `offsets[i] <= y`, clamped to a valid row. */
    private fun lastRowStartingAtOrBefore(y: Float): Int {
        var low = 0
        var high = rowCount - 1
        var result = 0
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (offsets!![mid] <= y) {
                result = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return result.coerceIn(0, rowCount - 1)
    }

    /** Greatest `i` whose row *starts* strictly before `y` — the last one on screen. */
    private fun lastRowStartingBefore(y: Float): Int {
        var low = 0
        var high = rowCount - 1
        var result = 0
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (offsets!![mid] < y) {
                result = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return result.coerceIn(0, rowCount - 1)
    }

    companion object {
        /** Every row the same height. Allocation-free and O(1) to query. */
        fun uniform(rowCount: Int, rowHeight: Dp): GridRowLayoutInfo =
            GridRowLayoutInfo(
                rowCount = rowCount.coerceAtLeast(0),
                uniformHeight = rowHeight.value.coerceAtLeast(0f),
                offsets = null,
            )

        /**
         * Per-row heights from [heightAt].
         *
         * [heightAt] is called once per row while building the offsets, for
         * *every* index — including rows a paged source hasn't loaded yet. It
         * receives only an index for exactly that reason: a height that depended
         * on row data couldn't be known before the row arrived, and the grid
         * would have no way to size the scrollbar or place anything below it.
         *
         * Negative heights are clamped to zero rather than corrupting the
         * cumulative offsets.
         */
        fun variable(rowCount: Int, heightAt: (index: Int) -> Dp): GridRowLayoutInfo {
            val count = rowCount.coerceAtLeast(0)
            val offsets = FloatArray(count + 1)
            var cursor = 0f
            for (i in 0 until count) {
                offsets[i] = cursor
                cursor += heightAt(i).value.coerceAtLeast(0f)
            }
            offsets[count] = cursor
            return GridRowLayoutInfo(rowCount = count, uniformHeight = Float.NaN, offsets = offsets)
        }
    }
}
