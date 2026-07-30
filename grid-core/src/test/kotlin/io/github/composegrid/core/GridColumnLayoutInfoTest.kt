package io.github.composegrid.core

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun column(
    id: String,
    width: GridColumnWidth,
): GridColumn<Int> = GridColumn(
    id = id,
    header = {},
    width = width,
    cell = {},
)

class GridColumnLayoutInfoTest {

    @Test
    fun `fixed columns lay out sequentially with no overlap`() {
        val columns = listOf(
            column("a", GridColumnWidth.Fixed(100.dp)),
            column("b", GridColumnWidth.Fixed(150.dp)),
            column("c", GridColumnWidth.Fixed(50.dp)),
        )
        val info = GridColumnLayoutInfo.resolve(columns, containerWidth = 1000.dp)

        assertEquals(0f, info.offset(0).value)
        assertEquals(100f, info.offset(1).value)
        assertEquals(250f, info.offset(2).value)
        assertEquals(300f, info.totalWidth.value)
    }

    @Test
    fun `weighted columns split remaining space proportionally`() {
        val columns = listOf(
            column("fixed", GridColumnWidth.Fixed(200.dp)),
            column("w1", GridColumnWidth.Weighted(1f)),
            column("w2", GridColumnWidth.Weighted(3f)),
        )
        val info = GridColumnLayoutInfo.resolve(columns, containerWidth = 1000.dp)

        // Remaining = 1000 - 200 = 800, split 1:3 -> 200 / 600
        assertEquals(200f, info.width(1).value, 0.01f)
        assertEquals(600f, info.width(2).value, 0.01f)
        assertEquals(1000f, info.totalWidth.value, 0.01f)
    }

    @Test
    fun `weighted columns collapse to zero when fixed columns exceed container width`() {
        val columns = listOf(
            column("fixed", GridColumnWidth.Fixed(1200.dp)),
            column("w", GridColumnWidth.Weighted(1f)),
        )
        val info = GridColumnLayoutInfo.resolve(columns, containerWidth = 1000.dp)

        assertEquals(0f, info.width(1).value)
    }

    @Test
    fun `range column uses initial width when no override present`() {
        val columns = listOf(
            column("r", GridColumnWidth.Range(min = 50.dp, max = 300.dp, initial = 120.dp)),
        )
        val info = GridColumnLayoutInfo.resolve(columns, containerWidth = 1000.dp)

        assertEquals(120f, info.width(0).value)
    }

    @Test
    fun `range column uses override when within bounds`() {
        val columns = listOf(
            column("r", GridColumnWidth.Range(min = 50.dp, max = 300.dp, initial = 120.dp)),
        )
        val info = GridColumnLayoutInfo.resolve(
            columns,
            containerWidth = 1000.dp,
            widthOverrides = mapOf("r" to 200.dp),
        )

        assertEquals(200f, info.width(0).value)
    }

    @Test
    fun `range column override is clamped to min and max`() {
        val columns = listOf(
            column("r", GridColumnWidth.Range(min = 50.dp, max = 300.dp, initial = 120.dp)),
        )

        val tooSmall = GridColumnLayoutInfo.resolve(
            columns,
            containerWidth = 1000.dp,
            widthOverrides = mapOf("r" to 10.dp),
        )
        assertEquals(50f, tooSmall.width(0).value)

        val tooBig = GridColumnLayoutInfo.resolve(
            columns,
            containerWidth = 1000.dp,
            widthOverrides = mapOf("r" to 1000.dp),
        )
        assertEquals(300f, tooBig.width(0).value)
    }

    @Test
    fun `empty column list resolves to zero total width`() {
        val info = GridColumnLayoutInfo.resolve(emptyList(), containerWidth = 1000.dp)

        assertEquals(0, info.columnCount)
        assertEquals(0f, info.totalWidth.value)
        assertEquals(IntRange.EMPTY, info.visibleColumnRange(0.dp, 500.dp))
    }

    @Test
    fun `visible range covers only columns intersecting the viewport`() {
        val columns = listOf(
            column("a", GridColumnWidth.Fixed(100.dp)), // [0, 100)
            column("b", GridColumnWidth.Fixed(100.dp)), // [100, 200)
            column("c", GridColumnWidth.Fixed(100.dp)), // [200, 300)
            column("d", GridColumnWidth.Fixed(100.dp)), // [300, 400)
        )
        val info = GridColumnLayoutInfo.resolve(columns, containerWidth = 400.dp)

        // Viewport fully inside column b only.
        assertEquals(1..1, info.visibleColumnRange(scrollOffsetX = 120.dp, viewportWidth = 50.dp))

        // Viewport spans b and c.
        assertEquals(1..2, info.visibleColumnRange(scrollOffsetX = 150.dp, viewportWidth = 100.dp))
    }

    @Test
    fun `visible range excludes a column that ends exactly at the viewport start`() {
        val columns = listOf(
            column("a", GridColumnWidth.Fixed(100.dp)), // [0, 100)
            column("b", GridColumnWidth.Fixed(100.dp)), // [100, 200)
        )
        val info = GridColumnLayoutInfo.resolve(columns, containerWidth = 200.dp)

        // Scrolled exactly to column b's start -> column a's [0,100) no longer intersects.
        assertEquals(1..1, info.visibleColumnRange(scrollOffsetX = 100.dp, viewportWidth = 100.dp))
    }

    @Test
    fun `visible range covers every column when viewport is wider than total content`() {
        val columns = listOf(
            column("a", GridColumnWidth.Fixed(100.dp)),
            column("b", GridColumnWidth.Fixed(100.dp)),
        )
        val info = GridColumnLayoutInfo.resolve(columns, containerWidth = 200.dp)

        assertEquals(0..1, info.visibleColumnRange(scrollOffsetX = 0.dp, viewportWidth = 5000.dp))
    }

    @Test
    fun `visible range is empty for a zero-width viewport sitting exactly on a column boundary`() {
        val columns = listOf(
            column("a", GridColumnWidth.Fixed(100.dp)), // [0, 100)
            column("b", GridColumnWidth.Fixed(100.dp)), // [100, 200)
        )
        val info = GridColumnLayoutInfo.resolve(columns, containerWidth = 200.dp)

        // A zero-width viewport is a half-open point at x=100: it's the exclusive
        // end of column a and the inclusive-but-zero-width start of column b, so
        // neither column's span actually contains it.
        assertTrue(info.visibleColumnRange(scrollOffsetX = 100.dp, viewportWidth = 0.dp).isEmpty())
    }
}
