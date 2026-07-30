package io.github.composegrid.core

import androidx.compose.foundation.gestures.Scrollable2DState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.Remeasurement
import androidx.compose.ui.layout.RemeasurementModifier
import androidx.compose.ui.unit.Dp

/**
 * The (up to) three horizontal regions a [DataGrid] can render: columns
 * pinned to the start, the normally-scrolling middle, and columns pinned to
 * the end. Each is its own `LazyLayout` pair (header + body) sharing this
 * [GridState].
 */
internal enum class ColumnRegion {
    PinnedStart,
    Scrollable,
    PinnedEnd,
}

/**
 * Holds all mutable state for a [DataGrid] instance: scroll position on both
 * axes, per-column widths (for resizable columns), sort state, and row
 * selection.
 *
 * Create via [rememberGridState] in most cases; construct directly if you
 * need to hoist grid state above the composable that hosts the grid (e.g. to
 * drive an external "N rows selected" toolbar).
 */
class GridState(
    initialSortColumnId: String? = null,
    initialSortDirection: SortDirection = SortDirection.None,
) {
    /**
     * Current scroll position in pixels — `x` is the horizontal/column axis,
     * `y` is the vertical/row axis. Read directly by the [DataGrid] measure
     * policy to decide which cells are on-screen; clamped against the actual
     * content size via [updateScrollBounds], called once per measure pass
     * once that size is known.
     */
    var scrollOffset: Offset by mutableStateOf(Offset.Zero)
        private set

    private var maxScrollOffset: Offset = Offset.Zero

    internal fun updateScrollBounds(maxOffset: Offset) {
        maxScrollOffset = maxOffset
        scrollOffset = scrollOffset.coerceIn(maxOffset)
    }

    // A LazyLayout is backed by SubcomposeLayout, which — unlike a plain Layout {} —
    // doesn't automatically remeasure just because a state value it read during
    // measurement changed; Compose's own LazyListState hits the same thing and
    // works around it by capturing a Remeasurement per layout and calling
    // forceRemeasure() explicitly on every scroll delta. We do the same for every
    // header/body region (up to three each, once pinned columns are in play), all
    // of which share this scroll position.
    private val headerRemeasurements = mutableMapOf<ColumnRegion, Remeasurement>()
    private val bodyRemeasurements = mutableMapOf<ColumnRegion, Remeasurement>()

    // Built once (not per-recomposition): RemeasurementModifier has no detach
    // callback, so recreating these on every recomposition would leak stale
    // Remeasurement references into the maps above instead of replacing them.
    internal val headerRemeasurementModifiers: Map<ColumnRegion, RemeasurementModifier> =
        ColumnRegion.entries.associateWith { region ->
            object : RemeasurementModifier {
                override fun onRemeasurementAvailable(remeasurement: Remeasurement) {
                    headerRemeasurements[region] = remeasurement
                }
            }
        }

    internal val bodyRemeasurementModifiers: Map<ColumnRegion, RemeasurementModifier> =
        ColumnRegion.entries.associateWith { region ->
            object : RemeasurementModifier {
                override fun onRemeasurementAvailable(remeasurement: Remeasurement) {
                    bodyRemeasurements[region] = remeasurement
                }
            }
        }

    private fun forceRemeasureAll() {
        headerRemeasurements.values.forEach { it.forceRemeasure() }
        bodyRemeasurements.values.forEach { it.forceRemeasure() }
    }

    /**
     * Backs [androidx.compose.foundation.gestures.scrollable2D] on the grid
     * body. `delta` matches raw drag movement (dragging left/up is negative),
     * and content must move opposite the finger to reveal what's further
     * along the drag direction — so it's subtracted, not added, to
     * [scrollOffset].
     */
    internal val scrollableState: Scrollable2DState = Scrollable2DState { delta ->
        val target = (scrollOffset - delta).coerceIn(maxScrollOffset)
        val consumed = scrollOffset - target
        scrollOffset = target
        forceRemeasureAll()
        consumed
    }

    /** Row keys currently selected. Mutate via [toggleSelection] / [clearSelection]. */
    val selectedRowKeys: androidx.compose.runtime.snapshots.SnapshotStateList<Any> =
        androidx.compose.runtime.snapshots.SnapshotStateList()

    /** Live-resized column widths, keyed by [GridColumn.id]. Only populated for [GridColumnWidth.Range] columns. */
    internal val columnWidthOverrides = mutableStateMapOf<String, Dp>()

    /**
     * Sets a column's live resize override. Goes through this method rather
     * than mutating [columnWidthOverrides] directly so the same
     * force-remeasure requirement documented above [scrollableState] can't be
     * forgotten at a call site — [GridColumnLayoutInfo] reads this map during
     * measurement the same way it reads [scrollOffset].
     */
    internal fun setColumnWidthOverride(columnId: String, width: Dp) {
        columnWidthOverrides[columnId] = width
        forceRemeasureAll()
    }

    /**
     * Bounds (in window coordinates) of every currently-composed resize
     * handle, keyed by column id. [DataGrid] mirrors this into
     * `View.systemGestureExclusionRects` (API 29+) so a resize drag starting
     * near the screen edge isn't stolen by Android's back gesture mid-drag —
     * see `ColumnResizeHandle` and `SystemGestureExclusionEffect`.
     */
    internal val resizeHandleExclusionRects = mutableStateMapOf<String, android.graphics.Rect>()

    /**
     * What we last pushed into the View's exclusion-rect list, so we only
     * ever remove/replace our own contributions and never clobber rects some
     * other part of the host app may have registered on the same View.
     */
    internal var previouslyContributedExclusionRects: List<android.graphics.Rect> = emptyList()

    var sortColumnId: String? by mutableStateOf(initialSortColumnId)
        internal set

    var sortDirection: SortDirection by mutableStateOf(initialSortDirection)
        internal set

    fun toggleSelection(rowKey: Any) {
        if (!selectedRowKeys.remove(rowKey)) {
            selectedRowKeys.add(rowKey)
        }
    }

    fun clearSelection() {
        selectedRowKeys.clear()
    }

    internal fun onHeaderClicked(column: GridColumn<*>) {
        if (!column.sortable) return
        if (sortColumnId != column.id) {
            sortColumnId = column.id
            sortDirection = SortDirection.Ascending
        } else {
            sortDirection = sortDirection.next()
            if (sortDirection == SortDirection.None) sortColumnId = null
        }
    }
}

@Composable
fun rememberGridState(
    initialSortColumnId: String? = null,
    initialSortDirection: SortDirection = SortDirection.None,
): GridState = remember {
    GridState(initialSortColumnId, initialSortDirection)
}

/** Clamps each axis of this [Offset] to `[0, max]` on that axis. */
private fun Offset.coerceIn(max: Offset): Offset = Offset(
    x = x.coerceIn(0f, max.x.coerceAtLeast(0f)),
    y = y.coerceIn(0f, max.y.coerceAtLeast(0f)),
)
