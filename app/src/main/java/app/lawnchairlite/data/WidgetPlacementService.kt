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
    val stackTargetWidgetId: Int = 0,
)

enum class WidgetSetupStep {
    ADD_DIRECTLY,
    CONFIGURE_PROVIDER,
}

enum class WidgetPreviewVisual {
    PREVIEW_IMAGE,
    ICON_FALLBACK,
    PLACEHOLDER,
}

enum class WidgetRemovalOutcome {
    KEEP,
    DELETE_HOST_ID,
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

    fun previewVisual(hasPreviewImage: Boolean, hasIcon: Boolean): WidgetPreviewVisual = when {
        hasPreviewImage -> WidgetPreviewVisual.PREVIEW_IMAGE
        hasIcon -> WidgetPreviewVisual.ICON_FALLBACK
        else -> WidgetPreviewVisual.PLACEHOLDER
    }

    fun removalOutcome(confirmed: Boolean): WidgetRemovalOutcome =
        if (confirmed) WidgetRemovalOutcome.DELETE_HOST_ID else WidgetRemovalOutcome.KEEP

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

    fun stackIdForTarget(target: WidgetInfo): String =
        target.stackId.ifBlank { "stack:${target.appWidgetId}" }

    fun nextStackOrder(widgets: List<WidgetInfo>, stackId: String): Int =
        (widgets.filter { it.stackId == stackId }.maxOfOrNull { it.stackOrder } ?: 0).plus(1).coerceIn(1, 99)
}
