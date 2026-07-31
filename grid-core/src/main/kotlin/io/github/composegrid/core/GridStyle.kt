package io.github.composegrid.core

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Customization surface for [DataGrid]'s visual styling. Kept design-system
 * agnostic (plain [Color]s, no Material dependency) so grid-core stays
 * usable standalone; `grid-material3`'s `GridDefaults.style()` builds one of
 * these from Material3 theme tokens for consumers who want that instead.
 *
 * @param sortIndicator Rendered inline in a sortable column's header cell.
 *   Called with [SortDirection.None] when the column is sortable but not the
 *   currently active sort column — implementations should render nothing in
 *   that case (see [DefaultSortIndicator]).
 * @param cellPadding Horizontal inset applied to header and body cell
 *   *content*. The cell background, focus ring, and resize handle still span
 *   the column's full width — this only keeps text off the column boundary,
 *   so adjacent columns don't read as one run-on string.
 */
data class GridStyle(
    val headerBackground: Color,
    val rowBackground: Color,
    val selectedRowBackground: Color,
    val dividerColor: Color,
    val focusIndicatorColor: Color,
    val sortIndicator: @Composable (direction: SortDirection) -> Unit,
    val cellPadding: Dp = 8.dp,
) {
    companion object {
        /** Reproduces the grid's pre-M6 hardcoded colors, plus M6's default cell padding. */
        val Default = GridStyle(
            headerBackground = Color.Transparent,
            rowBackground = Color.Transparent,
            selectedRowBackground = Color(0x1F6750A4),
            dividerColor = Color(0x33000000),
            focusIndicatorColor = Color(0x1F6750A4),
            sortIndicator = { direction -> DefaultSortIndicator(direction) },
        )
    }
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
