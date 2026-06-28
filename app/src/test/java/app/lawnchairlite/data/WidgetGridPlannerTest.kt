package app.lawnchairlite.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetGridPlannerTest {

    @Test
    fun findsFirstSpanBeforeBindingWidget() {
        val grid = listOf<GridCell?>(
            GridCell.App("one"),
            null,
            null,
            null,
            null,
            null,
        )

        val span = WidgetGridPlanner.findFirstEmptySpan(
            grid = grid,
            page = 0,
            columns = 3,
            rows = 2,
            spanX = 2,
            spanY = 1,
        )

        assertEquals(0 to 1, span)
    }

    @Test
    fun returnsNullWhenWidgetDoesNotFitPage() {
        val grid = List<GridCell?>(4) { GridCell.App("app$it") }

        val span = WidgetGridPlanner.findFirstEmptySpan(
            grid = grid,
            page = 0,
            columns = 2,
            rows = 2,
            spanX = 1,
            spanY = 1,
        )

        assertNull(span)
    }
}
