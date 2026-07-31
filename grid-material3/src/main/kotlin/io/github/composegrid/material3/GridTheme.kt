package io.github.composegrid.material3

import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import io.github.composegrid.core.DefaultResizeHandle
import io.github.composegrid.core.GridStyle
import io.github.composegrid.core.ResizeHandleState
import io.github.composegrid.core.SortDirection
import io.github.composegrid.core.SortIndicatorPosition

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
 * @param sortIndicatorPosition Which side of the header label the indicator
 *   sits on. Pass [SortIndicatorPosition.Leading] for right-aligned numeric
 *   columns; note the label-shift caveat documented on that enum.
 */
fun GridColors.toGridStyle(
    sortIndicator: @Composable (direction: SortDirection) -> Unit = { direction ->
        Material3SortIndicator(direction, color = focusIndicatorColor)
    },
    resizeHandle: @Composable (state: ResizeHandleState) -> Unit = { handleState ->
        DefaultResizeHandle(handleState, restColor = dividerColor, activeColor = focusIndicatorColor)
    },
    sortIndicatorPosition: SortIndicatorPosition = SortIndicatorPosition.Trailing,
): GridStyle = GridStyle(
    headerBackground = headerBackground,
    rowBackground = rowBackground,
    selectedRowBackground = selectedRowBackground,
    dividerColor = dividerColor,
    focusIndicatorColor = focusIndicatorColor,
    sortIndicator = sortIndicator,
    resizeHandle = resizeHandle,
    sortIndicatorPosition = sortIndicatorPosition,
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

    /**
     * Ready-made [GridStyle] built from [colors] — the easiest way to
     * Material3-theme a `DataGrid`.
     *
     * Remembered against the theme colors, because [GridStyle]'s composable
     * slots compare by reference and `DataGrid` keys its item providers on the
     * style instance: returning a fresh one per recomposition would rebuild
     * every header and body provider each time.
     */
    @Composable
    fun style(): GridStyle {
        val colors = colors()
        return remember(colors) { colors.toGridStyle() }
    }
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

/**
 * Opt-in resize handle that shows left/right chevrons instead of the plain
 * thickening line of [DefaultResizeHandle]:
 *
 * ```
 * val style = GridDefaults.colors().toGridStyle(resizeHandle = { Material3ResizeHandle(it) })
 * ```
 *
 * The chevrons appear only while hovered or dragged, and a chevron is dropped
 * when the column has hit that end of its `Range` — pointing somewhere a drag
 * can't go reads as a broken control. At rest this is the same quiet boundary
 * line, deliberately: one permanent icon per resizable column competes with
 * the data for attention, and a wide grid has a lot of columns.
 *
 * Drawn with [Canvas] rather than a vector asset so `grid-material3` doesn't
 * pull in `material-icons-extended` for two glyphs.
 *
 * Caveat worth knowing before you enable this: on a column that is both
 * sortable and resizable, the chevrons share the header's trailing edge with
 * [Material3SortIndicator] and will overlap it while active. The plain line
 * is narrow enough not to notice; chevrons are not.
 */
@Composable
fun Material3ResizeHandle(
    state: ResizeHandleState,
    restColor: Color = MaterialTheme.colorScheme.outlineVariant,
    activeColor: Color = MaterialTheme.colorScheme.primary,
) {
    if (!state.isActive) {
        Box(modifier = Modifier.fillMaxHeight().width(RestLineWidth).background(restColor))
        return
    }
    Box(
        modifier = Modifier.fillMaxHeight().width(ChevronPairWidth),
        contentAlignment = Alignment.Center,
    ) {
        Box(modifier = Modifier.fillMaxHeight().width(ActiveLineWidth).background(activeColor))
        Canvas(modifier = Modifier.size(ChevronPairWidth, ChevronHeight)) {
            val inset = ChevronInset.toPx()
            val stroke = ChevronStroke.toPx()
            val midY = size.height / 2f
            val reach = ChevronReach.toPx()

            // Left-pointing chevron, suppressed once the column can't shrink.
            if (!state.atMinWidth) {
                drawLine(
                    color = activeColor,
                    start = androidx.compose.ui.geometry.Offset(inset, midY),
                    end = androidx.compose.ui.geometry.Offset(inset + reach, midY - reach),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = activeColor,
                    start = androidx.compose.ui.geometry.Offset(inset, midY),
                    end = androidx.compose.ui.geometry.Offset(inset + reach, midY + reach),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }
            // Right-pointing chevron, suppressed once the column can't grow.
            if (!state.atMaxWidth) {
                val right = size.width - inset
                drawLine(
                    color = activeColor,
                    start = androidx.compose.ui.geometry.Offset(right, midY),
                    end = androidx.compose.ui.geometry.Offset(right - reach, midY - reach),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = activeColor,
                    start = androidx.compose.ui.geometry.Offset(right, midY),
                    end = androidx.compose.ui.geometry.Offset(right - reach, midY + reach),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

private val RestLineWidth = 2.dp
private val ActiveLineWidth = 2.dp
private val ChevronPairWidth = 20.dp
private val ChevronHeight = 12.dp
private val ChevronInset = 1.dp
private val ChevronReach = 4.dp
private val ChevronStroke = 1.5.dp
