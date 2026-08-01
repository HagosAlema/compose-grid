package io.github.composegrid.core

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Per-row heights, end to end. [GridRowLayoutInfoTest] covers the arithmetic;
 * these check the measure policy actually sizes and stacks rows from it, which
 * is where an off-by-one surfaces as overlapping rows or a gap of background.
 */
@RunWith(AndroidJUnit4::class)
class DataGridRowHeightTest {

    @get:Rule
    val composeRule = createComposeRule()

    private data class Row(val id: Int, val label: String)

    private val rows = (0 until 12).map { Row(it, "row-$it") }

    /** Alternating 40dp / 80dp, so neighbours always differ. */
    private fun heightAt(index: Int): Dp = if (index % 2 == 0) 40.dp else 80.dp

    @Composable
    private fun Grid(variable: Boolean) {
        val columns = remember {
            listOf(
                GridColumn<Row>(
                    id = "label",
                    header = { BasicText("Label") },
                    width = GridColumnWidth.Fixed(200.dp),
                    cell = { BasicText(it.label) },
                ),
            )
        }
        DataGrid(
            columns = columns,
            dataSource = rows.asGridDataSource(),
            state = rememberGridState(),
            modifier = Modifier.size(200.dp, 400.dp),
            rowHeight = 40.dp,
            rowHeightAt = if (variable) ::heightAt else null,
            rowKey = { it.id },
        )
    }

    private fun boundsOf(text: String) = composeRule.onNodeWithText(text).getUnclippedBoundsInRoot()

    private fun composedRowCount(): Int =
        rows.count { composeRule.onAllNodesWithText(it.label).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun rowsTakeTheirDeclaredHeights() {
        composeRule.setContent { Grid(variable = true) }

        assertEquals(40f, boundsOf("row-0").height.value, 1f)
        assertEquals(80f, boundsOf("row-1").height.value, 1f)
        assertEquals(40f, boundsOf("row-2").height.value, 1f)
    }

    @Test
    fun rowsStackWithoutOverlapOrGap() {
        composeRule.setContent { Grid(variable = true) }

        // Each row must begin exactly where the previous ended. Anything else is
        // either overlapping content or a visible stripe of background.
        for (i in 0 until 4) {
            assertEquals(
                "row-${i + 1} should start where row-$i ends",
                boundsOf("row-$i").bottom.value,
                boundsOf("row-${i + 1}").top.value,
                1f,
            )
        }
    }

    @Test
    fun uniformIsUnaffectedWhenNoHeightFunctionIsGiven() {
        composeRule.setContent { Grid(variable = false) }

        assertEquals(40f, boundsOf("row-0").height.value, 1f)
        assertEquals(40f, boundsOf("row-1").height.value, 1f)
    }

    /**
     * Virtualization has to compose exactly the rows that intersect the
     * viewport — no more, no fewer. Both cases below are the same grid over the
     * same 12 rows, so the difference in count comes entirely from the heights
     * fed into the visible-range calculation.
     *
     * The grid is 400dp tall *including* its 40dp header, so the body viewport
     * is 360dp.
     *
     * Split across two tests because `setContent` may only be called once per
     * test.
     */
    @Test
    fun uniformRowsComposeExactlyThoseThatFit() {
        composeRule.setContent { Grid(variable = false) }

        // 360dp body / 40dp rows = rows 0..8.
        assertEquals(9, composedRowCount())
    }

    @Test
    fun variableRowsComposeExactlyThoseThatFit() {
        composeRule.setContent { Grid(variable = true) }

        // Alternating 40/80 stacks to 40, 120, 160, 240, 280, 360. Row 6 starts
        // exactly at 360 — the viewport edge — and is correctly left out rather
        // than composed where nobody could see it.
        assertEquals(6, composedRowCount())
        assertTrue("taller rows should compose fewer than the uniform 9", composedRowCount() < 9)
    }
}
