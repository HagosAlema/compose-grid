package io.github.composegrid.core

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Resizing is drag-only with a pointer, which leaves TalkBack and
 * switch-access users unable to do it at all. These cover the custom
 * accessibility actions that make it operable, and assert on *measured
 * column width* so they fail if the actions stop being wired to real resizing.
 */
@RunWith(AndroidJUnit4::class)
class DataGridResizeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private data class Row(val id: Int, val name: String, val note: String)

    private val rows = listOf(Row(1, "Alice", "first"))

    private val initialWidth = 120.dp
    private val minWidth = 80.dp
    private val maxWidth = 200.dp

    private fun columns() = listOf(
        GridColumn<Row>(
            id = "name",
            header = { BasicText("Name") },
            width = GridColumnWidth.Range(min = minWidth, max = maxWidth, initial = initialWidth),
            cell = { BasicText(it.name) },
        ),
        GridColumn<Row>(
            id = "note",
            header = { BasicText("Note") },
            width = GridColumnWidth.Fixed(100.dp),
            cell = { BasicText(it.note) },
        ),
    )

    @Composable
    private fun ResizableGrid() {
        val columns = remember { columns() }
        DataGrid(
            columns = columns,
            dataSource = rows.asGridDataSource(),
            state = rememberGridState(),
            rowKey = { it.id },
        )
    }

    private fun headerWidth(): Dp = composeRule.onNodeWithText("Name").getUnclippedBoundsInRoot().width

    private fun invokeAction(nodeText: String, label: String) {
        var invoked = false
        composeRule.onNodeWithText(nodeText).assert(
            SemanticsMatcher("has custom action '$label'") { node ->
                val action = node.config.getOrNull(SemanticsActions.CustomActions)
                    ?.firstOrNull { it.label == label }
                invoked = action?.action?.invoke() == true
                invoked
            },
        )
        composeRule.waitForIdle()
        assertTrue("expected to invoke '$label'", invoked)
    }

    @Test
    fun resizableHeaderExposesWidthActions() {
        composeRule.setContent { ResizableGrid() }

        composeRule.onNodeWithText("Name").assert(
            SemanticsMatcher("exposes both resize actions") { node ->
                val labels = node.config.getOrNull(SemanticsActions.CustomActions).orEmpty().map { it.label }
                labels.containsAll(listOf("Increase column width", "Decrease column width"))
            },
        )
    }

    @Test
    fun nonResizableHeaderExposesNoWidthActions() {
        composeRule.setContent { ResizableGrid() }

        // "note" is Fixed, so there is nothing to resize.
        composeRule.onNodeWithText("Note").assert(
            SemanticsMatcher("has no custom actions") { node ->
                node.config.getOrNull(SemanticsActions.CustomActions).isNullOrEmpty()
            },
        )
    }

    @Test
    fun increaseActionWidensTheColumn() {
        composeRule.setContent { ResizableGrid() }
        val before = headerWidth()

        invokeAction("Name", "Increase column width")

        assertTrue("expected wider than $before, was ${headerWidth()}", headerWidth() > before)
    }

    @Test
    fun decreaseActionNarrowsTheColumn() {
        composeRule.setContent { ResizableGrid() }
        val before = headerWidth()

        invokeAction("Name", "Decrease column width")

        assertTrue("expected narrower than $before, was ${headerWidth()}", headerWidth() < before)
    }

    @Test
    fun repeatedActionsClampToTheDeclaredRange() {
        composeRule.setContent { ResizableGrid() }

        // Far more steps than the range allows, in both directions.
        repeat(10) { invokeAction("Name", "Increase column width") }
        assertEquals(maxWidth.value, headerWidth().value, 1f)

        repeat(20) { invokeAction("Name", "Decrease column width") }
        assertEquals(minWidth.value, headerWidth().value, 1f)
    }

    /**
     * The grab area is [48dp wide][androidx.compose.ui.unit.Dp], Material's
     * minimum touch target, measured inward from the column boundary. A drag
     * starting 40dp inside the edge lands within it — and would have missed the
     * 24dp target this replaced.
     */
    @Test
    fun dragStartingWellInsideTheEdgeStillResizes() {
        composeRule.setContent { ResizableGrid() }
        val before = headerWidth()

        val node = composeRule.onNodeWithText("Name").fetchSemanticsNode()
        val cellWidthPx = node.size.width.toFloat()
        val insetPx = with(composeRule.density) { 40.dp.toPx() }

        composeRule.onNodeWithText("Name").performTouchInput {
            val y = node.size.height / 2f
            swipe(
                start = Offset(cellWidthPx - insetPx, y),
                end = Offset(cellWidthPx - insetPx + with(composeRule.density) { 30.dp.toPx() }, y),
                durationMillis = 200,
            )
        }
        composeRule.waitForIdle()

        assertTrue(
            "expected the drag to widen the column from $before, got ${headerWidth()}",
            headerWidth() > before,
        )
    }

    /**
     * ...but the grab area is capped at half the column, so a narrow column
     * keeps space that is plain header rather than resize surface.
     */
    @Test
    fun theGrabAreaNeverExceedsHalfTheColumn() {
        composeRule.setContent { ResizableGrid() }

        // The Range column starts at 120dp, so the cap is 60dp, not the full
        // 48dp-from-a-wider-column case. A drag starting 70dp inside the edge is
        // outside the grab area and must not resize anything.
        val before = headerWidth()
        val node = composeRule.onNodeWithText("Name").fetchSemanticsNode()
        val cellWidthPx = node.size.width.toFloat()
        val insetPx = with(composeRule.density) { 70.dp.toPx() }

        composeRule.onNodeWithText("Name").performTouchInput {
            val y = node.size.height / 2f
            swipe(
                start = Offset(cellWidthPx - insetPx, y),
                end = Offset(cellWidthPx - insetPx + with(composeRule.density) { 30.dp.toPx() }, y),
                durationMillis = 200,
            )
        }
        composeRule.waitForIdle()

        assertEquals(before.value, headerWidth().value, 0.5f)
    }

    @Test
    fun resizeHandleStateReportsBounds() {
        // Pure state check — no composition needed.
        val atMin = ResizeHandleState(isDragging = false, isHovered = true, atMinWidth = true, atMaxWidth = false)
        assertTrue(atMin.isActive)

        val idle = ResizeHandleState(isDragging = false, isHovered = false, atMinWidth = false, atMaxWidth = false)
        assertEquals(false, idle.isActive)
    }

    @Test
    fun styleSlotReceivesTheHandleState() {
        var lastState: ResizeHandleState? = null
        val style = GridStyle.Default.copy(
            resizeHandle = { handleState -> lastState = handleState },
        )
        composeRule.setContent {
            val columns = remember { columns() }
            DataGrid(
                columns = columns,
                dataSource = rows.asGridDataSource(),
                state = rememberGridState(),
                style = style,
                rowKey = { it.id },
            )
        }
        composeRule.waitForIdle()

        // Rendered for the Range column, idle and off both bounds at 120.dp.
        assertNotNull("style.resizeHandle was never invoked", lastState)
        assertEquals(false, lastState!!.isActive)
        assertEquals(false, lastState!!.atMinWidth)
        assertEquals(false, lastState!!.atMaxWidth)
    }

    @Test
    fun fixedColumnsGetNoResizeHandle() {
        var invocations = 0
        val style = GridStyle.Default.copy(resizeHandle = { invocations++ })
        composeRule.setContent {
            val columns = remember {
                listOf(
                    GridColumn<Row>(
                        id = "note",
                        header = { BasicText("Note") },
                        width = GridColumnWidth.Fixed(100.dp),
                        cell = { BasicText(it.note) },
                    ),
                )
            }
            DataGrid(
                columns = columns,
                dataSource = rows.asGridDataSource(),
                state = rememberGridState(),
                style = style,
                rowKey = { it.id },
            )
        }
        composeRule.waitForIdle()

        assertEquals(0, invocations)
    }
}
