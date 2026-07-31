package io.github.composegrid.core

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Customization surface for [DataGrid]'s visual styling. Kept design-system
 * agnostic (plain [Color]s, no Material dependency) so grid-core stays
 * usable standalone; `grid-material3`'s `GridDefaults.style()` builds one of
 * these from Material3 theme tokens for consumers who want that instead.
 *
 * ## Hold this in a stable reference
 * [DataGrid] keys its internal item providers on the style instance, and the
 * composable-slot properties below compare by *reference* — so a `GridStyle`
 * rebuilt inline on every recomposition throws those providers away every
 * time. Use [Default], `GridDefaults.style()` (which remembers internally),
 * or wrap your own in `remember`:
 *
 * ```
 * val style = remember { GridStyle.Default.copy(dividerColor = Color.Gray) }
 * ```
 *
 * @param sortIndicator Rendered inline in a sortable column's header cell.
 *   Called with [SortDirection.None] when the column is sortable but not the
 *   currently active sort column — implementations should render nothing in
 *   that case (see [DefaultSortIndicator]).
 * @param resizeHandle Rendered at the trailing edge of a resizable column's
 *   header ([GridColumnWidth.Range]). Controls *appearance only* — the grid
 *   keeps ownership of the touch target, the drag gesture, and the
 *   system-gesture-exclusion bookkeeping that stops Android's back-swipe from
 *   stealing a drag near the screen edge. See [DefaultResizeHandle].
 * @param cellPadding Horizontal inset applied to header and body cell
 *   *content*. The cell background, focus ring, and resize handle still span
 *   the column's full width — this only keeps text off the column boundary,
 *   so adjacent columns don't read as one run-on string.
 */
@Immutable
data class GridStyle(
    val headerBackground: Color,
    val rowBackground: Color,
    val selectedRowBackground: Color,
    val dividerColor: Color,
    val focusIndicatorColor: Color,
    val sortIndicator: @Composable (direction: SortDirection) -> Unit,
    val resizeHandle: @Composable (state: ResizeHandleState) -> Unit = { handleState ->
        DefaultResizeHandle(handleState, DefaultDividerColor, DefaultAccentColor)
    },
    val cellPadding: Dp = 8.dp,
) {
    companion object {
        /**
         * Design-system-agnostic defaults.
         *
         * Colors match the grid's original hardcoded look, with one
         * deliberate exception: [focusIndicatorColor] is opaque rather than
         * the 12%-alpha tint the selection overlay uses. A focus ring that
         * faint fails to read as an indicator at all, which defeats its
         * purpose for keyboard and switch-access users.
         */
        val Default = GridStyle(
            headerBackground = Color.Transparent,
            rowBackground = Color.Transparent,
            selectedRowBackground = Color(0x1F6750A4),
            dividerColor = DefaultDividerColor,
            focusIndicatorColor = DefaultAccentColor,
            sortIndicator = { direction -> DefaultSortIndicator(direction) },
        )
    }
}

private val DefaultDividerColor = Color(0x33000000)
private val DefaultAccentColor = Color(0xFF6750A4)

/**
 * What a [GridStyle.resizeHandle] can react to.
 *
 * [atMinWidth]/[atMaxWidth] exist so a handle can avoid implying a drag
 * direction that won't do anything — a two-way arrow is misleading on a
 * column already pinned to one end of its [GridColumnWidth.Range].
 */
@Immutable
data class ResizeHandleState(
    val isDragging: Boolean,
    val isHovered: Boolean,
    val atMinWidth: Boolean,
    val atMaxWidth: Boolean,
) {
    /** Whether the user is currently interacting with this handle. */
    val isActive: Boolean get() = isDragging || isHovered
}

/**
 * Plain-text fallback sort indicator — no icon library dependency, so
 * grid-core remains usable without pulling in Material or any other icon
 * set. Renders nothing when [direction] is [SortDirection.None].
 */
@Composable
fun DefaultSortIndicator(direction: SortDirection) {
    val glyph = when (direction) {
        SortDirection.None -> return
        SortDirection.Ascending -> "▲"
        SortDirection.Descending -> "▼"
    }
    BasicText(glyph)
}

/**
 * The default resize affordance: a hairline at rest that thickens and takes
 * [activeColor] while hovered or dragged.
 *
 * Deliberately quiet when idle. A grid can have many resizable columns, and a
 * permanent per-column icon competes with the data for attention — so the
 * resting state is just a boundary line, and the affordance escalates only
 * once the user is actually near it. Pointer users additionally get a
 * horizontal-resize cursor, which `DataGrid` applies to the touch target.
 */
@Composable
fun DefaultResizeHandle(
    state: ResizeHandleState,
    restColor: Color,
    activeColor: Color,
) {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(if (state.isActive) ActiveHandleWidth else RestHandleWidth)
            .background(if (state.isActive) activeColor else restColor),
    )
}

private val RestHandleWidth = 2.dp
private val ActiveHandleWidth = 4.dp
