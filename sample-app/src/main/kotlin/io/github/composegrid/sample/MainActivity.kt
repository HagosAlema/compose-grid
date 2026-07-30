package io.github.composegrid.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.composegrid.core.DataGrid
import io.github.composegrid.core.GridColumn
import io.github.composegrid.core.GridColumnWidth
import io.github.composegrid.core.asGridDataSource
import io.github.composegrid.core.rememberGridState

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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface {
                    EmployeeGridScreen()
                }
            }
        }
    }
}

@Composable
fun EmployeeGridScreen() {
    val employees = remember { sampleEmployees() }
    val dataSource = employees.asGridDataSource()
    val gridState = rememberGridState()

    val columns = listOf(
        GridColumn<Employee>(
            id = "name",
            header = { Text("Name") },
            width = GridColumnWidth.Fixed(160.dp),
            sortable = true,
            cell = { Text(it.name) },
        ),
        GridColumn(
            id = "department",
            header = { Text("Department") },
            width = GridColumnWidth.Fixed(140.dp),
            sortable = true,
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
            header = { Text("Salary") },
            width = GridColumnWidth.Fixed(120.dp),
            sortable = true,
            cell = { Text("$${it.salary}") },
        ),
        GridColumn(
            id = "location",
            header = { Text("Location") },
            width = GridColumnWidth.Fixed(120.dp),
            sortable = true,
            cell = { Text(it.location) },
        ),
        GridColumn(
            id = "startDate",
            header = { Text("Start date") },
            width = GridColumnWidth.Fixed(140.dp),
            sortable = true,
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
            width = GridColumnWidth.Fixed(160.dp),
            cell = { Text(it.phone) },
        ),
    )

    Scaffold { padding ->
        DataGrid(
            columns = columns,
            dataSource = dataSource,
            state = gridState,
            modifier = Modifier.padding(padding),
            rowKey = { it.id },
        )
    }
}
