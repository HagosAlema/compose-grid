package io.github.composegrid.core

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [GridRowLayoutInfo] decides which rows exist on screen and where they sit.
 * Both are load-bearing: an off-by-one in [GridRowLayoutInfo.visibleRowRange]
 * leaves a blank strip at a viewport edge, and a wrong offset misplaces every
 * row below it.
 *
 * The uniform and variable representations are tested against the same
 * expectations wherever they should agree, since the whole point of keeping two
 * is that callers can't tell them apart.
 */
class GridRowLayoutInfoTest {

    // --- uniform -----------------------------------------------------------

    @Test
    fun uniformReportsOffsetsHeightsAndTotal() {
        val rows = GridRowLayoutInfo.uniform(rowCount = 5, rowHeight = 20.dp)

        assertEquals(0f, rows.offset(0).value, 0.01f)
        assertEquals(60f, rows.offset(3).value, 0.01f)
        assertEquals(20f, rows.height(3).value, 0.01f)
        assertEquals(100f, rows.totalHeight.value, 0.01f)
    }

    @Test
    fun uniformVisibleRangeCoversPartiallyShownRows() {
        val rows = GridRowLayoutInfo.uniform(rowCount = 10, rowHeight = 20.dp)

        // Window 30–70 clips row 1 and row 3, so all of 1..3 must be composed.
        assertEquals(1..3, rows.visibleRowRange(30.dp, 40.dp))
    }

    @Test
    fun uniformVisibleRangeClampsAtTheEnd() {
        val rows = GridRowLayoutInfo.uniform(rowCount = 4, rowHeight = 20.dp)

        // Viewport extends past the content; the range must stop at the last row.
        assertEquals(2..3, rows.visibleRowRange(50.dp, 200.dp))
    }

    // --- variable ----------------------------------------------------------

    /** Heights 10, 30, 20, 40 → starts at 0, 10, 40, 60; total 100. */
    private fun steppedRows() =
        GridRowLayoutInfo.variable(rowCount = 4) { listOf(10, 30, 20, 40)[it].dp }

    @Test
    fun variableAccumulatesOffsets() {
        val rows = steppedRows()

        assertEquals(0f, rows.offset(0).value, 0.01f)
        assertEquals(10f, rows.offset(1).value, 0.01f)
        assertEquals(40f, rows.offset(2).value, 0.01f)
        assertEquals(60f, rows.offset(3).value, 0.01f)
        assertEquals(100f, rows.totalHeight.value, 0.01f)
    }

    @Test
    fun variableReportsEachRowsOwnHeight() {
        val rows = steppedRows()

        assertEquals(10f, rows.height(0).value, 0.01f)
        assertEquals(30f, rows.height(1).value, 0.01f)
        assertEquals(20f, rows.height(2).value, 0.01f)
        assertEquals(40f, rows.height(3).value, 0.01f)
    }

    @Test
    fun variableVisibleRangeFindsRowsSpanningTheWindow() {
        val rows = steppedRows()

        // Window 15–45 starts inside row 1 (10–40) and ends inside row 2 (40–60).
        assertEquals(1..2, rows.visibleRowRange(15.dp, 30.dp))
    }

    @Test
    fun variableVisibleRangeIncludesATallRowStraddlingTheWholeViewport() {
        // A row taller than the viewport must still be the only one returned,
        // rather than the range collapsing to empty.
        val rows = GridRowLayoutInfo.variable(rowCount = 3) { listOf(10, 500, 10)[it].dp }

        assertEquals(1..1, rows.visibleRowRange(100.dp, 50.dp))
    }

    @Test
    fun variableVisibleRangeAtAnExactBoundaryDoesNotIncludeTheRowAbove() {
        val rows = steppedRows()

        // Scrolled exactly to row 2's start (40). Row 1 ends there and is off
        // screen; including it would compose a row nobody can see.
        assertEquals(2..2, rows.visibleRowRange(40.dp, 20.dp))
    }

    @Test
    fun variableVisibleRangeClampsPastTheEnd() {
        val rows = steppedRows()

        assertEquals(3..3, rows.visibleRowRange(90.dp, 200.dp))
    }

    @Test
    fun variableAgreesWithUniformWhenEveryHeightIsTheSame() {
        val uniform = GridRowLayoutInfo.uniform(rowCount = 10, rowHeight = 20.dp)
        val asVariable = GridRowLayoutInfo.variable(rowCount = 10) { 20.dp }

        assertEquals(uniform.totalHeight.value, asVariable.totalHeight.value, 0.01f)
        for (scroll in listOf(0, 15, 30, 55, 199)) {
            assertEquals(
                "range differs at scroll $scroll",
                uniform.visibleRowRange(scroll.dp, 40.dp),
                asVariable.visibleRowRange(scroll.dp, 40.dp),
            )
        }
    }

    // --- degenerate cases --------------------------------------------------

    @Test
    fun noRowsIsAlwaysEmpty() {
        assertEquals(IntRange.EMPTY, GridRowLayoutInfo.uniform(0, 20.dp).visibleRowRange(0.dp, 100.dp))
        assertEquals(IntRange.EMPTY, GridRowLayoutInfo.variable(0) { 20.dp }.visibleRowRange(0.dp, 100.dp))
    }

    @Test
    fun zeroHeightViewportShowsNothing() {
        val rows = GridRowLayoutInfo.uniform(10, 20.dp)

        assertEquals(IntRange.EMPTY, rows.visibleRowRange(0.dp, 0.dp))
    }

    @Test
    fun zeroHeightRowsShowNothing() {
        assertEquals(IntRange.EMPTY, GridRowLayoutInfo.uniform(10, 0.dp).visibleRowRange(0.dp, 100.dp))
        assertEquals(IntRange.EMPTY, GridRowLayoutInfo.variable(10) { 0.dp }.visibleRowRange(0.dp, 100.dp))
    }

    @Test
    fun negativeHeightsAreClampedRatherThanCorruptingOffsets() {
        val rows = GridRowLayoutInfo.variable(rowCount = 3) { if (it == 1) (-50).dp else 20.dp }

        // The bad row contributes nothing; rows after it stay in ascending order.
        assertEquals(20f, rows.offset(1).value, 0.01f)
        assertEquals(20f, rows.offset(2).value, 0.01f)
        assertEquals(40f, rows.totalHeight.value, 0.01f)
    }
}
