package io.github.composegrid.material3

import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import io.github.composegrid.core.GridStyle
import io.github.composegrid.core.SortDirection

/**
 * Default Material3-token-driven colors for `DataGrid`, for consumers who
 * want sensible defaults instead of specifying every color via `grid-core`'s
 * lower-level, design-system-agnostic [GridStyle] API. Convert to one via
 * [toGridStyle], or skip straight to a ready-made [GridStyle] via
 * [GridDefaults.style].
 *
 * Status: **M6.** Column definitions and cell composables in `grid-core`
 * are already fully custom-renderable; this module's job (per
 * `DEVELOPMENT_PLAN.md` M6) is to layer opinionated, overridable Material3
 * defaults on top — header background, selected-row tint, sort-icon color,
 * divider color — so most consumers never need to touch `grid-core` directly.
 */
data class GridColors(
    val headerBackground: Color,
    val rowBackground: Color,
    val selectedRowBackground: Color,
    val dividerColor: Color,
    val focusIndicatorColor: Color,
)

/**
 * Builds a design-system-agnostic [GridStyle] from these Material3 tokens,
 * for handing to [io.github.composegrid.core.DataGrid]'s `style` parameter.
 *
 * @param sortIndicator Rendered in sortable column headers. Defaults to a
 *   Material3-colored/typed triangle glyph tinted with [focusIndicatorColor]
 *   — see [Material3SortIndicator].
 */
fun GridColors.toGridStyle(
    sortIndicator: @Composable (direction: SortDirection) -> Unit = { direction ->
        Material3SortIndicator(direction, color = focusIndicatorColor)
    },
): GridStyle = GridStyle(
    headerBackground = headerBackground,
    rowBackground = rowBackground,
    selectedRowBackground = selectedRowBackground,
    dividerColor = dividerColor,
    focusIndicatorColor = focusIndicatorColor,
    sortIndicator = sortIndicator,
)

object GridDefaults {
    @Composable
    @ReadOnlyComposable
    fun colors(): GridColors = GridColors(
        headerBackground = MaterialTheme.colorScheme.surfaceVariant,
        rowBackground = MaterialTheme.colorScheme.surface,
        selectedRowBackground = MaterialTheme.colorScheme.primaryContainer,
        dividerColor = MaterialTheme.colorScheme.outlineVariant,
        focusIndicatorColor = MaterialTheme.colorScheme.primary,
    )

    /** Ready-made [GridStyle] built from [colors] — the easiest way to Material3-theme a `DataGrid`. */
    @Composable
    fun style(): GridStyle = colors().toGridStyle()
}

/**
 * Material3-styled sort direction glyph: colored via [color] and typed via
 * [MaterialTheme.typography] rather than [GridStyle.Default]'s bare
 * [BasicText] fallback. Renders nothing for [SortDirection.None].
 */
@Composable
fun Material3SortIndicator(direction: SortDirection, color: Color = MaterialTheme.colorScheme.primary) {
    val glyph = when (direction) {
        SortDirection.None -> return
        SortDirection.Ascending -> "▲"
        SortDirection.Descending -> "▼"
    }
    CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.labelSmall.copy(color = color)) {
        BasicText(glyph, style = LocalTextStyle.current)
    }
}
