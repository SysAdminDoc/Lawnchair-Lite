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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Icon
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
 * Lawnchair Lite - App Drawer
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
    iconShadow: Boolean,
    grayscale: Boolean,
    labelWeight: androidx.compose.ui.text.font.FontWeight,
    drawerOpacity: Int,
    showSectionHeaders: Boolean,
    labelSizeSp: Int,
    drawerSort: app.lawnchairlite.data.DrawerSort,
    onSearchChange: (String) -> Unit,
    onAppClick: (AppInfo) -> Unit,
    onAppLongClick: (AppInfo) -> Unit,
    contactResults: List<app.lawnchairlite.LauncherViewModel.ContactResult> = emptyList(),
    autoFocusSearch: Boolean = false,
    contactPermissionGranted: Boolean = true,
    onRequestContactPermission: () -> Unit = {},
    onContactTap: (String) -> Unit = {},
    onContactCall: (String) -> Unit = {},
    onSearchWeb: (String) -> Unit,
    calculatorResult: String? = null,
    searchHistory: List<String> = emptyList(),
    onSearchHistoryTap: (String) -> Unit = {},
    onSearchHistoryRemove: (String) -> Unit = {},
    onSearchHistoryClear: () -> Unit = {},
    searchEngineLabel: String = "Google",
    onVibrate: () -> Unit = {},
    onClearRecents: () -> Unit = {},
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
    val searchFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }

    LaunchedEffect(progress) {
        if (progress > 0.99f) {
            displaced = false
            if (autoFocusSearch) try { searchFocusRequester.requestFocus() } catch (_: Exception) {}
        }
    }
    LaunchedEffect(progress < 0.01f) {
        if (progress < 0.01f) { displaced = false; gridState.scrollToItem(0) }
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            // Dismiss multiplier: 2.5x makes overscroll-to-dismiss feel snappy
            // instead of requiring a full screen-height drag.
            private val dismissMultiplier = 2.5f

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (displaced && currentProgress < 0.99f) {
                    val delta = -available.y / currentScreenHeight * dismissMultiplier
                    val newP = (currentProgress + delta).coerceIn(0f, 1f)
                    currentOnProgressChange(newP)
                    if (newP > 0.99f) displaced = false
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0f && atTop) {
                    val delta = -available.y / currentScreenHeight * dismissMultiplier
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
                // Settle whenever drawer has been partially dismissed, regardless of velocity.
                // Without this, slow drags leave the drawer stuck mid-screen.
                if (displaced && currentProgress < 0.99f) {
                    currentOnSettle(available.y)
                    displaced = false
                    return available
                }
                if (atTop && available.y > 0f && currentProgress < 0.99f) {
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

    // Compute letters/index from displayApps so fast scroller is correct when
    // categories are active or section headers shift grid positions.
    val letters = remember(displayApps) { displayApps.map { it.label.firstOrNull()?.uppercaseChar() ?: '#' }.distinct().sorted() }
    // Maps letter -> index of first matching app within displayApps (used for non-header path)
    val letterIndex = remember(displayApps) {
        val map = mutableMapOf<Char, Int>()
        displayApps.forEachIndexed { i, a -> val ch = a.label.firstOrNull()?.uppercaseChar() ?: '#'; if (ch !in map) map[ch] = i }
        map
    }
    // When section headers are enabled, each header occupies one grid item slot.
    // Recompute letter -> grid-item-index accounting for those header items.
    val headerAdjustedLetterIndex = remember(displayApps, showSectionHeaders, showRecent) {
        if (!showSectionHeaders) letterIndex
        else {
            val map = mutableMapOf<Char, Int>()
            var gridIdx = if (showRecent) 1 else 0
            var lastLetter: Char? = null
            displayApps.forEach { a ->
                val ch = a.label.firstOrNull()?.uppercaseChar() ?: '#'
                if (ch != lastLetter) { lastLetter = ch; gridIdx++ }  // header item
                if (ch !in map) map[ch] = gridIdx
                gridIdx++  // app item
            }
            map
        }
    }
    // Hide fast scroller when a specific category is selected — displayApps is a subset
    // and the alphabet rail wouldn't match the visible grid position anyway.
    val showFastScroller = letters.size > 3 && searchQuery.isBlank() &&
        !(showCategories && selectedCategory != app.lawnchairlite.data.DrawerCategory.ALL)

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
                    Box(Modifier.width(48.dp).height(4.dp).clip(RoundedCornerShape(2.dp))
                        .background(colors.textSecondary.copy(alpha = 0.7f)))
                }
                DrawerSearch(searchQuery, onSearchChange, Modifier.padding(horizontal = 20.dp, vertical = 4.dp), focusRequester = searchFocusRequester)
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
                    Text(if (searchQuery.isNotBlank()) "${displayApps.size} results" else "${displayApps.size} apps", color = colors.textSecondary.copy(alpha = 0.8f), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    if (searchQuery.isBlank() && drawerSort != app.lawnchairlite.data.DrawerSort.NAME) {
                        Text("  ·  ${drawerSort.label}", color = colors.accent.copy(alpha = 0.6f), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            // Calculator result
            if (calculatorResult != null && searchQuery.isNotBlank()) {
                CalculatorResultRow(result = calculatorResult)
            }

            // Contact search permission prompt
            if (searchQuery.length >= 2 && contactResults.isEmpty() && !contactPermissionGranted) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.accent.copy(alpha = 0.06f))
                        .clickable { onRequestContactPermission() }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Enable contact search", color = colors.accent, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.weight(1f))
                    Text("Grant", color = colors.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(colors.accent.copy(alpha = 0.12f)).padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }

            // Contact search results
            if (contactResults.isNotEmpty() && searchQuery.isNotBlank()) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                    Text("CONTACTS", color = LocalLauncherColors.current.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    contactResults.forEach { contact ->
                        ContactResultRow(
                            name = contact.name, number = contact.number,
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
                        Icon(Icons.Default.SearchOff, null,
                            tint = colors.textSecondary.copy(alpha = 0.5f),
                            modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(8.dp))
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
                                Text(searchEngineLabel.take(1), color = colors.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
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
                                    Row(
                                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            "RECENT", color = colors.accent, fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
                                        )
                                        Spacer(Modifier.weight(1f))
                                        Text("Clear", color = colors.textSecondary, fontSize = 11.sp,
                                            modifier = Modifier.clip(RoundedCornerShape(8.dp))
                                                .clickable { onClearRecents() }
                                                .padding(horizontal = 8.dp, vertical = 4.dp))
                                    }
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        items(recentApps, key = { "recent_${it.key}" }) { app ->
                                            val badge = if (showBadges) notifCounts[app.packageName] ?: 0 else 0
                                            TappableAppIcon(
                                                app, shape, iconSizeDp, showLabel = true,
                                                badgeCount = badge, badgeDotOnly = badgeDotOnly,
                                                iconShadow = iconShadow, grayscale = grayscale, labelWeight = labelWeight,
                                                onClick = { onAppClick(app) },
                                                onLongClick = { onAppLongClick(app) },
                                                modifier = Modifier.width(iconSizeDp + 20.dp),
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
                                            iconShadow = iconShadow, grayscale = grayscale, labelWeight = labelWeight,
                                            onClick = { onAppClick(app) },
                                            onLongClick = { onAppLongClick(app) },
                                            modifier = Modifier.width(iconSizeDp + 20.dp),
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
                                        iconShadow = iconShadow, grayscale = grayscale, labelWeight = labelWeight,
                                        onClick = { onAppClick(app) },
                                        onLongClick = { onAppLongClick(app) },
                                        modifier = Modifier.width(iconSizeDp + 20.dp),
                                    )
                                }
                            }
                        }

                        // Web search link at bottom of results when searching
                        if (searchQuery.isNotBlank()) {
                            item(span = { GridItemSpan(columns) }, key = "__web_search__") {
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(colors.accent.copy(alpha = 0.08f))
                                        .clickable { onSearchWeb(searchQuery) }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(searchEngineLabel.take(1), color = colors.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.width(10.dp))
                                    Text("Search $searchEngineLabel for \"$searchQuery\"", color = colors.accent, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                    if (showFastScroller) {
                        FastScrollerRail(letters = letters, onLetterSelected = { ch ->
                            val scrollIdx = if (showSectionHeaders && searchQuery.isBlank() && !showCategories) {
                                headerAdjustedLetterIndex[ch] ?: return@FastScrollerRail
                            } else {
                                val appIdx = letterIndex[ch] ?: return@FastScrollerRail
                                // Grid item index = app index + offset for recent row
                                appIdx + (if (showRecent) 1 else 0)
                            }
                            scope.launch { gridState.scrollToItem(scrollIdx) }
                        }, onVibrate = onVibrate)
                    }
                }
            }
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}
