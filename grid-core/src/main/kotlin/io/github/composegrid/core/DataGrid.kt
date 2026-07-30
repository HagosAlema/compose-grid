@file:OptIn(ExperimentalFoundationApi::class)

package io.github.composegrid.core

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.scrollable2D
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.layout.LazyLayout
import androidx.compose.foundation.lazy.layout.LazyLayoutItemProvider
import androidx.compose.foundation.lazy.layout.LazyLayoutMeasurePolicy
import androidx.compose.foundation.lazy.layout.LazyLayoutPrefetchState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * A virtualized, resizable, sortable data grid for Jetpack Compose.
 *
 * ## Status: M2 milestone
 * Rows and columns virtualize jointly on a custom
 * [androidx.compose.foundation.lazy.layout.LazyLayout] engine: only cells
 * whose row *and* column both intersect the viewport are composed, measured,
 * and placed — see `DEVELOPMENT_PLAN.md` §3.2/M2. The header shares the same
 * [GridColumnLayoutInfo] and horizontal scroll position as a second, single-row
 * `LazyLayout`, so header and body columns always stay pixel-aligned.
 *
 * Known current limitations, tracked for M3+:
 *  - [ColumnPin] is accepted in the API but not yet wired up — no frozen
 *    columns yet.
 *  - Rows share a single uniform [rowHeight]; variable per-row height isn't
 *    part of the v1 feature set.
 *  - Column resizing ([GridColumnWidth.Range]) is resolved by
 *    [GridColumnLayoutInfo] but there's no drag handle yet to change it.
 *
 * @param columns Column definitions, in display order.
 * @param dataSource Row data. Use [asGridDataSource] to wrap a plain `List<T>`.
 * @param state Grid state (scroll, selection, sort, column resize). See [rememberGridState].
 * @param modifier Modifier applied to the outer grid container.
 * @param rowHeight Uniform height applied to every row and to the header.
 * @param onSortChange Invoked when the user changes sort via a header click.
 * @param rowKey Stable key for a row item, used for selection tracking and
 *   cell keys. Defaults to [Any.hashCode], which is a reasonable fallback but
 *   a real key (e.g. a database id) is strongly recommended.
 */
@Composable
fun <T> DataGrid(
    columns: List<GridColumn<T>>,
    dataSource: GridDataSource<T>,
    state: GridState = rememberGridState(),
    modifier: Modifier = Modifier,
    rowHeight: Dp = 48.dp,
    onSortChange: (column: GridColumn<T>, direction: SortDirection) -> Unit = { _, _ -> },
    rowKey: (T) -> Any = { it.hashCode() },
) {
    Column(modifier = modifier.fillMaxSize()) {
        GridHeader(
            columns = columns,
            state = state,
            rowHeight = rowHeight,
            onSortChange = onSortChange,
        )
        GridBody(
            columns = columns,
            dataSource = dataSource,
            state = state,
            rowHeight = rowHeight,
            rowKey = rowKey,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun <T> GridHeader(
    columns: List<GridColumn<T>>,
    state: GridState,
    rowHeight: Dp,
    onSortChange: (GridColumn<T>, SortDirection) -> Unit,
) {
    val itemProvider = remember(columns, state, onSortChange) {
        GridHeaderItemProvider(columns, state, onSortChange)
    }
    LazyLayout(
        itemProvider = { itemProvider },
        modifier = Modifier
            .fillMaxWidth()
            .height(rowHeight)
            .clipToBounds()
            .then(state.headerRemeasurementModifier),
        measurePolicy = headerMeasurePolicy(columns, state, rowHeight),
    )
}

@Composable
private fun <T> GridBody(
    columns: List<GridColumn<T>>,
    dataSource: GridDataSource<T>,
    state: GridState,
    rowHeight: Dp,
    rowKey: (T) -> Any,
    modifier: Modifier = Modifier,
) {
    val itemProvider = remember(columns, dataSource, rowKey, state) {
        GridBodyItemProvider(columns, dataSource, rowKey, state)
    }
    val prefetchState = remember { LazyLayoutPrefetchState() }
    LazyLayout(
        itemProvider = { itemProvider },
        prefetchState = prefetchState,
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds()
            .then(state.bodyRemeasurementModifier)
            .scrollable2D(state.scrollableState),
        measurePolicy = bodyMeasurePolicy(columns, dataSource, state, rowHeight),
    )
}

private class GridHeaderItemProvider<T>(
    private val columns: List<GridColumn<T>>,
    private val state: GridState,
    private val onSortChange: (GridColumn<T>, SortDirection) -> Unit,
) : LazyLayoutItemProvider {
    override val itemCount: Int get() = columns.size
    override fun getKey(index: Int): Any = columns[index].id

    @Composable
    override fun Item(index: Int, key: Any) {
        val column = columns[index]
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(enabled = column.sortable) {
                    state.onHeaderClicked(column)
                    onSortChange(column, state.sortDirection)
                },
        ) {
            column.header()
            val rangeWidth = column.width as? GridColumnWidth.Range
            if (rangeWidth != null) {
                ColumnResizeHandle(columnId = column.id, rangeWidth = rangeWidth, state = state)
            }
        }
    }
}

/**
 * A thin draggable strip pinned to a resizable column's right edge in the
 * header. Positioned via [BoxScope.align] within the header cell's own [Box],
 * so it always sits exactly at the column's *current* resolved width without
 * needing its own copy of [GridColumnLayoutInfo].
 */
@Composable
private fun BoxScope.ColumnResizeHandle(
    columnId: String,
    rangeWidth: GridColumnWidth.Range,
    state: GridState,
) {
    val density = LocalDensity.current
    val dragState = rememberDraggableState { deltaPx ->
        val deltaDp = with(density) { deltaPx.toDp() }
        val current = state.columnWidthOverrides[columnId] ?: rangeWidth.initial
        state.setColumnWidthOverride(
            columnId,
            (current + deltaDp).coerceIn(rangeWidth.min, rangeWidth.max),
        )
    }
    Box(
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .fillMaxHeight()
            .width(ResizeHandleTouchWidth)
            .draggable(orientation = Orientation.Horizontal, state = dragState),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(ResizeHandleVisibleWidth)
                .background(ResizeHandleColor),
        )
    }
}

private val ResizeHandleTouchWidth = 24.dp
private val ResizeHandleVisibleWidth = 2.dp
private val ResizeHandleColor = Color(0x33000000)

private class GridBodyItemProvider<T>(
    private val columns: List<GridColumn<T>>,
    private val dataSource: GridDataSource<T>,
    private val rowKey: (T) -> Any,
    private val state: GridState,
) : LazyLayoutItemProvider {
    private val columnCount get() = columns.size

    override val itemCount: Int
        get() = dataSource.itemCount.coerceAtLeast(0) * columnCount

    override fun getKey(index: Int): Any {
        val row = index / columnCount
        val col = index % columnCount
        val rowPart = dataSource.peek(row)?.let(rowKey) ?: "__placeholder_$row"
        return "$rowPart:${columns[col].id}"
    }

    @Composable
    override fun Item(index: Int, key: Any) {
        val row = index / columnCount
        val col = index % columnCount
        val column = columns[col]
        val item = dataSource.peek(row)

        val cellModifier = if (item != null) {
            val selected = state.selectedRowKeys.contains(rowKey(item))
            Modifier
                .fillMaxSize()
                .background(if (selected) SelectedRowOverlay else Color.Transparent)
                .clickable { state.toggleSelection(rowKey(item)) }
        } else {
            Modifier.fillMaxSize()
        }

        Box(modifier = cellModifier) {
            if (item != null) column.cell(item)
        }
    }
}

/** One measured, positioned cell awaiting placement in a [LazyLayout]'s `layout {}` block. */
private class PlacedCell(val x: Int, val y: Int, val placeable: Placeable)

private fun <T> headerMeasurePolicy(
    columns: List<GridColumn<T>>,
    state: GridState,
    rowHeight: Dp,
): LazyLayoutMeasurePolicy = LazyLayoutMeasurePolicy { constraints ->
    val columnCount = columns.size
    val viewportWidth = constraints.maxWidth.toDp()
    val rowHeightPx = rowHeight.roundToPx()

    val columnLayout = GridColumnLayoutInfo.resolve(
        columns = columns,
        containerWidth = viewportWidth,
        widthOverrides = state.columnWidthOverrides,
    )

    val scrollX = state.scrollOffset.x
    val colRange = if (columnCount == 0) {
        IntRange.EMPTY
    } else {
        columnLayout.visibleColumnRange(scrollX.toDp(), viewportWidth)
    }

    val placedCells = mutableListOf<PlacedCell>()
    for (col in colRange) {
        val x = (columnLayout.offset(col).toPx() - scrollX).roundToInt()
        val cellConstraints = Constraints.fixed(
            width = columnLayout.width(col).roundToPx(),
            height = rowHeightPx,
        )
        compose(col).forEach { measurable ->
            placedCells += PlacedCell(x, 0, measurable.measure(cellConstraints))
        }
    }

    layout(constraints.maxWidth, rowHeightPx) {
        placedCells.forEach { it.placeable.placeRelative(it.x, it.y) }
    }
}

private fun <T> bodyMeasurePolicy(
    columns: List<GridColumn<T>>,
    dataSource: GridDataSource<T>,
    state: GridState,
    rowHeight: Dp,
): LazyLayoutMeasurePolicy = LazyLayoutMeasurePolicy { constraints ->
    val columnCount = columns.size
    val rowCount = dataSource.itemCount.coerceAtLeast(0)
    val viewportWidth = constraints.maxWidth.toDp()
    val viewportWidthPx = constraints.maxWidth.toFloat()
    val viewportHeightPx = constraints.maxHeight.toFloat()
    val rowHeightPx = rowHeight.toPx()
    val rowHeightPxInt = rowHeight.roundToPx()

    val columnLayout = GridColumnLayoutInfo.resolve(
        columns = columns,
        containerWidth = viewportWidth,
        widthOverrides = state.columnWidthOverrides,
    )

    val totalHeightPx = rowCount * rowHeightPx
    state.updateScrollBounds(
        Offset(
            x = (columnLayout.totalWidth.toPx() - viewportWidthPx).coerceAtLeast(0f),
            y = (totalHeightPx - viewportHeightPx).coerceAtLeast(0f),
        ),
    )

    val scrollX = state.scrollOffset.x
    val scrollY = state.scrollOffset.y

    val colRange = if (columnCount == 0) {
        IntRange.EMPTY
    } else {
        columnLayout.visibleColumnRange(scrollX.toDp(), viewportWidth)
    }
    val rowRange = visibleRowRange(scrollY, viewportHeightPx, rowHeightPx, rowCount)

    val placedCells = mutableListOf<PlacedCell>()
    for (row in rowRange) {
        val y = (row * rowHeightPx - scrollY).roundToInt()
        for (col in colRange) {
            val flatIndex = row * columnCount + col
            val x = (columnLayout.offset(col).toPx() - scrollX).roundToInt()
            val cellConstraints = Constraints.fixed(
                width = columnLayout.width(col).roundToPx(),
                height = rowHeightPxInt,
            )
            compose(flatIndex).forEach { measurable ->
                placedCells += PlacedCell(x, y, measurable.measure(cellConstraints))
            }
        }
    }

    layout(constraints.maxWidth, constraints.maxHeight) {
        placedCells.forEach { it.placeable.placeRelative(it.x, it.y) }
    }
}

/**
 * Visible row indices for uniform-height rows: the half-open-interval
 * counterpart of [GridColumnLayoutInfo.visibleColumnRange], specialized to
 * O(1) math since every row is the same height.
 */
private fun visibleRowRange(
    scrollY: Float,
    viewportHeight: Float,
    rowHeightPx: Float,
    rowCount: Int,
): IntRange {
    if (rowCount == 0 || rowHeightPx <= 0f || viewportHeight <= 0f) return IntRange.EMPTY
    val viewEnd = scrollY + viewportHeight
    val first = floor(scrollY / rowHeightPx).toInt().coerceIn(0, rowCount - 1)
    val last = (ceil(viewEnd / rowHeightPx).toInt() - 1).coerceIn(0, rowCount - 1)
    return if (first > last) IntRange.EMPTY else first..last
}

private val SelectedRowOverlay = Color(0x1F6750A4)
