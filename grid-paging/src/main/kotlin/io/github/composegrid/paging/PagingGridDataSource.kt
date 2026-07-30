package io.github.composegrid.paging

import androidx.compose.runtime.Composable
import androidx.paging.compose.LazyPagingItems
import io.github.composegrid.core.GridDataSource

/**
 * Adapts a Paging 3 [LazyPagingItems] into a [GridDataSource] for use with
 * `DataGrid`.
 *
 * Status: **M5 stub.** Wires the shape of the adapter per `DEVELOPMENT_PLAN.md`
 * but does not yet forward load-state (loading/error) to the grid — that's
 * scoped for M5 alongside the placeholder-row UX in `grid-core`.
 *
 * Kept in its own artifact so consumers who only need `List<T>` via
 * `grid-core` don't pull in the Paging 3 dependency.
 */
class PagingGridDataSource<T : Any>(
    private val pagingItems: LazyPagingItems<T>,
) : GridDataSource<T> {
    override val itemCount: Int get() = pagingItems.itemCount
    override fun peek(index: Int): T? = pagingItems.peek(index)
}

@Composable
fun <T : Any> LazyPagingItems<T>.asGridDataSource(): GridDataSource<T> =
    PagingGridDataSource(this)
