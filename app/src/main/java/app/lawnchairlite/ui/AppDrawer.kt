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
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lawnchairlite.R
import app.lawnchairlite.data.AppInfo
import app.lawnchairlite.data.DrawerGroup
import app.lawnchairlite.data.DrawerTab
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
    favoriteApps: List<AppInfo>,
    workProfileApps: List<AppInfo>,
    drawerGroups: List<DrawerGroup>,
    selectedDrawerGroupId: String?,
    onDrawerGroupChange: (String?) -> Unit,
    categorizedApps: Map<app.lawnchairlite.data.DrawerCategory, List<AppInfo>>,
    showCategories: Boolean,
    selectedCategory: app.lawnchairlite.data.DrawerCategory,
    onCategoryChange: (app.lawnchairlite.data.DrawerCategory) -> Unit,
    selectedTab: DrawerTab,
    onTabChange: (DrawerTab) -> Unit,
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
    val effectiveTab = if (searchQuery.isBlank()) selectedTab else DrawerTab.ALL
    val tabApps = when (effectiveTab) {
        DrawerTab.ALL -> apps
        DrawerTab.RECENT -> recentApps
        DrawerTab.FAVORITES -> favoriteApps
        DrawerTab.WORK -> workProfileApps
    }
    val enabledGroups = drawerGroups.filter { it.enabled }
    val selectedGroup = enabledGroups.firstOrNull { it.id == selectedDrawerGroupId }
    val groupFilteredApps = if (searchQuery.isBlank() && selectedGroup != null) {
        tabApps.filter { selectedGroup.matches(it) }
    } else tabApps
    val showCategoriesForTab = showCategories && searchQuery.isBlank() && effectiveTab == DrawerTab.ALL && selectedGroup == null
    val showRecent = false
    // Use categorized apps when categories are enabled on the All tab, otherwise use the selected tab list.
    val displayApps = if (showCategoriesForTab && selectedCategory != app.lawnchairlite.data.DrawerCategory.ALL) {
        categorizedApps[selectedCategory] ?: apps
    } else groupFilteredApps

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
        !(showCategoriesForTab && selectedCategory != app.lawnchairlite.data.DrawerCategory.ALL)
    val sectionHeadersActive = showSectionHeaders && searchQuery.isBlank() && effectiveTab != DrawerTab.RECENT && !showCategoriesForTab
    val drawerHidden = progress < 0.01f
    val prewarmLabels = remember(displayApps) { displayApps.map { it.label } }
    val prewarmTargetIndex = remember(prewarmLabels, columns, sectionHeadersActive, showRecent) {
        drawerPrewarmTargetIndex(
            labels = prewarmLabels,
            columns = columns,
            showSectionHeaders = sectionHeadersActive,
            showRecentRow = showRecent,
        )
    }
    val prewarmKey = remember(prewarmLabels, columns, sectionHeadersActive, showRecent) {
        listOf(prewarmLabels, columns, sectionHeadersActive, showRecent).hashCode()
    }
    var warmedDrawerKey by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(prewarmKey, drawerHidden, prewarmTargetIndex) {
        if (!drawerHidden || warmedDrawerKey == prewarmKey || prewarmTargetIndex <= 0) return@LaunchedEffect
        withFrameNanos { }
        if (currentProgress < 0.01f && gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset == 0) {
            runCatching {
                gridState.scrollToItem(prewarmTargetIndex)
                gridState.scrollToItem(0)
                warmedDrawerKey = prewarmKey
            }
        }
    }

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
                val scrollTopLabel = stringResource(R.string.scroll_drawer_to_top)
                Box(Modifier.fillMaxWidth().padding(vertical = 14.dp).semantics { contentDescription = scrollTopLabel; role = Role.Button }.clickable(role = Role.Button) {
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
                if (searchQuery.isBlank()) {
                    Spacer(Modifier.height(4.dp))
                    DrawerTabs(
                        selectedTab = effectiveTab,
                        allCount = apps.size,
                        recentCount = recentApps.size,
                        favoriteCount = favoriteApps.size,
                        workCount = workProfileApps.size,
                        onTabChange = onTabChange,
                    )
                    if (enabledGroups.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        DrawerGroupChips(
                            groups = enabledGroups,
                            tabApps = tabApps,
                            selectedGroupId = selectedGroup?.id,
                            onGroupChange = onDrawerGroupChange,
                        )
                    }
                }
                if (showCategoriesForTab) {
                    Spacer(Modifier.height(4.dp))
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        app.lawnchairlite.data.DrawerCategory.entries.forEach { cat ->
                            val count = categorizedApps[cat]?.size ?: 0
                            if (cat == app.lawnchairlite.data.DrawerCategory.ALL || count > 0) {
                                val sel = selectedCategory == cat
                                val categoryLabel = cat.localizedLabel()
                                val categoryDescription = stringResource(R.string.category_content_description, categoryLabel, count)
                                val categoryState = stringResource(if (sel) R.string.selected else R.string.not_selected)
                                Text(
                                    if (cat == app.lawnchairlite.data.DrawerCategory.ALL) categoryLabel else stringResource(R.string.tab_count_format, categoryLabel, count),
                                    color = if (sel) colors.accent else colors.textSecondary,
                                    fontSize = 12.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(if (sel) colors.accent.copy(alpha = 0.12f) else colors.card)
                                        .semantics {
                                            contentDescription = categoryDescription
                                            role = Role.Button
                                            selected = sel
                                            stateDescription = categoryState
                                        }
                                        .clickable(role = Role.Button) { onCategoryChange(cat) }
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.padding(horizontal = 24.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    val countLabel = when {
                        searchQuery.isNotBlank() -> stringResource(R.string.drawer_results_count, displayApps.size)
                        selectedGroup != null -> stringResource(R.string.drawer_group_count, selectedGroup.name, displayApps.size)
                        effectiveTab == DrawerTab.RECENT -> stringResource(R.string.drawer_recent_count, displayApps.size)
                        effectiveTab == DrawerTab.FAVORITES -> stringResource(R.string.drawer_favorites_count, displayApps.size)
                        effectiveTab == DrawerTab.WORK -> stringResource(R.string.drawer_work_count, displayApps.size)
                        else -> stringResource(R.string.drawer_apps_count, displayApps.size)
                    }
                    Text(countLabel, color = colors.textSecondary.copy(alpha = 0.8f), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    if (searchQuery.isBlank() && effectiveTab == DrawerTab.RECENT && recentApps.isNotEmpty()) {
                        val clearRecentAppsLabel = stringResource(R.string.clear_recent_apps)
                        Spacer(Modifier.weight(1f))
                        Text(stringResource(R.string.clear), color = colors.textSecondary, fontSize = 11.sp,
                            modifier = Modifier.clip(RoundedCornerShape(8.dp))
                                .semantics { contentDescription = clearRecentAppsLabel; role = Role.Button }
                                .clickable(role = Role.Button) { onClearRecents() }
                                .padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                    if (searchQuery.isBlank() && effectiveTab == DrawerTab.ALL && drawerSort != app.lawnchairlite.data.DrawerSort.NAME) {
                        Text(stringResource(R.string.sort_suffix, drawerSort.localizedLabel()), color = colors.accent.copy(alpha = 0.6f), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            // Calculator result
            if (calculatorResult != null && searchQuery.isNotBlank()) {
                CalculatorResultRow(result = calculatorResult)
            }

            // Contact search permission prompt
            if (searchQuery.length >= 2 && contactResults.isEmpty() && !contactPermissionGranted) {
                val enableContactSearchLabel = stringResource(R.string.enable_contact_search)
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.accent.copy(alpha = 0.06f))
                        .semantics { contentDescription = enableContactSearchLabel; role = Role.Button }
                        .clickable(role = Role.Button) { onRequestContactPermission() }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(enableContactSearchLabel, color = colors.accent, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.weight(1f))
                    Text(stringResource(R.string.grant), color = colors.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(colors.accent.copy(alpha = 0.12f)).padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }

            // Contact search results
            if (contactResults.isNotEmpty() && searchQuery.isNotBlank()) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                    Text(stringResource(R.string.contacts_header), color = LocalLauncherColors.current.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
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
                        val emptyText = when {
                            searchQuery.isNotBlank() -> stringResource(R.string.no_apps_found)
                            selectedGroup != null -> stringResource(R.string.no_apps_in_drawer_group)
                            effectiveTab == DrawerTab.RECENT -> stringResource(R.string.no_recent_apps)
                            effectiveTab == DrawerTab.FAVORITES -> stringResource(R.string.no_favorites_yet)
                            effectiveTab == DrawerTab.WORK -> stringResource(R.string.no_work_profile_apps)
                            else -> stringResource(R.string.no_apps_found)
                        }
                        Text(emptyText, color = colors.textSecondary, fontSize = 14.sp)
                        if (searchQuery.isNotBlank()) {
                            val searchWebDescription = stringResource(R.string.search_web_for_query, searchQuery)
                            val quotedSearchQuery = stringResource(R.string.search_query_quoted, searchQuery)
                            Spacer(Modifier.height(12.dp))
                            Row(
                                Modifier.clip(RoundedCornerShape(20.dp))
                                    .background(colors.accent.copy(alpha = 0.12f))
                                    .semantics { contentDescription = searchWebDescription; role = Role.Button }
                                    .clickable(role = Role.Button) { onSearchWeb(searchQuery) }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(searchEngineLabel.take(1), color = colors.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.width(8.dp))
                                Text(quotedSearchQuery, color = colors.accent, fontSize = 13.sp, fontWeight = FontWeight.Medium)
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
                                            stringResource(R.string.recent_header), color = colors.accent, fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
                                        )
                                        Spacer(Modifier.weight(1f))
                                        val clearRecentAppsLabel = stringResource(R.string.clear_recent_apps)
                                        Text(stringResource(R.string.clear), color = colors.textSecondary, fontSize = 11.sp,
                                            modifier = Modifier.clip(RoundedCornerShape(8.dp))
                                                .semantics { contentDescription = clearRecentAppsLabel; role = Role.Button }
                                                .clickable(role = Role.Button) { onClearRecents() }
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

                        if (showSectionHeaders && searchQuery.isBlank() && effectiveTab != DrawerTab.RECENT && !showCategoriesForTab) {
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
                                val webSearchDescription = stringResource(R.string.search_engine_for_query, searchEngineLabel, searchQuery)
                                val webSearchLabel = stringResource(R.string.search_engine_for_query_quoted, searchEngineLabel, searchQuery)
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(colors.accent.copy(alpha = 0.08f))
                                        .semantics { contentDescription = webSearchDescription; role = Role.Button }
                                        .clickable(role = Role.Button) { onSearchWeb(searchQuery) }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(searchEngineLabel.take(1), color = colors.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.width(10.dp))
                                    Text(webSearchLabel, color = colors.accent, fontSize = 13.sp, fontWeight = FontWeight.Medium)
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

internal fun drawerPrewarmTargetIndex(
    labels: List<String>,
    columns: Int,
    showSectionHeaders: Boolean,
    showRecentRow: Boolean,
    rowsToWarm: Int = 4,
): Int {
    if (labels.size <= 1) return 0
    val appTarget = (columns.coerceAtLeast(1) * rowsToWarm).coerceAtMost(labels.lastIndex)
    var gridIndex = if (showRecentRow) 1 else 0
    var lastLetter: Char? = null

    labels.forEachIndexed { index, label ->
        if (showSectionHeaders) {
            val letter = label.firstOrNull()?.uppercaseChar() ?: '#'
            if (letter != lastLetter) {
                lastLetter = letter
                gridIndex++
            }
        }
        if (index == appTarget) return gridIndex
        gridIndex++
    }

    return 0
}

@Composable
private fun DrawerTabs(
    selectedTab: DrawerTab,
    allCount: Int,
    recentCount: Int,
    favoriteCount: Int,
    workCount: Int,
    onTabChange: (DrawerTab) -> Unit,
) {
    val colors = LocalLauncherColors.current
    val tabs = DrawerTab.entries
    val selectedIndex = tabs.indexOf(selectedTab).let { if (it >= 0) it else 0 }

    PrimaryScrollableTabRow(
        selectedTabIndex = selectedIndex,
        edgePadding = 16.dp,
        containerColor = colors.background.copy(alpha = 0f),
        contentColor = colors.accent,
        divider = {},
    ) {
        tabs.forEach { tab ->
            val tabLabel = tab.localizedLabel()
            val count = when (tab) {
                DrawerTab.ALL -> allCount
                DrawerTab.RECENT -> recentCount
                DrawerTab.FAVORITES -> favoriteCount
                DrawerTab.WORK -> workCount
            }
            Tab(
                selected = selectedTab == tab,
                onClick = { onTabChange(tab) },
                selectedContentColor = colors.accent,
                unselectedContentColor = colors.textSecondary,
                text = {
                    Text(
                        stringResource(R.string.tab_count_format, tabLabel, count),
                        fontSize = 12.sp,
                        fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Medium,
                    )
                },
            )
        }
    }
}

@Composable
private fun DrawerGroupChips(
    groups: List<DrawerGroup>,
    tabApps: List<AppInfo>,
    selectedGroupId: String?,
    onGroupChange: (String?) -> Unit,
) {
    val colors = LocalLauncherColors.current
    val selectedState = stringResource(R.string.selected)
    val notSelectedState = stringResource(R.string.not_selected)
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val allSelected = selectedGroupId == null
        val allDescription = stringResource(R.string.drawer_group_content_description, stringResource(R.string.all_groups), tabApps.size)
        Text(
            stringResource(R.string.all_groups),
            color = if (allSelected) colors.accent else colors.textSecondary,
            fontSize = 12.sp,
            fontWeight = if (allSelected) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.clip(RoundedCornerShape(16.dp))
                .background(if (allSelected) colors.accent.copy(alpha = 0.12f) else colors.card)
                .semantics {
                    contentDescription = allDescription
                    role = Role.Button
                    selected = allSelected
                    stateDescription = if (allSelected) selectedState else notSelectedState
                }
                .clickable(role = Role.Button) { onGroupChange(null) }
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
        groups.forEach { group ->
            val count = tabApps.count { group.matches(it) }
            val selected = selectedGroupId == group.id
            val label = stringResource(R.string.tab_count_format, group.name, count)
            val description = stringResource(R.string.drawer_group_content_description, group.name, count)
            Text(
                label,
                color = if (selected) colors.accent else colors.textSecondary,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                modifier = Modifier.clip(RoundedCornerShape(16.dp))
                    .background(if (selected) colors.accent.copy(alpha = 0.12f) else colors.card)
                    .semantics {
                        contentDescription = description
                        role = Role.Button
                        this.selected = selected
                        stateDescription = if (selected) selectedState else notSelectedState
                    }
                    .clickable(role = Role.Button) { onGroupChange(group.id) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}
