package io.github.composegrid.paging

import androidx.compose.runtime.Composable
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import io.github.composegrid.core.GridDataSource
import io.github.composegrid.core.GridLoadState

/**
 * Adapts a Paging 3 [LazyPagingItems] into a [GridDataSource] for use with
 * `DataGrid`.
 *
 * Kept in its own artifact so consumers who only need `List<T>` via
 * `grid-core` don't pull in the Paging 3 dependency.
 */
class PagingGridDataSource<T : Any>(
    private val pagingItems: LazyPagingItems<T>,
) : GridDataSource<T> {
    override val itemCount: Int get() = pagingItems.itemCount

    /**
     * Uses the `get` operator, not [LazyPagingItems.peek] — despite the name
     * similarity to [GridDataSource.peek], Paging's own `peek` is explicitly
     * documented as side-effect-free (it will never trigger a page load);
     * `get`/`operator fun get` is the one that does, which is exactly the
     * side effect [GridDataSource.peek]'s contract promises.
     */
    override fun peek(index: Int): T? = pagingItems[index]

    /**
     * Folds `refresh`/`prepend`/`append` into one [GridLoadState] — the grid
     * doesn't need to know which direction triggered it, just whether *some*
     * load is in flight or has failed.
     */
    override val loadState: GridLoadState
        get() {
            val combined = pagingItems.loadState
            val states = listOf(combined.refresh, combined.prepend, combined.append)
            val error = states.filterIsInstance<LoadState.Error>().firstOrNull()
            return when {
                error != null -> GridLoadState.Error(error.error)
                states.any { it is LoadState.Loading } -> GridLoadState.Loading
                else -> GridLoadState.Idle
            }
        }
}

@Composable
fun <T : Any> LazyPagingItems<T>.asGridDataSource(): GridDataSource<T> =
    PagingGridDataSource(this)
