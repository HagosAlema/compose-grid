package io.github.composegrid.core

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [GridColumnLayoutInfo.revealStep] decides how far keyboard navigation scrolls
 * when focus needs a column that isn't composed yet. Getting it wrong is
 * user-visible in two distinct ways: too small a step leaves the target still
 * off-screen (focus never advances), and a non-zero step when everything is
 * already visible swallows the key event forever.
 *
 * Lives in unit tests rather than instrumented ones because the arithmetic is
 * pure — and because `requestFocus()` doesn't grant focus under compose-ui-test
 * in this project, so the end-to-end path is verified by hand on a device
 * instead. See DEVELOPMENT_PLAN.md §7.
 */
class GridColumnRevealStepTest {

    /** Three 100dp columns: spans 0–100, 100–200, 200–300. */
    private fun threeFixedColumns() = GridColumnLayoutInfo.resolve(
        columns = List(3) { index ->
            GridColumn<Any>(
                id = "c$index",
                header = {},
                width = GridColumnWidth.Fixed(100.dp),
                cell = {},
            )
        },
        containerWidth = 250.dp,
    )

    @Test
    fun forwardRevealsTheNextPartiallyHiddenColumn() {
        val layout = threeFixedColumns()

        // Viewport 0–150 shows column 0 fully and half of column 1. Revealing
        // the rest of column 1 means scrolling to its right edge at 200.
        val step = layout.revealStep(scrollOffsetX = 0.dp, viewportWidth = 150.dp, forward = true)

        assertEquals(50f, step.value, 0.01f)
    }

    @Test
    fun forwardStopsAtTheLastColumn() {
        val layout = threeFixedColumns()

        // Viewport 50–300 already reaches the end of the content.
        val step = layout.revealStep(scrollOffsetX = 50.dp, viewportWidth = 250.dp, forward = true)

        assertEquals(0f, step.value, 0.01f)
    }

    @Test
    fun forwardIsZeroWhenEveryColumnFits() {
        val layout = threeFixedColumns()

        val step = layout.revealStep(scrollOffsetX = 0.dp, viewportWidth = 300.dp, forward = true)

        assertEquals(0f, step.value, 0.01f)
    }

    @Test
    fun backwardRevealsThePreviousHiddenColumn() {
        val layout = threeFixedColumns()

        // Viewport 150–300 hides column 0 entirely and clips column 1, whose
        // left edge is at 100 — so scroll back by 50 to reach it.
        val step = layout.revealStep(scrollOffsetX = 150.dp, viewportWidth = 150.dp, forward = false)

        assertEquals(-50f, step.value, 0.01f)
    }

    @Test
    fun backwardStopsAtTheFirstColumn() {
        val layout = threeFixedColumns()

        val step = layout.revealStep(scrollOffsetX = 0.dp, viewportWidth = 150.dp, forward = false)

        assertEquals(0f, step.value, 0.01f)
    }

    @Test
    fun aFlushColumnEdgeCountsAsAlreadyVisible() {
        val layout = threeFixedColumns()

        // Viewport 0–100 ends exactly on column 0's right edge. Without the
        // rounding tolerance this would report a sliver to scroll and the key
        // event would be consumed without focus ever moving.
        val step = layout.revealStep(scrollOffsetX = 0.dp, viewportWidth = 100.dp, forward = true)

        assertEquals(100f, step.value, 0.01f) // reveals column 1, not a sliver of column 0
    }

    @Test
    fun emptyColumnListRevealsNothing() {
        val layout = GridColumnLayoutInfo.resolve(columns = emptyList(), containerWidth = 200.dp)

        assertEquals(0f, layout.revealStep(0.dp, 200.dp, forward = true).value, 0.01f)
        assertEquals(0f, layout.revealStep(0.dp, 200.dp, forward = false).value, 0.01f)
    }

    @Test
    fun revealStepRespectsResizeOverrides() {
        // A resized column changes where the boundary is, so the step must come
        // from resolved geometry rather than the declared widths.
        val columns = listOf(
            GridColumn<Any>(
                id = "a",
                header = {},
                width = GridColumnWidth.Range(min = 50.dp, max = 300.dp, initial = 100.dp),
                cell = {},
            ),
            GridColumn<Any>(id = "b", header = {}, width = GridColumnWidth.Fixed(100.dp), cell = {}),
        )
        val layout = GridColumnLayoutInfo.resolve(
            columns = columns,
            containerWidth = 150.dp,
            widthOverrides = mapOf("a" to 200.dp),
        )

        // Column "a" is now 0–200, so a 150-wide viewport at 0 still hides 50 of it.
        assertEquals(50f, layout.revealStep(0.dp, 150.dp, forward = true).value, 0.01f)
    }
}
