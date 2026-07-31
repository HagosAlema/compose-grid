package io.github.composegrid.core

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class GridSortingTest {

    private data class Row(val name: String, val score: Int)

    private val rows = listOf(
        Row("Charlie", 20),
        Row("Alice", 30),
        Row("Bob", 10),
    )

    /** `name` sorts locally; `score` is sortable but backend-ordered (no comparator). */
    private val columns = listOf(
        GridColumn<Row>(
            id = "name",
            header = {},
            width = GridColumnWidth.Fixed(100.dp),
            sortable = true,
            comparator = compareBy { it.name },
            cell = {},
        ),
        GridColumn<Row>(
            id = "score",
            header = {},
            width = GridColumnWidth.Fixed(100.dp),
            sortable = true,
            cell = {},
        ),
    )

    private fun stateSortedBy(columnId: String?, direction: SortDirection) =
        GridState(initialSortColumnId = columnId, initialSortDirection = direction)

    @Test
    fun sortsAscendingUsingTheColumnComparator() {
        val sorted = rows.sortedByGridState(columns, stateSortedBy("name", SortDirection.Ascending))

        assertEquals(listOf("Alice", "Bob", "Charlie"), sorted.map { it.name })
    }

    @Test
    fun sortsDescendingUsingTheReversedComparator() {
        val sorted = rows.sortedByGridState(columns, stateSortedBy("name", SortDirection.Descending))

        assertEquals(listOf("Charlie", "Bob", "Alice"), sorted.map { it.name })
    }

    @Test
    fun leavesOrderUntouchedWhenNothingIsSorted() {
        val sorted = rows.sortedByGridState(columns, stateSortedBy(null, SortDirection.None))

        assertSame(rows, sorted)
    }

    @Test
    fun leavesOrderUntouchedWhenDirectionIsNone() {
        // A column id can linger with direction None; that still means "unsorted".
        val sorted = rows.sortedByGridState(columns, stateSortedBy("name", SortDirection.None))

        assertSame(rows, sorted)
    }

    @Test
    fun leavesOrderUntouchedForABackendSortedColumn() {
        // "score" is sortable but declares no comparator — the data source is
        // expected to have ordered the rows already.
        val sorted = rows.sortedByGridState(columns, stateSortedBy("score", SortDirection.Ascending))

        assertSame(rows, sorted)
    }

    @Test
    fun leavesOrderUntouchedWhenTheSortedColumnIsGone() {
        val sorted = rows.sortedByGridState(columns, stateSortedBy("removed", SortDirection.Ascending))

        assertSame(rows, sorted)
    }

    @Test
    fun doesNotMutateTheReceiver() {
        rows.sortedByGridState(columns, stateSortedBy("name", SortDirection.Ascending))

        assertEquals(listOf("Charlie", "Alice", "Bob"), rows.map { it.name })
    }
}
