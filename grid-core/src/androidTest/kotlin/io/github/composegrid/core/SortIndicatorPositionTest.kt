package io.github.composegrid.core

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [SortIndicatorPosition] decides which side of a header label the sort
 * indicator renders on. Asserted by comparing rendered x positions rather than
 * by inspecting the composition, so these fail if the layout regresses even
 * when the right composables are still being called.
 */
@RunWith(AndroidJUnit4::class)
class SortIndicatorPositionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private data class Row(val id: Int, val name: String)

    private val rows = listOf(Row(1, "Alice"))

    /** A marker glyph that's easy to locate, unlike the default ▲/▼. */
    private val indicatorText = "SORTMARK"

    @Composable
    private fun Grid(position: SortIndicatorPosition) {
        val columns = remember {
            listOf(
                GridColumn<Row>(
                    id = "name",
                    header = { BasicText("Name") },
                    width = GridColumnWidth.Fixed(240.dp),
                    sortable = true,
                    cell = { BasicText(it.name) },
                ),
            )
        }
        val style = remember(position) {
            GridStyle.Default.copy(
                // Render unconditionally, including for None, so the marker is
                // present before any click.
                sortIndicator = { BasicText(indicatorText) },
                sortIndicatorPosition = position,
            )
        }
        DataGrid(
            columns = columns,
            dataSource = rows.asGridDataSource(),
            state = rememberGridState(),
            style = style,
            rowKey = { it.id },
        )
    }

    // `useUnmergedTree` matters here: the header cell's `clickable` merges
    // descendant semantics, so a merged-tree lookup for either string returns
    // the same header node and both bounds come back identical.
    private fun labelLeft() =
        composeRule.onNodeWithText("Name", useUnmergedTree = true).getUnclippedBoundsInRoot().left

    private fun labelRight() =
        composeRule.onNodeWithText("Name", useUnmergedTree = true).getUnclippedBoundsInRoot().right

    private fun indicatorLeft() =
        composeRule.onNodeWithText(indicatorText, useUnmergedTree = true).getUnclippedBoundsInRoot().left

    @Test
    fun trailingPositionPutsTheIndicatorAfterTheLabel() {
        composeRule.setContent { Grid(SortIndicatorPosition.Trailing) }

        assertTrue(
            "indicator at ${indicatorLeft()} should be right of label at ${labelLeft()}",
            indicatorLeft() > labelLeft(),
        )
    }

    @Test
    fun leadingPositionPutsTheIndicatorBeforeTheLabel() {
        composeRule.setContent { Grid(SortIndicatorPosition.Leading) }

        assertTrue(
            "indicator at ${indicatorLeft()} should be left of label at ${labelLeft()}",
            indicatorLeft() < labelLeft(),
        )
    }

    @Test
    fun trailingIsTheDefault() {
        composeRule.setContent {
            val columns = remember {
                listOf(
                    GridColumn<Row>(
                        id = "name",
                        header = { BasicText("Name") },
                        width = GridColumnWidth.Fixed(240.dp),
                        sortable = true,
                        cell = { BasicText(it.name) },
                    ),
                )
            }
            val style = remember { GridStyle.Default.copy(sortIndicator = { BasicText(indicatorText) }) }
            DataGrid(
                columns = columns,
                dataSource = rows.asGridDataSource(),
                state = rememberGridState(),
                style = style,
                rowKey = { it.id },
            )
        }

        assertTrue(
            "default should behave as Trailing",
            indicatorLeft() > labelLeft(),
        )
    }

    @Test
    fun indicatorIsAdjacentToTheLabelRatherThanPinnedToTheCellEdge() {
        // Regression guard: the label used to take weight(1f), which pushed a
        // trailing indicator to the far edge of a wide column where it read as
        // part of the column boundary.
        composeRule.setContent { Grid(SortIndicatorPosition.Trailing) }

        val gap = indicatorLeft() - labelRight()

        assertTrue("indicator should sit close to the label, gap was $gap", gap.value in 0f..16f)
    }
}
