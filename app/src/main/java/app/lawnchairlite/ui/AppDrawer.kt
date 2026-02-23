package app.lawnchairlite.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lawnchairlite.data.AppInfo
import app.lawnchairlite.data.IconShape
import kotlinx.coroutines.launch

/**
 * Lawnchair Lite v2.1.0 - App Drawer
 *
 * Launcher3 dismiss architecture:
 *   The grid ALWAYS has scroll enabled. The transition controller sits
 *   in the NestedScroll chain and intercepts events:
 *
 *   - Grid at top + downward pull: onPostScroll converts to progress
 *   - Sheet displaced (progress < 1): onPreScroll intercepts ALL scroll
 *     (both up and down) so the grid never moves during transition
 *   - On fling: onPreFling triggers settle in the parent
 *
 *   NEVER set userScrollEnabled=false. That kills the grid's gesture
 *   detector, NestedScrollConnection stops receiving events, user stuck.
 */
@Composable
fun AppDrawer(
    progress: Float,
    screenHeightPx: Float,
    apps: List<AppInfo>,
    searchQuery: String,
    shape: IconShape,
    iconSizeDp: Dp,
    columns: Int,
    onSearchChange: (String) -> Unit,
    onAppClick: (AppInfo) -> Unit,
    onAppLongClick: (AppInfo) -> Unit,
    onProgressChange: (Float) -> Unit,
    onSettle: (velocityPxPerSec: Float) -> Unit,
) {
    val colors = LocalLauncherColors.current
    val scope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()

    val currentProgress by rememberUpdatedState(progress)
    val currentScreenHeight by rememberUpdatedState(screenHeightPx)
    val currentOnProgressChange by rememberUpdatedState(onProgressChange)
    val currentOnSettle by rememberUpdatedState(onSettle)

    val atTop by remember { derivedStateOf {
        gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset == 0
    }}

    var displaced by remember { mutableStateOf(false) }

    val letters = remember(apps) { apps.map { it.label.firstOrNull()?.uppercaseChar() ?: '#' }.distinct().sorted() }
    val letterIndex = remember(apps) {
        val map = mutableMapOf<Char, Int>()
        apps.forEachIndexed { i, app -> val ch = app.label.firstOrNull()?.uppercaseChar() ?: '#'; if (ch !in map) map[ch] = i }
        map
    }

    LaunchedEffect(progress) {
        if (progress > 0.99f) displaced = false
    }
    LaunchedEffect(progress < 0.01f) {
        if (progress < 0.01f) { displaced = false; gridState.scrollToItem(0) }
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Sheet displaced → intercept ALL scroll to drive progress
                if (displaced && currentProgress < 0.99f) {
                    val delta = -available.y / currentScreenHeight
                    val newP = (currentProgress + delta).coerceIn(0f, 1f)
                    currentOnProgressChange(newP)
                    if (newP > 0.99f) displaced = false
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                // Grid at top + downward leftover → start dismiss
                if (available.y > 0f && atTop && currentProgress > 0.5f) {
                    val delta = -available.y / currentScreenHeight
                    val newP = (currentProgress + delta).coerceIn(0f, 1f)
                    currentOnProgressChange(newP)
                    displaced = true
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (displaced && currentProgress < 0.99f) {
                    currentOnSettle(available.y)
                    displaced = false
                    return available
                }
                return Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (atTop && available.y > 300f && currentProgress < 0.99f) {
                    currentOnSettle(available.y)
                    displaced = false
                    return available
                }
                return Velocity.Zero
            }
        }
    }

    val translationY = (1f - progress) * screenHeightPx

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                this.translationY = translationY
                alpha = if (progress < 0.01f) 0f else 1f
            }
            .background(colors.background)
            .statusBarsPadding()
    ) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().padding(vertical = 14.dp), Alignment.Center) {
                    Box(Modifier.width(40.dp).height(4.dp).clip(RoundedCornerShape(2.dp))
                        .background(colors.textSecondary.copy(alpha = 0.5f)))
                }
                DrawerSearch(searchQuery, onSearchChange, Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                Spacer(Modifier.height(6.dp))
                Text(
                    "${apps.size} apps", color = colors.textSecondary, fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                )
            }

            if (apps.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), Alignment.Center) {
                    Text("No apps found", color = colors.textSecondary, fontSize = 14.sp)
                }
            } else {
                Row(Modifier.fillMaxWidth().weight(1f)) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        state = gridState,
                        modifier = Modifier.weight(1f).nestedScroll(nestedScrollConnection),
                        contentPadding = PaddingValues(start = 8.dp, end = 0.dp, top = 4.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        items(apps, key = { it.key }) { app ->
                            Box(Modifier.fillMaxWidth(), Alignment.Center) {
                                TappableAppIcon(
                                    app, shape, iconSizeDp, showLabel = true,
                                    onClick = { onAppClick(app) },
                                    onLongClick = { onAppLongClick(app) },
                                    modifier = Modifier.width(70.dp),
                                )
                            }
                        }
                    }
                    if (letters.size > 3 && searchQuery.isBlank()) {
                        FastScrollerRail(letters = letters, onLetterSelected = { ch ->
                            letterIndex[ch]?.let { idx ->
                                scope.launch { gridState.animateScrollToItem(idx / columns * columns) }
                            }
                        })
                    }
                }
            }
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}
