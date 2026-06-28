package app.lawnchairlite.data

object WidgetGridPlanner {
    fun findFirstEmptySpan(
        grid: List<GridCell?>,
        page: Int,
        columns: Int,
        rows: Int,
        spanX: Int,
        spanY: Int,
    ): Pair<Int, Int>? {
        val safeColumns = columns.coerceAtLeast(1)
        val safeRows = rows.coerceAtLeast(1)
        val safeSpanX = spanX.coerceIn(1, safeColumns)
        val safeSpanY = spanY.coerceIn(1, safeRows)
        val safePage = page.coerceAtLeast(0)
        val pageSize = safeColumns * safeRows
        val pageStart = safePage * pageSize
        val requiredSize = pageStart + pageSize
        val paddedGrid = if (grid.size >= requiredSize) grid else grid + List(requiredSize - grid.size) { null }

        for (row in 0..(safeRows - safeSpanY)) {
            for (col in 0..(safeColumns - safeSpanX)) {
                var fits = true
                for (rowOffset in 0 until safeSpanY) {
                    for (colOffset in 0 until safeSpanX) {
                        val index = pageStart + (row + rowOffset) * safeColumns + col + colOffset
                        if (paddedGrid[index] != null) {
                            fits = false
                            break
                        }
                    }
                    if (!fits) break
                }
                if (fits) return row to col
            }
        }
        return null
    }
}
