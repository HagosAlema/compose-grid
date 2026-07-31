package io.github.composegrid.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.compose.collectAsLazyPagingItems
import io.github.composegrid.core.ColumnPin
import io.github.composegrid.core.DataGrid
import io.github.composegrid.core.GridColumn
import io.github.composegrid.core.GridColumnWidth
import io.github.composegrid.core.GridLoadState
import io.github.composegrid.core.SortDirection
import io.github.composegrid.core.SortIndicatorPosition
import io.github.composegrid.core.rememberGridState
import io.github.composegrid.core.rememberSortedGridDataSource
import io.github.composegrid.material3.GridDefaults
import io.github.composegrid.material3.Material3ResizeHandle
import io.github.composegrid.material3.toGridStyle
import io.github.composegrid.paging.asGridDataSource
import kotlinx.coroutines.delay

data class Employee(
    val id: Int,
    val name: String,
    val department: String,
    val role: String,
    val salary: Int,
    val location: String,
    val startDate: String,
    val manager: String,
    val email: String,
    val phone: String,
)

private fun sampleEmployees(): List<Employee> = listOf(
    Employee(1, "Ava Kim", "Engineering", "Senior Engineer", 145_000, "Seoul", "2021-03-01", "Zoe Lee", "ava.kim@example.com", "010-1111-2222"),
    Employee(2, "Noah Park", "Design", "Product Designer", 118_000, "Busan", "2022-07-15", "Ivy Han", "noah.park@example.com", "010-2222-3333"),
    Employee(3, "Mia Chen", "Engineering", "Staff Engineer", 172_000, "Seoul", "2019-11-20", "Zoe Lee", "mia.chen@example.com", "010-3333-4444"),
    Employee(4, "Leo Wang", "Sales", "Account Executive", 96_000, "Incheon", "2023-01-09", "Owen Ryu", "leo.wang@example.com", "010-4444-5555"),
    Employee(5, "Zoe Lee", "Engineering", "Engineering Manager", 165_000, "Seoul", "2018-05-30", "—", "zoe.lee@example.com", "010-5555-6666"),
    Employee(6, "Ethan Cho", "Marketing", "Growth Lead", 110_000, "Daegu", "2022-09-12", "Ivy Han", "ethan.cho@example.com", "010-6666-7777"),
    Employee(7, "Ivy Han", "Design", "Design Lead", 138_000, "Seoul", "2020-02-18", "—", "ivy.han@example.com", "010-7777-8888"),
    Employee(8, "Owen Ryu", "Sales", "Sales Director", 152_000, "Busan", "2017-10-05", "—", "owen.ryu@example.com", "010-8888-9999"),
    Employee(9, "Sora Kim", "Engineering", "Software Engineer", 128_000, "Seoul", "2023-06-01", "Zoe Lee", "sora.kim@example.com", "010-9999-0000"),
    Employee(10, "Jaden Moon", "Marketing", "Marketing Analyst", 92_000, "Daejeon", "2023-08-14", "Ethan Cho", "jaden.moon@example.com", "010-0000-1111"),
)

/** A larger synthetic dataset for the paging demo — the curated 10 above is too small to show multiple pages. */
private fun generateManyEmployees(count: Int): List<Employee> {
    val departments = listOf("Engineering", "Design", "Sales", "Marketing")
    val roles = listOf("Engineer", "Designer", "Account Executive", "Growth Lead", "Manager")
    return (1..count).map { n ->
        Employee(
            id = n,
            name = "Employee $n",
            department = departments[n % departments.size],
            role = roles[n % roles.size],
            salary = 80_000 + (n % 50) * 1_000,
            location = "City ${n % 10}",
            startDate = "2020-01-01",
            manager = "Manager ${n % 20}",
            email = "employee$n@example.com",
            phone = "010-0000-" + n.toString().padStart(4, '0'),
        )
    }
}

/**
 * Simulates a paginated backend: a fixed in-memory list served one delayed
 * page at a time. [comparator] stands in for a server-side `ORDER BY` — the
 * grid never reorders paged rows itself, so the "backend" applies the sort
 * before slicing pages.
 */
private class FakeEmployeePagingSource(
    allEmployees: List<Employee>,
    private val pageSize: Int,
    comparator: Comparator<Employee>? = null,
) : PagingSource<Int, Employee>() {
    private val rows = if (comparator == null) allEmployees else allEmployees.sortedWith(comparator)

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Employee> {
        val page = params.key ?: 0
        delay(800) // simulate network latency so the loading placeholder is actually visible
        val fromIndex = page * pageSize
        if (fromIndex >= rows.size) {
            return LoadResult.Page(
                data = emptyList(),
                prevKey = if (page == 0) null else page - 1,
                nextKey = null,
                itemsBefore = rows.size,
                itemsAfter = 0,
            )
        }
        val toIndex = (fromIndex + pageSize).coerceAtMost(rows.size)
        return LoadResult.Page(
            data = rows.subList(fromIndex, toIndex),
            prevKey = if (page == 0) null else page - 1,
            nextKey = if (toIndex >= rows.size) null else page + 1,
            // Without these, enablePlaceholders has nothing to extrapolate from and
            // itemCount stays capped at whatever's currently loaded, defeating the
            // whole point of the demo (a visible, scrollable band of placeholder
            // rows ahead of the loaded window).
            itemsBefore = fromIndex,
            itemsAfter = rows.size - toIndex,
        )
    }

    override fun getRefreshKey(state: PagingState<Int, Employee>): Int? = null
}

private fun employeeColumns(): List<GridColumn<Employee>> = listOf(
    GridColumn(
        id = "name",
        header = { Text("Name") },
        width = GridColumnWidth.Range(min = 80.dp, max = 200.dp, initial = 140.dp),
        sortable = true,
        comparator = compareBy { it.name },
        pinned = ColumnPin.Start,
        cell = { Text(it.name) },
    ),
    GridColumn(
        id = "department",
        header = { Text("Department") },
        width = GridColumnWidth.Fixed(140.dp),
        sortable = true,
        comparator = compareBy { it.department },
        cell = { Text(it.department) },
    ),
    GridColumn(
        id = "role",
        header = { Text("Role") },
        width = GridColumnWidth.Range(min = 120.dp, max = 320.dp, initial = 180.dp),
        cell = { Text(it.role) },
    ),
    GridColumn(
        id = "salary",
        header = {
            // Right-aligned, like the numbers below it. This is the case
            // SortIndicatorPosition.Leading exists for — see the Paged tab.
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                Text("Salary")
            }
        },
        width = GridColumnWidth.Fixed(120.dp),
        sortable = true,
        comparator = compareBy { it.salary },
        cell = {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                Text("$${it.salary}")
            }
        },
    ),
    GridColumn(
        id = "location",
        header = { Text("Location") },
        width = GridColumnWidth.Fixed(120.dp),
        sortable = true,
        comparator = compareBy { it.location },
        cell = { Text(it.location) },
    ),
    GridColumn(
        id = "startDate",
        header = { Text("Start date") },
        width = GridColumnWidth.Fixed(140.dp),
        sortable = true,
        // ISO-8601 dates sort correctly as plain strings.
        comparator = compareBy { it.startDate },
        cell = { Text(it.startDate) },
    ),
    GridColumn(
        id = "manager",
        header = { Text("Manager") },
        width = GridColumnWidth.Fixed(140.dp),
        cell = { Text(it.manager) },
    ),
    GridColumn(
        id = "email",
        header = { Text("Email") },
        width = GridColumnWidth.Fixed(220.dp),
        cell = { Text(it.email) },
    ),
    GridColumn(
        id = "phone",
        header = { Text("Phone") },
        width = GridColumnWidth.Range(min = 60.dp, max = 160.dp, initial = 160.dp),
        cell = { Text(it.phone) },
    ),
    // Pinned to the trailing edge: stays put during horizontal scroll, mirroring
    // the pinned "Name" column at the start. Also resizable, so this exercises a
    // pinned *and* resizable column — the two features have to share the header
    // cell's trailing edge.
    GridColumn(
        id = "tenure",
        header = { Text("Tenure") },
        width = GridColumnWidth.Range(min = 70.dp, max = 140.dp, initial = 90.dp),
        sortable = true,
        comparator = compareBy { it.startDate },
        pinned = ColumnPin.End,
        cell = { Text(it.startDate.take(4)) },
    ),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var darkTheme by remember { mutableStateOf(false) }
            val colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme()
            MaterialTheme(colorScheme = colorScheme) {
                Surface {
                    SampleAppRoot(darkTheme = darkTheme, onDarkThemeChange = { darkTheme = it })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class) // TopAppBar
@Composable
fun SampleAppRoot(darkTheme: Boolean, onDarkThemeChange: (Boolean) -> Unit) {
    var selectedTab by remember { mutableStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ComposeGrid sample") },
                actions = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Dark theme")
                        Switch(checked = darkTheme, onCheckedChange = onDarkThemeChange)
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("In-memory") },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Paged") },
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                if (selectedTab == 0) EmployeeGridScreen() else PagedEmployeeGridScreen()
            }
        }
    }
}

/**
 * In-memory sorting: [rememberSortedGridDataSource] reorders rows from each
 * column's comparator.
 *
 * Also shows two other things:
 *  - Selection state hoisted out of the grid. [GridState.selectedRowKeys] is
 *    plain observable state, so a toolbar above the grid can read it and clear
 *    it without the grid knowing anything about that UI.
 *  - [Material3ResizeHandle], the opt-in chevron resize affordance (visible on
 *    mouse hover or while dragging). The Paged tab keeps the plain default line
 *    for contrast.
 */
@Composable
fun EmployeeGridScreen() {
    val employees = remember { sampleEmployees() }
    val columns = remember { employeeColumns() }
    val gridState = rememberGridState()
    val dataSource = rememberSortedGridDataSource(employees, columns, gridState)
    val colors = GridDefaults.colors()
    val style = remember(colors) {
        colors.toGridStyle(resizeHandle = { handleState -> Material3ResizeHandle(handleState) })
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SelectionSummary(
            selectedCount = gridState.selectedRowKeys.size,
            totalCount = employees.size,
            onClear = { gridState.clearSelection() },
        )
        DataGrid(
            columns = columns,
            dataSource = dataSource,
            state = gridState,
            modifier = Modifier.weight(1f),
            style = style,
            rowKey = { it.id },
        )
    }
}

/** Reads [io.github.composegrid.core.GridState.selectedRowKeys] from outside the grid. */
@Composable
private fun SelectionSummary(selectedCount: Int, totalCount: Int, onClear: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (selectedCount == 0) {
                "Tap a row to select it"
            } else {
                "$selectedCount of $totalCount selected"
            },
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        if (selectedCount > 0) {
            TextButton(onClick = onClear) { Text("Clear") }
        }
    }
}

/**
 * Demonstrates the `grid-paging` adapter: real Paging 3 load states drive the
 * placeholder cells below.
 *
 * Also shows the *server-side* sorting path. A paged source can't be reordered
 * client-side — only the loaded window is in memory — so instead of
 * [rememberSortedGridDataSource], the sort state rebuilds the `Pager` and the
 * (fake) backend returns rows already ordered.
 *
 * Uses [SortIndicatorPosition.Leading] to contrast with the In-memory tab's
 * default: with the right-aligned Salary column, the arrow reads better on the
 * side the numbers align to.
 */
@Composable
fun PagedEmployeeGridScreen() {
    val columns = remember { employeeColumns() }
    val gridState = rememberGridState()
    val allEmployees = remember { generateManyEmployees(200) }
    val colors = GridDefaults.colors()
    val style = remember(colors) {
        colors.toGridStyle(sortIndicatorPosition = SortIndicatorPosition.Leading)
    }

    val pager = remember(gridState.sortColumnId, gridState.sortDirection) {
        val ascending = columns.firstOrNull { it.id == gridState.sortColumnId }?.comparator
        val comparator = when (gridState.sortDirection) {
            SortDirection.None -> null
            SortDirection.Ascending -> ascending
            SortDirection.Descending -> ascending?.reversed()
        }
        Pager(PagingConfig(pageSize = 20, enablePlaceholders = true)) {
            FakeEmployeePagingSource(allEmployees, pageSize = 20, comparator = comparator)
        }
    }
    val lazyPagingItems = pager.flow.collectAsLazyPagingItems()
    val dataSource = lazyPagingItems.asGridDataSource()

    DataGrid(
        columns = columns,
        dataSource = dataSource,
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        style = style,
        rowKey = { it.id },
        placeholderCell = { loadState ->
            when (loadState) {
                is GridLoadState.Loading -> Text("…")
                is GridLoadState.Error -> Text("!")
                GridLoadState.Idle -> {}
            }
        },
    )
}
