package app.lawnchairlite.data

import android.appwidget.AppWidgetProviderInfo

data class PendingWidgetPlacement(
    val appWidgetId: Int,
    val providerInfo: AppWidgetProviderInfo,
    val page: Int,
    val row: Int,
    val col: Int,
    val spanX: Int,
    val spanY: Int,
)

enum class WidgetSetupStep {
    ADD_DIRECTLY,
    CONFIGURE_PROVIDER,
}

object WidgetPlacementService {
    fun findFirstEmptySpan(
        grid: List<GridCell?>,
        settings: LauncherSettings,
        page: Int,
        spanX: Int,
        spanY: Int,
    ): Pair<Int, Int>? =
        WidgetGridPlanner.findFirstEmptySpan(
            grid = grid,
            page = page,
            columns = settings.gridColumns,
            rows = settings.gridRows,
            spanX = spanX,
            spanY = spanY,
        )

    fun setupStep(hasConfigureActivity: Boolean): WidgetSetupStep =
        if (hasConfigureActivity) WidgetSetupStep.CONFIGURE_PROVIDER else WidgetSetupStep.ADD_DIRECTLY

    fun createWidgetInfo(pending: PendingWidgetPlacement): WidgetInfo =
        WidgetInfo(
            pending.appWidgetId,
            pending.page,
            pending.row,
            pending.col,
            pending.spanX,
            pending.spanY,
            pending.providerInfo.provider.flattenToString(),
        )
}
