package io.github.composegrid.core

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp

/**
 * Declares a single column of a [DataGrid]. Modeled after `LazyColumn` items:
 * a plain data holder with composable slots, so consumers get full control of
 * rendering without a templating layer in between.
 */
class GridColumn<T>(
    /** Stable identifier for this column; used as a key for resize/sort/pin state. */
    val id: String,
    /** Column header content. */
    val header: @Composable () -> Unit,
    /** Sizing strategy for this column. See [GridColumnWidth]. */
    val width: GridColumnWidth,
    /** Whether clicking the header toggles sort state for this column. */
    val sortable: Boolean = false,
    /** Whether this column is pinned to the start/end and exempt from horizontal scroll. */
    val pinned: ColumnPin = ColumnPin.None,
    /** Cell content for a given row item. */
    val cell: @Composable (item: T) -> Unit,
)

/** Sizing strategy for a [GridColumn]. */
sealed interface GridColumnWidth {
    /** A fixed width in Dp that does not participate in weighted distribution. */
    data class Fixed(val width: Dp) : GridColumnWidth

    /** Distributes remaining space proportionally among all weighted columns. */
    data class Weighted(val weight: Float) : GridColumnWidth

    /** A resizable column constrained between [min] and [max], starting at [initial]. */
    data class Range(val min: Dp, val max: Dp, val initial: Dp) : GridColumnWidth
}

/** Which edge, if any, a column is pinned to. */
enum class ColumnPin {
    None,
    Start,
    End,
}

/** Sort direction for a sortable column. */
enum class SortDirection {
    None,
    Ascending,
    Descending;

    /** Returns the next state in the None -> Ascending -> Descending -> None cycle. */
    fun next(): SortDirection = when (this) {
        None -> Ascending
        Ascending -> Descending
        Descending -> None
    }
}
