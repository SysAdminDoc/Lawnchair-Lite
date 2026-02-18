package app.lawnchairlite.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lawnchairlite.data.AppInfo
import app.lawnchairlite.data.IconShape
import kotlinx.coroutines.launch

/**
 * Lawnchair Lite v0.9.0 - App Drawer
 */
@Composable
fun AppDrawer(
    visible: Boolean, apps: List<AppInfo>, searchQuery: String,
    shape: IconShape, iconSizeDp: Dp, columns: Int,
    onSearchChange: (String) -> Unit, onAppClick: (AppInfo) -> Unit,
    onAppLongClick: (AppInfo) -> Unit, onClose: () -> Unit,
) {
    val colors = LocalLauncherColors.current
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val atTop by remember { derivedStateOf { gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset == 0 } }

    val letters = remember(apps) { apps.map { it.label.firstOrNull()?.uppercaseChar() ?: '#' }.distinct().sorted() }
    val letterIndex = remember(apps) {
        val map = mutableMapOf<Char, Int>()
        apps.forEachIndexed { i, app -> val ch = app.label.firstOrNull()?.uppercaseChar() ?: '#'; if (ch !in map) map[ch] = i }
        map
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(350, easing = FastOutSlowInEasing)) + fadeIn(tween(200)),
        exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(300, easing = FastOutSlowInEasing)) + fadeOut(tween(150)),
    ) {
        LaunchedEffect(visible) { if (visible) dragOffset = 0f }
        Column(
            Modifier.fillMaxSize().graphicsLayer { translationY = dragOffset.coerceAtLeast(0f) }
                .background(colors.background.copy(alpha = 0.97f)).statusBarsPadding()
        ) {
            Column(Modifier.fillMaxWidth().pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = { if (dragOffset > 150f) onClose() else dragOffset = 0f },
                    onDragCancel = { dragOffset = 0f },
                ) { _, amt -> if (amt > 0f || dragOffset > 0f) dragOffset = (dragOffset + amt).coerceAtLeast(0f) }
            }) {
                Box(Modifier.fillMaxWidth().padding(vertical = 14.dp), Alignment.Center) { Box(Modifier.width(40.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(colors.textSecondary.copy(alpha = 0.5f))) }
                DrawerSearch(searchQuery, onSearchChange, Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                Spacer(Modifier.height(6.dp))
                Text("${apps.size} apps", color = colors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
            }

            if (apps.isEmpty()) Box(Modifier.fillMaxWidth().weight(1f), Alignment.Center) { Text("No apps found", color = colors.textSecondary, fontSize = 14.sp) }
            else Row(Modifier.fillMaxWidth().weight(1f)) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns), state = gridState,
                    modifier = Modifier.weight(1f).pointerInput(atTop) {
                        if (atTop) detectVerticalDragGestures(
                            onDragEnd = { if (dragOffset > 150f) onClose() else dragOffset = 0f },
                            onDragCancel = { dragOffset = 0f },
                        ) { _, amt -> if (amt > 0f && atTop) dragOffset = (dragOffset + amt).coerceAtLeast(0f) }
                    },
                    contentPadding = PaddingValues(start = 8.dp, end = 0.dp, top = 4.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp), horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    items(apps, key = { it.key }) { app ->
                        Box(Modifier.fillMaxWidth(), Alignment.Center) {
                            TappableAppIcon(app, shape, iconSizeDp, showLabel = true, onClick = { onAppClick(app) }, onLongClick = { onAppLongClick(app) }, modifier = Modifier.width(70.dp))
                        }
                    }
                }
                if (letters.size > 3 && searchQuery.isBlank()) {
                    FastScrollerRail(letters = letters, onLetterSelected = { ch ->
                        letterIndex[ch]?.let { idx -> scope.launch { gridState.animateScrollToItem(idx / columns * columns) } }
                    })
                }
            }
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}
