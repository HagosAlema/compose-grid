package io.github.composegrid.core

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Arrow keys move focus with `FocusManager.moveFocus`, which can only reach
 * cells that exist — and virtualization means the cell past the viewport edge
 * hasn't been composed. Without the scroll-then-retry these cover, a keyboard
 * user hits a wall at the edge of the visible window and can't reach the rest
 * of the data at all.
 *
 * Uses [setContentWithKeyboardInputMode]: focus assertions silently fail in the
 * default touch input mode — see [FocusHarnessInvestigationTest].
 *
 * The grid is sized far smaller than its content so the wall is only a few
 * keypresses away.
 */
@RunWith(AndroidJUnit4::class)
class DataGridKeyboardNavigationTest {

    @get:Rule
    val composeRule = createComposeRule()

    private data class Cell(val id: Int, val label: String)

    /** 60 rows of 40dp in a 200dp viewport: about 5 visible at a time. */
    private val rows = (0 until 60).map { Cell(it, "row-$it") }

    private val rowHeight = 40.dp
    private val viewportHeight = 200.dp
    private val viewportWidth = 200.dp

    @Composable
    private fun Grid(columnCount: Int = 1) {
        val columns = remember(columnCount) {
            (0 until columnCount).map { index ->
                GridColumn<Cell>(
                    id = "col-$index",
                    header = { BasicText("H$index") },
                    // Each column fills the viewport, so horizontal navigation
                    // hits the wall after a single step.
                    width = GridColumnWidth.Fixed(viewportWidth),
                    cell = { BasicText("${it.label}/c$index") },
                )
            }
        }
        DataGrid(
            columns = columns,
            dataSource = rows.asGridDataSource(),
            state = rememberGridState(),
            modifier = Modifier.size(viewportWidth, viewportHeight),
            rowHeight = rowHeight,
            rowKey = { it.id },
        )
    }

    private enum class Axis { Vertical, Horizontal }

    private fun scrollOffset(axis: Axis): Float {
        val node = composeRule.onNode(
            SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollBy),
        ).fetchSemanticsNode()
        return when (axis) {
            Axis.Vertical -> node.config[SemanticsProperties.VerticalScrollAxisRange]
            Axis.Horizontal -> node.config[SemanticsProperties.HorizontalScrollAxisRange]
        }.value()
    }

    private fun press(key: Key, times: Int = 1) {
        repeat(times) {
            composeRule.onRoot().performKeyInput { pressKey(key) }
            composeRule.waitForIdle()
        }
    }

    private fun startAtFirstCell(columnCount: Int = 1) {
        composeRule.setContentWithKeyboardInputMode { Grid(columnCount) }
        composeRule.onNodeWithText("row-0/c0").requestFocus()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("row-0/c0").assertIsFocused()
    }

    @Test
    fun movingWithinTheViewportDoesNotScroll() {
        startAtFirstCell()

        // Two rows down is still inside the visible window, so focus has
        // somewhere to go without moving the viewport.
        press(Key.DirectionDown, times = 2)

        composeRule.onNodeWithText("row-2/c0").assertIsFocused()
        assertEquals(0f, scrollOffset(Axis.Vertical), 0.5f)
    }

    @Test
    fun movingPastTheBottomEdgeScrollsTheGrid() {
        startAtFirstCell()

        press(Key.DirectionDown, times = 10)

        assertTrue(
            "expected a scroll, offset was ${scrollOffset(Axis.Vertical)}",
            scrollOffset(Axis.Vertical) > 0f,
        )
    }

    @Test
    fun keyboardReachesARowThatWasNeverComposed() {
        startAtFirstCell()

        // The whole point of the feature: row-20 is far outside the initial
        // ~5-row window, so it doesn't exist to focus at the start.
        composeRule.onNodeWithText("row-20/c0").assertDoesNotExist()

        press(Key.DirectionDown, times = 20)

        composeRule.onNodeWithText("row-20/c0").assertIsFocused()
    }

    @Test
    fun scrollingBackUpReturnsToTheTop() {
        startAtFirstCell()
        press(Key.DirectionDown, times = 20)
        assertTrue(scrollOffset(Axis.Vertical) > 0f)

        press(Key.DirectionUp, times = 20)

        composeRule.onNodeWithText("row-0/c0").assertIsFocused()
        assertEquals(0f, scrollOffset(Axis.Vertical), 0.5f)
    }

    @Test
    fun holdingAgainstTheTopEdgeIsHarmless() {
        startAtFirstCell()

        press(Key.DirectionUp, times = 3)

        composeRule.onNodeWithText("row-0/c0").assertIsFocused()
        assertEquals(0f, scrollOffset(Axis.Vertical), 0.5f)
    }

    @Test
    fun movingPastTheTrailingEdgeScrollsHorizontally() {
        startAtFirstCell(columnCount = 4)

        press(Key.DirectionRight)

        assertTrue(
            "expected a horizontal scroll, offset was ${scrollOffset(Axis.Horizontal)}",
            scrollOffset(Axis.Horizontal) > 0f,
        )
        composeRule.onNodeWithText("row-0/c1").assertIsFocused()
    }

    @Test
    fun oneHorizontalStepRevealsExactlyOneColumn() {
        startAtFirstCell(columnCount = 4)

        press(Key.DirectionRight)

        // Columns are viewport-width, so revealing the next one means scrolling
        // by exactly one column rather than some arbitrary amount.
        val expected = with(composeRule.density) { viewportWidth.toPx() }
        assertEquals(expected, scrollOffset(Axis.Horizontal), 2f)
    }
}
