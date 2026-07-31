@file:OptIn(ExperimentalFoundationApi::class)

package io.github.composegrid.core

import android.os.Build
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.scrollable2D
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.layout.LazyLayout
import androidx.compose.foundation.lazy.layout.LazyLayoutItemProvider
import androidx.compose.foundation.lazy.layout.LazyLayoutMeasurePolicy
import androidx.compose.foundation.lazy.layout.LazyLayoutPrefetchState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.ScrollAxisRange
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.horizontalScrollAxisRange
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.scrollBy
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.verticalScrollAxisRange
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * A virtualized, resizable, sortable data grid for Jetpack Compose.
 *
 * ## Status: M6 milestone
 * Rows and columns virtualize jointly on a custom
 * [androidx.compose.foundation.lazy.layout.LazyLayout] engine: only cells
 * whose row *and* column both intersect the viewport are composed, measured,
 * and placed — see `DEVELOPMENT_PLAN.md` §3.2/M2. The header shares the same
 * [GridColumnLayoutInfo] and horizontal scroll position as a second, single-row
 * `LazyLayout`, so header and body columns always stay pixel-aligned.
 *
 * Accessibility: header and body cells carry [Role.Button]/`selected`/
 * `stateDescription`/[CollectionInfo]/[CollectionItemInfo] semantics for
 * TalkBack, and every cell is a focus target with a [style]-colored focus
 * ring plus arrow-key navigation (via [FocusManager.moveFocus], so it moves
 * between *currently-composed* cells — an off-screen row/column only becomes
 * reachable once scrolled into view; keyboard-driven scroll-into-view isn't
 * implemented yet).
 *
 * Known current limitations, tracked for M7+:
 *  - Rows share a single uniform [rowHeight]; variable per-row height isn't
 *    part of the v1 feature set.
 *  - Arrow-key navigation doesn't scroll off-screen cells into view (see
 *    above).
 *
 * @param columns Column definitions, in display order.
 * @param dataSource Row data. Use [asGridDataSource] to wrap a plain `List<T>`.
 * @param state Grid state (scroll, selection, sort, column resize). See [rememberGridState].
 * @param modifier Modifier applied to the outer grid container.
 * @param style Visual styling (colors, sort indicator). Defaults to
 *   [GridStyle.Default]; `grid-material3`'s `GridDefaults.style()` builds one
 *   from Material3 theme tokens instead.
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
    style: GridStyle = GridStyle.Default,
    rowHeight: Dp = 48.dp,
    onSortChange: (column: GridColumn<T>, direction: SortDirection) -> Unit = { _, _ -> },
    rowKey: (T) -> Any = { it.hashCode() },
    placeholderCell: @Composable (loadState: GridLoadState) -> Unit = {},
) {
    val pinnedStartColumns = columns.filter { it.pinned == ColumnPin.Start }
    val scrollableColumns = columns.filter { it.pinned == ColumnPin.None }
    val pinnedEndColumns = columns.filter { it.pinned == ColumnPin.End }
    val hasResizableColumn = columns.any { it.width is GridColumnWidth.Range }
    val globalColumnIndex = columns.withIndex().associate { (i, c) -> c.id to i }
    val totalColumnCount = columns.size

    SystemGestureExclusionEffect(state)

    // Small defense-in-depth for API <29 (or any gap the exclusion-rect effect
    // above doesn't cover): keep the trailing edge's resize handle a few dp off
    // the physical screen edge, rather than exactly flush with it. Only
    // reserved when a resize handle could actually land there.
    val trailingGutter = if (hasResizableColumn) Modifier.padding(end = ResizeGutterWidth) else Modifier
    val focusManager = LocalFocusManager.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .then(trailingGutter)
            .gridArrowKeyNavigation(focusManager),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            if (pinnedStartColumns.isNotEmpty()) {
                GridPinnedHeader(
                    columns = pinnedStartColumns,
                    state = state,
                    rowHeight = rowHeight,
                    style = style,
                    globalColumnIndex = globalColumnIndex,
                    onSortChange = onSortChange,
                    region = ColumnRegion.PinnedStart,
                )
                RegionDivider(color = style.dividerColor, modifier = Modifier.height(rowHeight))
            }
            GridHeader(
                columns = scrollableColumns,
                state = state,
                rowHeight = rowHeight,
                style = style,
                globalColumnIndex = globalColumnIndex,
                onSortChange = onSortChange,
                modifier = Modifier.weight(1f),
            )
            if (pinnedEndColumns.isNotEmpty()) {
                RegionDivider(color = style.dividerColor, modifier = Modifier.height(rowHeight))
                GridPinnedHeader(
                    columns = pinnedEndColumns,
                    state = state,
                    rowHeight = rowHeight,
                    style = style,
                    globalColumnIndex = globalColumnIndex,
                    onSortChange = onSortChange,
                    region = ColumnRegion.PinnedEnd,
                )
            }
        }
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (pinnedStartColumns.isNotEmpty()) {
                GridPinnedBody(
                    columns = pinnedStartColumns,
                    dataSource = dataSource,
                    state = state,
                    rowHeight = rowHeight,
                    style = style,
                    globalColumnIndex = globalColumnIndex,
                    totalColumnCount = totalColumnCount,
                    rowKey = rowKey,
                    region = ColumnRegion.PinnedStart,
                    placeholderCell = placeholderCell,
                )
                RegionDivider(color = style.dividerColor, modifier = Modifier.fillMaxHeight())
            }
            GridBody(
                columns = scrollableColumns,
                dataSource = dataSource,
                state = state,
                rowHeight = rowHeight,
                style = style,
                globalColumnIndex = globalColumnIndex,
                totalColumnCount = totalColumnCount,
                rowKey = rowKey,
                placeholderCell = placeholderCell,
                modifier = Modifier.weight(1f),
            )
            if (pinnedEndColumns.isNotEmpty()) {
                RegionDivider(color = style.dividerColor, modifier = Modifier.fillMaxHeight())
                GridPinnedBody(
                    columns = pinnedEndColumns,
                    dataSource = dataSource,
                    state = state,
                    rowHeight = rowHeight,
                    style = style,
                    globalColumnIndex = globalColumnIndex,
                    totalColumnCount = totalColumnCount,
                    rowKey = rowKey,
                    region = ColumnRegion.PinnedEnd,
                    placeholderCell = placeholderCell,
                )
            }
        }
    }
}

/**
 * Maps arrow keys to [FocusManager.moveFocus] spatial focus search among
 * currently-composed, focusable descendants (every header/body cell — see
 * [GridHeaderItemProvider.Item] / [GridBodyItemProvider.Item]). Applied once
 * at the grid root rather than per-cell: a cell never consumes arrow-key
 * events itself, so they bubble up to this handler regardless of which
 * region (pinned-start/scrollable/pinned-end) currently holds focus.
 */
private fun Modifier.gridArrowKeyNavigation(focusManager: FocusManager): Modifier = onKeyEvent { keyEvent ->
    if (keyEvent.type != KeyEventType.KeyDown) return@onKeyEvent false
    val direction = when (keyEvent.key) {
        Key.DirectionUp -> FocusDirection.Up
        Key.DirectionDown -> FocusDirection.Down
        Key.DirectionLeft -> FocusDirection.Left
        Key.DirectionRight -> FocusDirection.Right
        else -> return@onKeyEvent false
    }
    focusManager.moveFocus(direction)
}

@Composable
private fun <T> GridHeader(
    columns: List<GridColumn<T>>,
    state: GridState,
    rowHeight: Dp,
    style: GridStyle,
    globalColumnIndex: Map<String, Int>,
    onSortChange: (GridColumn<T>, SortDirection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val itemProvider = remember(columns, state, style, globalColumnIndex, onSortChange) {
        GridHeaderItemProvider(columns, state, style, globalColumnIndex, onSortChange)
    }
    LazyLayout(
        itemProvider = { itemProvider },
        modifier = modifier
            .fillMaxWidth()
            .height(rowHeight)
            .clipToBounds()
            .then(state.headerRemeasurementModifiers.getValue(ColumnRegion.Scrollable)),
        measurePolicy = headerMeasurePolicy(columns, state, rowHeight),
    )
}

@Composable
private fun <T> GridBody(
    columns: List<GridColumn<T>>,
    dataSource: GridDataSource<T>,
    state: GridState,
    rowHeight: Dp,
    style: GridStyle,
    globalColumnIndex: Map<String, Int>,
    totalColumnCount: Int,
    rowKey: (T) -> Any,
    placeholderCell: @Composable (loadState: GridLoadState) -> Unit,
    modifier: Modifier = Modifier,
) {
    val itemProvider = remember(columns, dataSource, rowKey, state, style, globalColumnIndex, placeholderCell) {
        GridBodyItemProvider(columns, dataSource, rowKey, state, style, globalColumnIndex, placeholderCell)
    }
    val prefetchState = remember { LazyLayoutPrefetchState() }
    LazyLayout(
        itemProvider = { itemProvider },
        prefetchState = prefetchState,
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds()
            .then(state.bodyRemeasurementModifiers.getValue(ColumnRegion.Scrollable))
            .scrollable2D(state.scrollableState)
            .semantics {
                collectionInfo = CollectionInfo(dataSource.itemCount, totalColumnCount)
                // Without these the grid is touch-scrollable but invisible as
                // a scroll container to accessibility services: TalkBack can't
                // page through it and can't reach rows that are off-screen.
                // Declared only on the scrollable region — the pinned regions
                // share this scroll state, so scrolling here moves them too,
                // and advertising three separate scrollables would just be
                // noise to traverse.
                verticalScrollAxisRange = ScrollAxisRange(
                    value = { state.scrollOffset.y },
                    maxValue = { state.maxScroll.y },
                )
                horizontalScrollAxisRange = ScrollAxisRange(
                    value = { state.scrollOffset.x },
                    maxValue = { state.maxScroll.x },
                )
                scrollBy { x, y -> state.scrollBy(Offset(x, y)) != Offset.Zero }
            },
        measurePolicy = bodyMeasurePolicy(columns, dataSource, state, rowHeight),
    )
}

/**
 * A pinned (frozen) header region: always shows every column in [columns] —
 * no horizontal windowing, since pinned regions never scroll — sized to its
 * own natural content width via [pinnedHeaderMeasurePolicy] rather than
 * filling incoming constraints, so the surrounding `Row` gives the
 * [ColumnRegion.Scrollable] region everything else.
 */
@Composable
private fun <T> GridPinnedHeader(
    columns: List<GridColumn<T>>,
    state: GridState,
    rowHeight: Dp,
    style: GridStyle,
    globalColumnIndex: Map<String, Int>,
    onSortChange: (GridColumn<T>, SortDirection) -> Unit,
    region: ColumnRegion,
) {
    val itemProvider = remember(columns, state, style, globalColumnIndex, onSortChange) {
        GridHeaderItemProvider(columns, state, style, globalColumnIndex, onSortChange)
    }
    LazyLayout(
        itemProvider = { itemProvider },
        modifier = Modifier
            .height(rowHeight)
            .clipToBounds()
            .then(state.headerRemeasurementModifiers.getValue(region)),
        measurePolicy = pinnedHeaderMeasurePolicy(columns, state, rowHeight),
    )
}

/**
 * A pinned (frozen) body region: always shows every column in [columns],
 * vertically virtualized like [GridBody] but never horizontally windowed.
 * Also gets [scrollable2D] wired to the same shared [GridState.scrollableState]
 * as the scrollable region, so a drag gesture starting over a frozen column
 * still scrolls the grid instead of doing nothing.
 */
@Composable
private fun <T> GridPinnedBody(
    columns: List<GridColumn<T>>,
    dataSource: GridDataSource<T>,
    state: GridState,
    rowHeight: Dp,
    style: GridStyle,
    globalColumnIndex: Map<String, Int>,
    totalColumnCount: Int,
    rowKey: (T) -> Any,
    region: ColumnRegion,
    placeholderCell: @Composable (loadState: GridLoadState) -> Unit,
) {
    val itemProvider = remember(columns, dataSource, rowKey, state, style, globalColumnIndex, placeholderCell) {
        GridBodyItemProvider(columns, dataSource, rowKey, state, style, globalColumnIndex, placeholderCell)
    }
    val prefetchState = remember { LazyLayoutPrefetchState() }
    LazyLayout(
        itemProvider = { itemProvider },
        prefetchState = prefetchState,
        modifier = Modifier
            .clipToBounds()
            .then(state.bodyRemeasurementModifiers.getValue(region))
            .scrollable2D(state.scrollableState)
            .semantics { collectionInfo = CollectionInfo(dataSource.itemCount, totalColumnCount) },
        measurePolicy = pinnedBodyMeasurePolicy(columns, dataSource, state, rowHeight),
    )
}

private class GridHeaderItemProvider<T>(
    private val columns: List<GridColumn<T>>,
    private val state: GridState,
    private val style: GridStyle,
    private val globalColumnIndex: Map<String, Int>,
    private val onSortChange: (GridColumn<T>, SortDirection) -> Unit,
) : LazyLayoutItemProvider {
    override val itemCount: Int get() = columns.size
    override fun getKey(index: Int): Any = columns[index].id

    @Composable
    override fun Item(index: Int, key: Any) {
        val column = columns[index]
        val interactionSource = remember { MutableInteractionSource() }
        val isFocused by interactionSource.collectIsFocusedAsState()
        val columnSortDirection = if (state.sortColumnId == column.id) state.sortDirection else SortDirection.None
        val rangeWidth = column.width as? GridColumnWidth.Range

        // Dragging is the only way to resize with a pointer, which leaves
        // TalkBack and switch-access users with no way at all — so expose the
        // same operation as discrete actions on the header itself. They live
        // here rather than on the handle because `clickable` above merges
        // descendant semantics, which would fold a separate handle node into
        // this one anyway.
        val resizeActions = rangeWidth?.let {
            listOf(
                CustomAccessibilityAction("Increase column width") {
                    state.resizeColumn(column.id, it, by = ResizeAccessibilityStep)
                    true
                },
                CustomAccessibilityAction("Decrease column width") {
                    state.resizeColumn(column.id, it, by = -ResizeAccessibilityStep)
                    true
                },
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(style.headerBackground)
                .then(
                    if (isFocused) Modifier.border(FocusRingWidth, style.focusIndicatorColor) else Modifier,
                )
                .clickable(
                    enabled = column.sortable,
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                ) {
                    state.onHeaderClicked(column)
                    onSortChange(column, state.sortDirection)
                }
                .semantics {
                    collectionItemInfo = CollectionItemInfo(
                        rowIndex = 0,
                        rowSpan = 1,
                        columnIndex = globalColumnIndex.getValue(column.id),
                        columnSpan = 1,
                    )
                    if (column.sortable) {
                        role = Role.Button
                        stateDescription = when (columnSortDirection) {
                            SortDirection.None -> "Not sorted"
                            SortDirection.Ascending -> "Sorted ascending"
                            SortDirection.Descending -> "Sorted descending"
                        }
                    }
                    if (resizeActions != null) customActions = resizeActions
                },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = style.cellPadding,
                        // Keep header content clear of the resize handle's
                        // footprint, so a long label — or the sort indicator —
                        // can never slide underneath it.
                        end = if (rangeWidth != null) ResizeHandleTouchWidth else style.cellPadding,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val showLeadingIndicator = column.sortable &&
                    style.sortIndicatorPosition == SortIndicatorPosition.Leading
                val showTrailingIndicator = column.sortable &&
                    style.sortIndicatorPosition == SortIndicatorPosition.Trailing

                if (showLeadingIndicator) {
                    style.sortIndicator(columnSortDirection)
                    Spacer(modifier = Modifier.width(SortIndicatorGap))
                }
                // `fill = false` keeps the label sized to its content, so a
                // trailing indicator sits right after the text instead of being
                // pushed to the far edge of a wide column, where it reads as
                // belonging to the column boundary rather than to this header.
                // The weight still caps it, so a long label ellipsizes rather
                // than shoving the indicator out of the cell.
                Box(modifier = Modifier.weight(1f, fill = false)) { column.header() }
                if (showTrailingIndicator) {
                    Spacer(modifier = Modifier.width(SortIndicatorGap))
                    style.sortIndicator(columnSortDirection)
                }
            }
            if (rangeWidth != null) {
                ColumnResizeHandle(
                    columnId = column.id,
                    rangeWidth = rangeWidth,
                    state = state,
                    style = style,
                )
            }
        }
    }
}

/**
 * The draggable strip pinned to a resizable column's trailing edge in the
 * header. Positioned via [BoxScope.align] within the header cell's own [Box],
 * so it always sits exactly at the column's *current* resolved width without
 * needing its own copy of [GridColumnLayoutInfo].
 *
 * Owns everything behavioural — touch target, drag gesture, hover tracking,
 * pointer cursor, and the system-gesture-exclusion bookkeeping — and delegates
 * only the visuals to [GridStyle.resizeHandle], so a custom handle can't
 * accidentally shrink the touch target or break back-swipe protection.
 */
@Composable
private fun BoxScope.ColumnResizeHandle(
    columnId: String,
    rangeWidth: GridColumnWidth.Range,
    state: GridState,
    style: GridStyle,
) {
    val density = LocalDensity.current
    val interactionSource = remember { MutableInteractionSource() }
    val isDragging by interactionSource.collectIsDraggedAsState()
    val isHovered by interactionSource.collectIsHoveredAsState()
    val currentWidth = state.columnWidthOverrides[columnId] ?: rangeWidth.initial

    val dragState = rememberDraggableState { deltaPx ->
        val deltaDp = with(density) { deltaPx.toDp() }
        state.resizeColumn(columnId, rangeWidth, by = deltaDp)
    }
    DisposableEffect(columnId) {
        onDispose { state.resizeHandleExclusionRects.remove(columnId) }
    }
    Box(
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .fillMaxHeight()
            .width(ResizeHandleTouchWidth)
            .onGloballyPositioned { coordinates ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val bounds = coordinates.boundsInWindow()
                    state.resizeHandleExclusionRects[columnId] = android.graphics.Rect(
                        bounds.left.roundToInt(),
                        bounds.top.roundToInt(),
                        bounds.right.roundToInt(),
                        bounds.bottom.roundToInt(),
                    )
                }
            }
            .hoverable(interactionSource)
            .pointerHoverIcon(HorizontalResizeCursor)
            .draggable(
                orientation = Orientation.Horizontal,
                state = dragState,
                interactionSource = interactionSource,
            ),
        contentAlignment = Alignment.Center,
    ) {
        style.resizeHandle(
            ResizeHandleState(
                isDragging = isDragging,
                isHovered = isHovered,
                atMinWidth = currentWidth <= rangeWidth.min,
                atMaxWidth = currentWidth >= rangeWidth.max,
            ),
        )
    }
}

/**
 * The platform's horizontal-resize cursor, for mouse/trackpad users on
 * ChromeOS, tablets, and desktop-mode devices. [PointerIcon.Companion] only
 * offers Default/Crosshair/Text/Hand, so this goes through the Android
 * pointer-type bridge — no extra dependency, and `android.view.PointerIcon`
 * has existed since API 24, which is our `minSdk`.
 */
private val HorizontalResizeCursor: PointerIcon =
    PointerIcon(android.view.PointerIcon.TYPE_HORIZONTAL_DOUBLE_ARROW)

/**
 * Mirrors [GridState.resizeHandleExclusionRects] into
 * `View.systemGestureExclusionRects` (API 29+ only — gesture-navigation
 * back-swipe isn't a concept below that, so there's nothing to exclude),
 * so a resize drag starting within the edge back-gesture zone isn't stolen
 * by the OS mid-drag. Only ever removes/replaces rects *we* previously
 * contributed, so it won't clobber whatever else the host app may have
 * registered on the same View.
 */
@Composable
private fun SystemGestureExclusionEffect(state: GridState) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
    val view = LocalView.current
    val ourRects = state.resizeHandleExclusionRects.values.toList()
    SideEffect {
        val foreign = view.systemGestureExclusionRects.filterNot { it in state.previouslyContributedExclusionRects }
        view.systemGestureExclusionRects = foreign + ourRects
        state.previouslyContributedExclusionRects = ourRects
    }
}

private val ResizeHandleTouchWidth = 24.dp
private val ResizeGutterWidth = 8.dp
private val FocusRingWidth = 2.dp

/** How much one "Increase/Decrease column width" accessibility action moves the edge. */
private val ResizeAccessibilityStep = 24.dp

/** Breathing room between a header label and its sort indicator. */
private val SortIndicatorGap = 4.dp

/**
 * A thin vertical line marking the boundary between a pinned region and the
 * scrollable one. Takes its height via [modifier] rather than always calling
 * `fillMaxHeight()` itself: the header `Row` has unbounded incoming height
 * (it just wraps its content, unlike the `weight(1f)` body `Row`), and
 * `fillMaxHeight()` against unbounded constraints there inflated the header
 * row's measured height enough to starve the weighted body row down to zero
 * — callers in a bounded-height context (the body row) pass
 * `Modifier.fillMaxHeight()`; callers in an unbounded one (the header row)
 * pass an explicit `Modifier.height(rowHeight)` instead.
 */
@Composable
private fun RegionDivider(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .width(RegionDividerWidth)
            .background(color),
    )
}

private val RegionDividerWidth = 1.dp

private class GridBodyItemProvider<T>(
    private val columns: List<GridColumn<T>>,
    private val dataSource: GridDataSource<T>,
    private val rowKey: (T) -> Any,
    private val state: GridState,
    private val style: GridStyle,
    private val globalColumnIndex: Map<String, Int>,
    private val placeholderCell: @Composable (loadState: GridLoadState) -> Unit,
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

        if (item == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(style.rowBackground)
                    .padding(horizontal = style.cellPadding),
                contentAlignment = Alignment.CenterStart,
            ) {
                placeholderCell(dataSource.loadState)
            }
            return
        }

        val itemSelected = state.selectedRowKeys.contains(rowKey(item))
        val interactionSource = remember { MutableInteractionSource() }
        val isFocused by interactionSource.collectIsFocusedAsState()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (itemSelected) style.selectedRowBackground else style.rowBackground)
                .then(
                    if (isFocused) Modifier.border(FocusRingWidth, style.focusIndicatorColor) else Modifier,
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                ) {
                    state.toggleSelection(rowKey(item))
                }
                .semantics {
                    selected = itemSelected
                    collectionItemInfo = CollectionItemInfo(
                        rowIndex = row,
                        rowSpan = 1,
                        columnIndex = globalColumnIndex.getValue(column.id),
                        columnSpan = 1,
                    )
                }
                .padding(horizontal = style.cellPadding),
            contentAlignment = Alignment.CenterStart,
        ) {
            column.cell(item)
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
 * Like [headerMeasurePolicy] but for a pinned (frozen) region: every column in
 * [columns] is always placed — a pinned region's column count is small and
 * bounded and never scrolls, so there's no `visibleColumnRange` windowing —
 * and `layout()` reports the region's own natural [GridColumnLayoutInfo.totalWidth]
 * instead of filling incoming constraints, so the surrounding `Row` sizes this
 * region to its content and gives the rest to the scrollable region.
 */
private fun <T> pinnedHeaderMeasurePolicy(
    columns: List<GridColumn<T>>,
    state: GridState,
    rowHeight: Dp,
): LazyLayoutMeasurePolicy = LazyLayoutMeasurePolicy { constraints ->
    val rowHeightPx = rowHeight.roundToPx()

    val columnLayout = GridColumnLayoutInfo.resolve(
        columns = columns,
        containerWidth = constraints.maxWidth.toDp(),
        widthOverrides = state.columnWidthOverrides,
    )

    val placedCells = mutableListOf<PlacedCell>()
    for (col in columns.indices) {
        val x = columnLayout.offset(col).roundToPx()
        val cellConstraints = Constraints.fixed(
            width = columnLayout.width(col).roundToPx(),
            height = rowHeightPx,
        )
        compose(col).forEach { measurable ->
            placedCells += PlacedCell(x, 0, measurable.measure(cellConstraints))
        }
    }

    layout(columnLayout.totalWidth.roundToPx(), rowHeightPx) {
        placedCells.forEach { it.placeable.placeRelative(it.x, it.y) }
    }
}

/** Like [bodyMeasurePolicy] but for a pinned (frozen) region — see [pinnedHeaderMeasurePolicy]. */
private fun <T> pinnedBodyMeasurePolicy(
    columns: List<GridColumn<T>>,
    dataSource: GridDataSource<T>,
    state: GridState,
    rowHeight: Dp,
): LazyLayoutMeasurePolicy = LazyLayoutMeasurePolicy { constraints ->
    val columnCount = columns.size
    val rowCount = dataSource.itemCount.coerceAtLeast(0)
    val viewportHeightPx = constraints.maxHeight.toFloat()
    val rowHeightPx = rowHeight.toPx()
    val rowHeightPxInt = rowHeight.roundToPx()

    val columnLayout = GridColumnLayoutInfo.resolve(
        columns = columns,
        containerWidth = constraints.maxWidth.toDp(),
        widthOverrides = state.columnWidthOverrides,
    )

    val scrollY = state.scrollOffset.y
    val rowRange = visibleRowRange(scrollY, viewportHeightPx, rowHeightPx, rowCount)

    val placedCells = mutableListOf<PlacedCell>()
    for (row in rowRange) {
        val y = (row * rowHeightPx - scrollY).roundToInt()
        for (col in columns.indices) {
            val flatIndex = row * columnCount + col
            val x = columnLayout.offset(col).roundToPx()
            val cellConstraints = Constraints.fixed(
                width = columnLayout.width(col).roundToPx(),
                height = rowHeightPxInt,
            )
            compose(flatIndex).forEach { measurable ->
                placedCells += PlacedCell(x, y, measurable.measure(cellConstraints))
            }
        }
    }

    layout(columnLayout.totalWidth.roundToPx(), constraints.maxHeight) {
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
