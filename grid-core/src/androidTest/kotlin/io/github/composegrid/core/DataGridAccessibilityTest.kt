package io.github.composegrid.core

import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers the M6 accessibility surface: the semantics TalkBack actually reads
 * (sort state, selection, collection position). Rendering/measurement is
 * covered by [GridColumnLayoutInfoTest]; this is strictly about what the
 * semantics tree exposes.
 */
@RunWith(AndroidJUnit4::class)
class DataGridAccessibilityTest {

    @get:Rule
    val composeRule = createComposeRule()

    private data class Person(val id: Int, val name: String, val dept: String)

    private val people = listOf(
        Person(1, "Alice", "Engineering"),
        Person(2, "Bob", "Design"),
        Person(3, "Carol", "Sales"),
    )

    private fun columns() = listOf(
        GridColumn<Person>(
            id = "name",
            header = { BasicText("Name") },
            width = GridColumnWidth.Fixed(120.dp),
            sortable = true,
            cell = { BasicText(it.name) },
        ),
        GridColumn<Person>(
            id = "dept",
            header = { BasicText("Dept") },
            width = GridColumnWidth.Fixed(120.dp),
            cell = { BasicText(it.dept) },
        ),
    )

    private fun setGrid() {
        composeRule.setContent {
            DataGrid(
                columns = columns(),
                dataSource = people.asGridDataSource(),
                rowKey = { it.id },
            )
        }
    }

    @Test
    fun sortableHeaderIsAButtonAndAnnouncesItsSortState() {
        setGrid()

        composeRule.onNodeWithText("Name")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Not sorted"))

        composeRule.onNodeWithText("Name").performClick()
        composeRule.onNodeWithText("Name")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Sorted ascending"))

        composeRule.onNodeWithText("Name").performClick()
        composeRule.onNodeWithText("Name")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Sorted descending"))

        // None -> Ascending -> Descending -> None cycle closes.
        composeRule.onNodeWithText("Name").performClick()
        composeRule.onNodeWithText("Name")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Not sorted"))
    }

    @Test
    fun nonSortableHeaderIsNotExposedAsAButton() {
        setGrid()

        composeRule.onNodeWithText("Dept")
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Role))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
    }

    @Test
    fun clickingACellTogglesSelectedSemanticsForThatRow() {
        setGrid()

        composeRule.onNodeWithText("Alice").assertIsNotSelected()
        composeRule.onNodeWithText("Alice").performClick()
        composeRule.onNodeWithText("Alice").assertIsSelected()

        // Selection is per-row, so the other cell in the same row reports it too...
        composeRule.onNodeWithText("Engineering").assertIsSelected()
        // ...while a different row stays untouched.
        composeRule.onNodeWithText("Bob").assertIsNotSelected()

        composeRule.onNodeWithText("Alice").performClick()
        composeRule.onNodeWithText("Alice").assertIsNotSelected()
    }

    @Test
    fun cellsReportTheirRowAndColumnPosition() {
        setGrid()

        fun assertPosition(text: String, row: Int, column: Int) {
            composeRule.onNodeWithText(text).assert(
                SemanticsMatcher("is at row $row, column $column") { node ->
                    val info = node.config.getOrNull(SemanticsProperties.CollectionItemInfo)
                    info != null && info.rowIndex == row && info.columnIndex == column
                },
            )
        }

        assertPosition("Alice", row = 0, column = 0)
        assertPosition("Engineering", row = 0, column = 1)
        assertPosition("Carol", row = 2, column = 0)
    }

    @Test
    fun bodyReportsCollectionSize() {
        setGrid()

        composeRule.onNode(
            SemanticsMatcher.expectValue(
                SemanticsProperties.CollectionInfo,
                CollectionInfo(rowCount = people.size, columnCount = 2),
            ),
        ).assertExists()
    }
}
