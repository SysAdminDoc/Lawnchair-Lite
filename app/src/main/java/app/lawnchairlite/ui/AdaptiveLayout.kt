package app.lawnchairlite.ui

import kotlin.math.roundToInt

internal data class AdaptiveLauncherLayout(
    val largeScreen: Boolean,
    val drawerPaneWidthDp: Int,
    val drawerColumns: Int,
    val homeEndPaddingDp: Int,
)

internal fun resolveAdaptiveLauncherLayout(
    screenWidthDp: Int,
    screenHeightDp: Int,
    requestedDrawerColumns: Int,
    homeColumns: Int,
): AdaptiveLauncherLayout {
    val isExpandedWidth = screenWidthDp >= 840 && screenHeightDp >= 600
    val isFoldableWide = screenWidthDp >= 720 && screenHeightDp >= 720
    val largeScreen = isExpandedWidth || isFoldableWide
    val fallbackColumns = if (requestedDrawerColumns > 0) requestedDrawerColumns else homeColumns.coerceAtLeast(1)

    if (!largeScreen) {
        return AdaptiveLauncherLayout(
            largeScreen = false,
            drawerPaneWidthDp = 0,
            drawerColumns = fallbackColumns,
            homeEndPaddingDp = 0,
        )
    }

    val paneWidth = (screenWidthDp * 0.38f).roundToInt().coerceIn(360, 520)
    val autoColumns = when {
        paneWidth >= 480 -> 5
        paneWidth >= 420 -> 4
        else -> 3
    }

    return AdaptiveLauncherLayout(
        largeScreen = true,
        drawerPaneWidthDp = paneWidth,
        drawerColumns = if (requestedDrawerColumns > 0) requestedDrawerColumns.coerceIn(3, 6) else autoColumns,
        homeEndPaddingDp = paneWidth + 16,
    )
}
