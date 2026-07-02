package app.lawnchairlite.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetPlacementServiceTest {
    @Test
    fun usesConfigurationStepOnlyWhenProviderRequiresIt() {
        assertEquals(WidgetSetupStep.ADD_DIRECTLY, WidgetPlacementService.setupStep(hasConfigureActivity = false))
        assertEquals(WidgetSetupStep.CONFIGURE_PROVIDER, WidgetPlacementService.setupStep(hasConfigureActivity = true))
    }

    @Test
    fun delegatesSpanPlanningThroughLauncherSettings() {
        val grid = listOf<GridCell?>(
            GridCell.App("one"),
            null,
            null,
            null,
            null,
            null,
        )
        val settings = LauncherSettings(gridColumns = 3, gridRows = 2)

        val span = WidgetPlacementService.findFirstEmptySpan(
            grid = grid,
            settings = settings,
            page = 0,
            spanX = 2,
            spanY = 1,
        )

        assertEquals(0 to 1, span)
    }

    @Test
    fun returnsNullWhenSettingsGridCannotFitWidget() {
        val grid = List<GridCell?>(4) { GridCell.App("app$it") }
        val settings = LauncherSettings(gridColumns = 2, gridRows = 2)

        val span = WidgetPlacementService.findFirstEmptySpan(
            grid = grid,
            settings = settings,
            page = 0,
            spanX = 1,
            spanY = 1,
        )

        assertNull(span)
    }
}
