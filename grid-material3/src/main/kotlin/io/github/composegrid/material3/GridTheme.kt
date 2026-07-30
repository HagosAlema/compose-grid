package io.github.composegrid.material3

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * Default Material3-token-driven colors for `DataGrid`, for consumers who
 * want sensible defaults instead of specifying every color via `grid-core`'s
 * lower-level, design-system-agnostic API.
 *
 * Status: **M6 stub.** Column definitions and cell composables in `grid-core`
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
)

object GridDefaults {
    @Composable
    @ReadOnlyComposable
    fun colors(): GridColors = GridColors(
        headerBackground = MaterialTheme.colorScheme.surfaceVariant,
        rowBackground = MaterialTheme.colorScheme.surface,
        selectedRowBackground = MaterialTheme.colorScheme.primaryContainer,
        dividerColor = MaterialTheme.colorScheme.outlineVariant,
    )
}
