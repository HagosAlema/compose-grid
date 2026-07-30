package io.github.composegrid.core

/**
 * Abstracts over how row data is supplied to [DataGrid].
 *
 * This exists so the rendering/virtualization core never needs to know whether
 * data is already in memory or must be fetched (e.g. from a paging source or
 * network). It only ever asks "what's at this index," and "is it loaded yet."
 *
 * Most consumers won't implement this directly — use [ListGridDataSource] for a
 * plain `List<T>`, or the `PagingGridDataSource` adapter in the `grid-paging`
 * artifact for `LazyPagingItems`-backed data.
 */
interface GridDataSource<T> {
    /**
     * Total number of rows, if known. Use [UNKNOWN_COUNT] for open-ended /
     * streaming sources where the total isn't known up front.
     */
    val itemCount: Int

    /**
     * Returns the item at [index] if it's already loaded/available, or `null`
     * if it hasn't been loaded yet (in which case the grid will show a
     * placeholder row and the source is expected to trigger loading as a
     * side effect of this call, if applicable).
     */
    fun peek(index: Int): T?

    companion object {
        const val UNKNOWN_COUNT = -1
    }
}

/**
 * Simple [GridDataSource] wrapping an in-memory [List]. This is the default
 * choice for most consumers — data is already loaded, nothing to fetch.
 */
class ListGridDataSource<T>(private val items: List<T>) : GridDataSource<T> {
    override val itemCount: Int get() = items.size
    override fun peek(index: Int): T? = items.getOrNull(index)
}

/** Convenience extension for wrapping a [List] as a [GridDataSource]. */
fun <T> List<T>.asGridDataSource(): GridDataSource<T> = ListGridDataSource(this)
