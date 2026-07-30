package io.github.composegrid.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GridDataSourceTest {

    @Test
    fun `list data source reports every item and no unknown count`() {
        val source = listOf("a", "b", "c").asGridDataSource()

        assertEquals(3, source.itemCount)
        assertEquals("a", source.peek(0))
        assertEquals("c", source.peek(2))
        assertNull(source.peek(3))
    }

    @Test
    fun `list data source defaults to idle load state`() {
        val source = listOf("a").asGridDataSource()

        assertEquals(GridLoadState.Idle, source.loadState)
    }
}
