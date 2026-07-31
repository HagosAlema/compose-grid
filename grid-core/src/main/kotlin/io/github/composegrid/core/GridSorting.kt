package io.github.composegrid.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * In-memory sorting helpers.
 *
 * [DataGrid] deliberately never reorders rows itself. It owns the sort
 * *state* — which column, which direction — but the data belongs to the
 * [GridDataSource], and a paged or network-backed source can only be ordered
 * at the source. So the grid reports header clicks (via `onSortChange` and
 * [GridState.sortColumnId]/[GridState.sortDirection]) and leaves the actual
 * ordering to the caller.
 *
 * For the common in-memory case that would mean hand-rolling a `when` over
 * column ids at every call site, so these helpers do it instead, driven by
 * [GridColumn.comparator].
 */

/**
 * Reorders this list to match the sort [state] currently reports, using the
 * sorted column's [GridColumn.comparator].
 *
 * Returns the receiver untouched whenever there's nothing to do: no column is
 * sorted, the direction is [SortDirection.None], the sorted column isn't in
 * [columns] (anymore), or that column declares no comparator — the last case
 * being the server-side-sorting story described above, not an error.
 */
fun <T> List<T>.sortedByGridState(
    columns: List<GridColumn<T>>,
    state: GridState,
): List<T> {
    val sortColumnId = state.sortColumnId ?: return this
    val comparator = columns.firstOrNull { it.id == sortColumnId }?.comparator ?: return this
    return when (state.sortDirection) {
        SortDirection.None -> this
        SortDirection.Ascending -> sortedWith(comparator)
        SortDirection.Descending -> sortedWith(comparator.reversed())
    }
}

/**
 * Remembers a [GridDataSource] over [items], reordered to match [state]'s
 * current sort. This is the one-liner most in-memory consumers want:
 *
 * ```
 * val columns = remember { employeeColumns() }
 * val gridState = rememberGridState()
 * DataGrid(
 *     columns = columns,
 *     dataSource = rememberSortedGridDataSource(employees, columns, gridState),
 *     state = gridState,
 * )
 * ```
 *
 * Re-sorts only when [items] or the sort state actually change, and hands
 * back a stable [GridDataSource] instance in between — worth doing, since
 * `DataGrid` keys its internal item providers on the data source identity.
 */
@Composable
fun <T> rememberSortedGridDataSource(
    items: List<T>,
    columns: List<GridColumn<T>>,
    state: GridState,
): GridDataSource<T> = remember(items, columns, state.sortColumnId, state.sortDirection) {
    items.sortedByGridState(columns, state).asGridDataSource()
}
