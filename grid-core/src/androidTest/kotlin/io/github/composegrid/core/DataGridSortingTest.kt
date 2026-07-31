package io.github.composegrid.core

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end cover for the sorting bug: clicking a sortable header used to
 * move the sort indicator while leaving the rows in their original order,
 * because [DataGrid] tracks sort state but never reorders data itself. These
 * assert on *rendered row order*, so they fail if the wiring regresses even
 * when the indicator still looks right.
 */
@RunWith(AndroidJUnit4::class)
class DataGridSortingTest {

    @get:Rule
    val composeRule = createComposeRule()

    private data class Row(val id: Int, val name: String)

    private val rows = listOf(
        Row(1, "Charlie"),
        Row(2, "Alice"),
        Row(3, "Bob"),
    )

    private fun columns() = listOf(
        GridColumn<Row>(
            id = "name",
            header = { BasicText("Name") },
            width = GridColumnWidth.Fixed(200.dp),
            sortable = true,
            comparator = compareBy { it.name },
            cell = { BasicText(it.name) },
        ),
    )

    @Composable
    private fun SortableGrid() {
        val columns = remember { columns() }
        val state = rememberGridState()
        DataGrid(
            columns = columns,
            dataSource = rememberSortedGridDataSource(rows, columns, state),
            state = state,
            rowKey = { it.id },
        )
    }

    /** Rendered top-to-bottom order, read back out of each cell's row index. */
    private fun renderedNames(): List<String> = rows
        .map { it.name }
        .map { name -> name to rowIndexOf(name) }
        .sortedBy { it.second }
        .map { it.first }

    private fun rowIndexOf(text: String): Int {
        var index = -1
        composeRule.onNodeWithText(text).assert(
            SemanticsMatcher("has a row index") { node ->
                index = node.config.getOrNull(SemanticsProperties.CollectionItemInfo)?.rowIndex ?: -1
                index >= 0
            },
        )
        return index
    }

    @Test
    fun clickingASortableHeaderReordersTheRows() {
        composeRule.setContent { SortableGrid() }

        assertEquals(listOf("Charlie", "Alice", "Bob"), renderedNames())

        composeRule.onNodeWithText("Name").performClick()
        assertEquals(listOf("Alice", "Bob", "Charlie"), renderedNames())

        composeRule.onNodeWithText("Name").performClick()
        assertEquals(listOf("Charlie", "Bob", "Alice"), renderedNames())

        // Third click returns to None, which restores the original order.
        composeRule.onNodeWithText("Name").performClick()
        assertEquals(listOf("Charlie", "Alice", "Bob"), renderedNames())
    }
}
