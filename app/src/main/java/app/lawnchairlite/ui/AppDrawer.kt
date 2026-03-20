package app.lawnchairlite.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
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
 * Lawnchair Lite v2.12.0 - App Drawer
 *
 * v2.2.0: Recent apps row, notification badges, package name search
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
    recentApps: List<AppInfo>,
    categorizedApps: Map<app.lawnchairlite.data.DrawerCategory, List<AppInfo>>,
    showCategories: Boolean,
    selectedCategory: app.lawnchairlite.data.DrawerCategory,
    onCategoryChange: (app.lawnchairlite.data.DrawerCategory) -> Unit,
    notifCounts: Map<String, Int>,
    showBadges: Boolean,
    badgeDotOnly: Boolean,
    showLabels: Boolean,
    drawerOpacity: Int,
    showSectionHeaders: Boolean,
    labelSizeSp: Int,
    drawerSort: app.lawnchairlite.data.DrawerSort,
    onSearchChange: (String) -> Unit,
    onAppClick: (AppInfo) -> Unit,
    onAppLongClick: (AppInfo) -> Unit,
    contactResults: List<app.lawnchairlite.LauncherViewModel.ContactResult> = emptyList(),
    onContactTap: (String) -> Unit = {},
    onContactCall: (String) -> Unit = {},
    onSearchWeb: (String) -> Unit,
    calculatorResult: String? = null,
    searchHistory: List<String> = emptyList(),
    onSearchHistoryTap: (String) -> Unit = {},
    onSearchHistoryRemove: (String) -> Unit = {},
    onSearchHistoryClear: () -> Unit = {},
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
    val showRecent = searchQuery.isBlank() && recentApps.isNotEmpty() && !showCategories
    // Use categorized apps when categories enabled, otherwise use full list
    val displayApps = if (showCategories && searchQuery.isBlank() && selectedCategory != app.lawnchairlite.data.DrawerCategory.ALL) {
        categorizedApps[selectedCategory] ?: apps
    } else apps

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                this.translationY = translationY
                alpha = if (progress < 0.01f) 0f else 1f
                val entranceScale = 0.95f + progress.coerceIn(0f, 1f) * 0.05f
                scaleX = entranceScale; scaleY = entranceScale
            }
            .background(colors.background.copy(alpha = drawerOpacity / 100f))
            .statusBarsPadding()
    ) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().padding(vertical = 14.dp).clickable {
                    scope.launch { gridState.animateScrollToItem(0) }
                }, Alignment.Center) {
                    Box(Modifier.width(40.dp).height(4.dp).clip(RoundedCornerShape(2.dp))
                        .background(colors.textSecondary.copy(alpha = 0.5f)))
                }
                DrawerSearch(searchQuery, onSearchChange, Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                if (searchQuery.isBlank() && searchHistory.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    SearchHistoryChips(
                        history = searchHistory,
                        onTap = onSearchHistoryTap,
                        onRemove = onSearchHistoryRemove,
                        onClearAll = onSearchHistoryClear,
                    )
                }
                if (showCategories && searchQuery.isBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        app.lawnchairlite.data.DrawerCategory.entries.forEach { cat ->
                            val count = categorizedApps[cat]?.size ?: 0
                            if (cat == app.lawnchairlite.data.DrawerCategory.ALL || count > 0) {
                                val sel = selectedCategory == cat
                                Text(
                                    if (cat == app.lawnchairlite.data.DrawerCategory.ALL) cat.label else "${cat.label} ($count)",
                                    color = if (sel) colors.accent else colors.textSecondary,
                                    fontSize = 12.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(if (sel) colors.accent.copy(alpha = 0.12f) else colors.card)
                                        .clickable { onCategoryChange(cat) }
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.padding(horizontal = 24.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${displayApps.size} apps", color = colors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    if (drawerSort != app.lawnchairlite.data.DrawerSort.NAME) {
                        Text("  ·  ${drawerSort.label}", color = colors.accent.copy(alpha = 0.6f), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            // Calculator result
            if (calculatorResult != null && searchQuery.isNotBlank()) {
                CalculatorResultRow(result = calculatorResult)
            }

            // Contact search results
            if (contactResults.isNotEmpty() && searchQuery.isNotBlank()) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                    Text("CONTACTS", color = LocalLauncherColors.current.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    contactResults.forEach { contact ->
                        ContactResultRow(
                            name = contact.name, number = contact.number, lookupUri = contact.lookupUri,
                            onTap = { contact.lookupUri?.let { onContactTap(it) } },
                            onCall = if (contact.number != null) {{ onContactCall(contact.number) }} else null,
                        )
                    }
                    Box(Modifier.fillMaxWidth().height(0.5.dp).padding(horizontal = 20.dp).background(LocalLauncherColors.current.border.copy(alpha = 0.3f)))
                }
            }

            if (displayApps.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No apps found", color = colors.textSecondary, fontSize = 14.sp)
                        if (searchQuery.isNotBlank()) {
                            Spacer(Modifier.height(12.dp))
                            Row(
                                Modifier.clip(RoundedCornerShape(20.dp))
                                    .background(colors.accent.copy(alpha = 0.12f))
                                    .clickable { onSearchWeb(searchQuery) }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("G", color = colors.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.width(8.dp))
                                Text("Search \"$searchQuery\"", color = colors.accent, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
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
                        // Recent apps row
                        if (showRecent) {
                            item(span = { GridItemSpan(columns) }, key = "__recent__") {
                                Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                                    Text(
                                        "RECENT", color = colors.accent, fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    )
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        items(recentApps, key = { "recent_${it.key}" }) { app ->
                                            val badge = if (showBadges) notifCounts[app.packageName] ?: 0 else 0
                                            TappableAppIcon(
                                                app, shape, iconSizeDp, showLabel = true,
                                                badgeCount = badge, badgeDotOnly = badgeDotOnly,
                                                onClick = { onAppClick(app) },
                                                onLongClick = { onAppLongClick(app) },
                                                modifier = Modifier.width(70.dp),
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Box(Modifier.fillMaxWidth().height(0.5.dp).padding(horizontal = 20.dp).background(colors.border.copy(alpha = 0.3f)))
                                }
                            }
                        }

                        if (showSectionHeaders && searchQuery.isBlank() && !showCategories) {
                            // Group apps by first letter and add section headers
                            var lastLetter: Char? = null
                            displayApps.forEach { app ->
                                val letter = app.label.firstOrNull()?.uppercaseChar() ?: '#'
                                if (letter != lastLetter) {
                                    lastLetter = letter
                                    item(span = { GridItemSpan(columns) }, key = "__header_$letter") {
                                        Text(
                                            letter.toString(), color = colors.accent, fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(start = 16.dp, end = 16.dp).padding(top = 12.dp, bottom = 4.dp),
                                        )
                                    }
                                }
                                item(key = app.key) {
                                    val badge = if (showBadges) notifCounts[app.packageName] ?: 0 else 0
                                    Box(Modifier.fillMaxWidth(), Alignment.Center) {
                                        TappableAppIcon(
                                            app, shape, iconSizeDp, showLabel = showLabels,
                                            badgeCount = badge, badgeDotOnly = badgeDotOnly, labelSizeSp = labelSizeSp,
                                            onClick = { onAppClick(app) },
                                            onLongClick = { onAppLongClick(app) },
                                            modifier = Modifier.width(70.dp),
                                        )
                                    }
                                }
                            }
                        } else {
                            items(displayApps, key = { it.key }) { app ->
                                val badge = if (showBadges) notifCounts[app.packageName] ?: 0 else 0
                                Box(Modifier.fillMaxWidth(), Alignment.Center) {
                                    TappableAppIcon(
                                        app, shape, iconSizeDp, showLabel = showLabels,
                                        badgeCount = badge, badgeDotOnly = badgeDotOnly, labelSizeSp = labelSizeSp,
                                        onClick = { onAppClick(app) },
                                        onLongClick = { onAppLongClick(app) },
                                        modifier = Modifier.width(70.dp),
                                    )
                                }
                            }
                        }
                    }
                    if (letters.size > 3 && searchQuery.isBlank()) {
                        FastScrollerRail(letters = letters, onLetterSelected = { ch ->
                            letterIndex[ch]?.let { idx ->
                                scope.launch { gridState.animateScrollToItem(idx / columns * columns + (if (showRecent) 1 else 0)) }
                            }
                        })
                    }
                }
            }
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}
