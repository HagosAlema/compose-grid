package io.github.composegrid.material3

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.composegrid.core.ColumnPin
import io.github.composegrid.core.DataGrid
import io.github.composegrid.core.DefaultResizeHandle
import io.github.composegrid.core.GridColumn
import io.github.composegrid.core.GridColumnWidth
import io.github.composegrid.core.GridState
import io.github.composegrid.core.SortDirection
import io.github.composegrid.core.SortIndicatorPosition
import io.github.composegrid.core.asGridDataSource
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Visual regression cover for the rendered grid.
 *
 * Theming is a real public surface — colours, two composable slots, cell
 * padding, indicator placement — and the behavioural tests assert semantics,
 * not pixels. Nothing else catches a change that silently moves the sort
 * indicator or drops the selection tint.
 *
 * ```
 * ./gradlew :grid-material3:recordRoborazziDebug   # (re)record references
 * ./gradlew :grid-material3:verifyRoborazziDebug   # fail on any visual diff
 * ```
 *
 * Runs on the JVM under Robolectric, so no device or emulator is needed and it
 * works in CI. `sdk = 35` is deliberate: Robolectric's newest `android-all`
 * image is Android 15, while the project compiles against 37.
 *
 * Each case pins its own state explicitly rather than relying on defaults, so a
 * diff means a rendering change and not incidental state drift.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h240dp-xhdpi")
class GridScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private data class Row(val id: Int, val name: String, val team: String, val amount: Int)

    private val rows = listOf(
        Row(1, "Ava Kim", "Engineering", 145_000),
        Row(2, "Noah Park", "Design", 118_000),
        Row(3, "Mia Chen", "Engineering", 172_000),
        Row(4, "Leo Wang", "Sales", 96_000),
    )

    private fun columns(): List<GridColumn<Row>> = listOf(
        GridColumn(
            id = "name",
            header = { Text("Name") },
            width = GridColumnWidth.Fixed(120.dp),
            sortable = true,
            pinned = ColumnPin.Start,
            cell = { Text(it.name) },
        ),
        GridColumn(
            id = "team",
            header = { Text("Team") },
            width = GridColumnWidth.Fixed(130.dp),
            sortable = true,
            cell = { Text(it.team) },
        ),
        GridColumn(
            id = "amount",
            header = {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    Text("Amount")
                }
            },
            width = GridColumnWidth.Range(min = 80.dp, max = 160.dp, initial = 110.dp),
            sortable = true,
            cell = {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    Text("$${it.amount}")
                }
            },
        ),
    )

    @Composable
    private fun Fixture(
        darkTheme: Boolean = false,
        sortColumnId: String? = null,
        sortDirection: SortDirection = SortDirection.None,
        selectedIds: List<Int> = emptyList(),
        indicatorPosition: SortIndicatorPosition = SortIndicatorPosition.Trailing,
        chevronHandle: Boolean = false,
    ) {
        MaterialTheme(colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme()) {
            Surface {
                val gridColumns = remember { columns() }
                val state = remember {
                    GridState(initialSortColumnId = sortColumnId, initialSortDirection = sortDirection)
                        .also { grid -> selectedIds.forEach(grid::toggleSelection) }
                }
                val colors = GridDefaults.colors()
                val style = remember(colors) {
                    colors.toGridStyle(
                        sortIndicatorPosition = indicatorPosition,
                        resizeHandle = if (chevronHandle) {
                            { handleState -> Material3ResizeHandle(handleState) }
                        } else {
                            { handleState ->
                                DefaultResizeHandle(
                                    handleState,
                                    restColor = colors.dividerColor,
                                    activeColor = colors.focusIndicatorColor,
                                )
                            }
                        },
                    )
                }
                DataGrid(
                    columns = gridColumns,
                    dataSource = rows.asGridDataSource(),
                    state = state,
                    modifier = Modifier.size(400.dp, 240.dp),
                    style = style,
                    rowKey = { it.id },
                )
            }
        }
    }

    private fun capture(name: String, content: @Composable () -> Unit) {
        composeRule.setContent { content() }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test
    fun light() = capture("grid_light") { Fixture() }

    @Test
    fun dark() = capture("grid_dark") { Fixture(darkTheme = true) }

    /** Guards indicator placement and the gutter reserved for the resize handle. */
    @Test
    fun sortedAscendingTrailingIndicator() = capture("grid_sorted_trailing") {
        Fixture(sortColumnId = "team", sortDirection = SortDirection.Ascending)
    }

    @Test
    fun sortedDescendingLeadingIndicator() = capture("grid_sorted_leading") {
        Fixture(
            sortColumnId = "amount",
            sortDirection = SortDirection.Descending,
            indicatorPosition = SortIndicatorPosition.Leading,
        )
    }

    /** The selected-row tint has to span the pinned and scrollable regions alike. */
    @Test
    fun selectionAcrossRegions() = capture("grid_selection") {
        Fixture(selectedIds = listOf(2, 4))
    }

    @Test
    fun selectionDark() = capture("grid_selection_dark") {
        Fixture(darkTheme = true, selectedIds = listOf(2))
    }

    /**
     * At rest the chevron handle should be indistinguishable from the default
     * line — the chevrons only appear while hovered or dragged, which a static
     * capture can't produce. Pins that, so the opt-in handle can't start drawing
     * something permanent unnoticed.
     */
    @Test
    fun chevronHandleAtRest() = capture("grid_chevron_handle_at_rest") {
        Fixture(chevronHandle = true)
    }
}
