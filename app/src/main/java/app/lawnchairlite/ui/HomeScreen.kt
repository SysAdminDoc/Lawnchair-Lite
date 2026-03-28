package app.lawnchairlite.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.appwidget.AppWidgetManager
import app.lawnchairlite.LauncherViewModel
import app.lawnchairlite.data.*
import kotlinx.coroutines.launch

/**
 * Lawnchair Lite - Home Screen
 *
 * Drawer transition ported from Launcher3's AbstractStateChangeTouchController
 * + AllAppsSwipeController + AllAppsTransitionController.
 *
 * Architecture:
 *   drawerProgress: Animatable<Float, AnimationVector1D>
 *     0f = home (workspace visible, drawer offscreen)
 *     1f = drawer fully open
 *
 *   Swipe up on home:
 *     detectVerticalDragGestures directly snapTo(progress) every frame
 *     On release: settle() decides commit vs revert
 *
 *   Drawer overscroll dismiss:
 *     AppDrawer's NestedScrollConnection calls onProgressChange + onSettle
 *     Same settle() logic applies
 *
 * Thresholds from Launcher3 source:
 *   AllAppsSwipeController.ALL_APPS_STATE_TRANSITION_MANUAL = 0.4f
 *   SWIPE_DRAG_COMMIT_THRESHOLD = 1 - 0.4 = 0.6f
 *   AbstractStateChangeTouchController.onDragEnd:
 *     if fling -> target = direction of velocity
 *     else -> target = interpolatedProgress > successThreshold
 *   BaseSwipeDetector.isFling: abs(velocity) > 1dp (very sensitive)
 *
 * Our Compose equivalent:
 *   Opening: commit when progress > 0.4 OR fling velocity > 500 dp/s upward
 *   Closing: commit when progress < 0.6 OR fling velocity > 500 dp/s downward
 *   Fling threshold: 200 dp/s with Compose VelocityTracker for accurate velocity
 */
@Composable
fun HomeScreen(vm: LauncherViewModel) {
    val settings by vm.settings.collectAsState()
    val homeGrid by vm.homeGrid.collectAsState()
    val dockGrid by vm.dockGrid.collectAsState()
    val allApps by vm.filteredApps.collectAsState()
    val search by vm.search.collectAsState()
    val drag by vm.dragState.collectAsState()
    val dragOff by vm.dragOffset.collectAsState()
    val hoverIdx by vm.hoverIndex.collectAsState()
    val hoverDock by vm.hoverDock.collectAsState()
    val hoverRemove by vm.hoverRemove.collectAsState()
    val hoverUninstall by vm.hoverUninstall.collectAsState()
    val openFolder by vm.openFolder.collectAsState()
    val folderRename by vm.folderRename.collectAsState()
    val drawerMenuApp by vm.drawerMenuApp.collectAsState()
    val labelEdit by vm.labelEdit.collectAsState()
    val customLabels by vm.customLabels.collectAsState()
    val homeMenu by vm.homeMenu.collectAsState()
    val editMode by vm.editMode.collectAsState()
    val settingsOpen by vm.settingsOpen.collectAsState()
    val notifCounts by vm.notifCounts.collectAsState()
    val appShortcuts by vm.shortcuts.collectAsState()
    val recentApps by vm.recentApps.collectAsState()
    val categorizedApps by vm.categorizedApps.collectAsState()
    val selectedCategory by vm.selectedCategory.collectAsState()
    val widgetPickerOpen by vm.widgetPickerOpen.collectAsState()
    val homeSpaceMenu by vm.homeSpaceMenu.collectAsState()
    val widgetInfos by vm.widgets.collectAsState()
    val suggestedApps by vm.suggestedApps.collectAsState()
    val colors = LocalLauncherColors.current
    val isDragging = drag != null
    val iconDp = settings.iconSize.dp.dp
    val dockCount = settings.dockCount
    val homeLabels = settings.labelStyle != app.lawnchairlite.data.LabelStyle.HIDDEN && settings.labelStyle != app.lawnchairlite.data.LabelStyle.DRAWER_ONLY
    val cols = settings.gridColumns; val rows = settings.gridRows; val pageSize = cols * rows
    val resolvedLabelWeight = when (settings.labelWeight) { app.lawnchairlite.data.LabelWeight.LIGHT -> FontWeight.Light; app.lawnchairlite.data.LabelWeight.BOLD -> FontWeight.Bold; else -> FontWeight.Normal }
    val numPages = vm.numPages()
    val homeBounds = remember { mutableStateMapOf<Int, Rect>() }
    val dockBounds = remember { mutableStateMapOf<Int, Rect>() }
    var removeZoneBounds by remember { mutableStateOf(Rect.Zero) }
    var uninstallZoneBounds by remember { mutableStateOf(Rect.Zero) }
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { numPages })
    val currentPage by remember { derivedStateOf { pagerState.currentPage } }

    // Haptic feedback on page limits
    var lastHapticPage by remember { mutableIntStateOf(-1) }
    LaunchedEffect(pagerState.isScrollInProgress, pagerState.currentPage) {
        if (pagerState.isScrollInProgress) {
            val atStart = pagerState.currentPage == 0 && pagerState.currentPageOffsetFraction < -0.02f
            val atEnd = pagerState.currentPage == numPages - 1 && pagerState.currentPageOffsetFraction > 0.02f
            if ((atStart || atEnd) && lastHapticPage != pagerState.currentPage) {
                vm.vibrate(); lastHapticPage = pagerState.currentPage
            }
        } else { lastHapticPage = -1 }
    }

    // ═══════════════════════════════════════════════════════════════════
    // DRAWER TRANSITION STATE (Launcher3 port)
    // ═══════════════════════════════════════════════════════════════════
    val density = LocalDensity.current
    val screenHeightPx = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    // Fling threshold: 200 dp/s. Launcher3 uses ~1dp (any movement counts).
    // 200dp/s is the minimum that filters out accidental touches while still
    // making the drawer feel responsive to intentional swipes.
    val flingThresholdPx = with(density) { 200.dp.toPx() }
    val scope = rememberCoroutineScope()
    val drawerProgress = remember { Animatable(0f) }
    var openedViaSearch by remember { mutableStateOf(false) }
    val dpVal = drawerProgress.value
    val drawerVisible = dpVal > 0.01f
    val drawerFullyOpen = dpVal > 0.95f
    // Stable refs for gesture lambdas — reads current value without
    // causing pointerInput key invalidation (the critical fix).
    val currentDrawerFullyOpen by rememberUpdatedState(drawerFullyOpen)
    val currentDrawerVisible by rememberUpdatedState(drawerVisible)

    /**
     * Settle drawer: decides whether to commit (open/close) or revert.
     * Direct port of AbstractStateChangeTouchController.onDragEnd logic.
     *
     * @param velocityPxPerSec: positive = toward open, negative = toward close
     *   (for the home-swipe-up case: upward movement = positive = opening)
     *   (for the drawer-overscroll case: downward fling = negative = closing)
     */
    fun settleDrawer(velocityPxPerSec: Float) {
        scope.launch {
            val p = drawerProgress.value
            val isFling = kotlin.math.abs(velocityPxPerSec) > flingThresholdPx
            val flingToOpen = isFling && velocityPxPerSec > 0
            val flingToClose = isFling && velocityPxPerSec < 0

            // Launcher3 thresholds:
            // Opening from home: commit at progress > 0.4
            // Closing from drawer: commit at progress < 0.6
            val shouldOpen = flingToOpen || (!flingToClose && p > 0.4f)
            val shouldClose = flingToClose || (!flingToOpen && p < 0.6f)
            val animated = settings.drawerAnimation

            if (shouldOpen && !shouldClose) {
                if (animated) drawerProgress.animateTo(1f, spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = if (isFling) Spring.StiffnessMediumLow else Spring.StiffnessMedium,
                )) else drawerProgress.snapTo(1f)
            } else {
                if (animated) drawerProgress.animateTo(0f, spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = if (isFling) Spring.StiffnessLow else Spring.StiffnessMedium,
                )) else drawerProgress.snapTo(0f)
                vm.setSearch("")
                vm.setSelectedCategory(app.lawnchairlite.data.DrawerCategory.ALL)
            }
        }
    }

    // Keep ViewModel in sync for back-press handling
    LaunchedEffect(dpVal) {
        if (dpVal > 0.5f && !vm.drawerOpen.value) vm.openDrawer()
        else if (dpVal < 0.01f && vm.drawerOpen.value) { vm.closeDrawer(); openedViaSearch = false }
    }

    // ViewModel can request close externally (back press, home button)
    val vmDrawerOpen by vm.drawerOpen.collectAsState()
    LaunchedEffect(vmDrawerOpen) {
        if (!vmDrawerOpen && drawerProgress.value > 0.01f) {
            if (settings.drawerAnimation) drawerProgress.animateTo(0f, spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMedium,
            )) else drawerProgress.snapTo(0f)
            vm.setSelectedCategory(app.lawnchairlite.data.DrawerCategory.ALL)
        }
    }

    fun hitTest(pos: Offset) {
        if (removeZoneBounds != Rect.Zero && removeZoneBounds.contains(pos)) { vm.setHoverRemove(true); return }
        if (uninstallZoneBounds != Rect.Zero && uninstallZoneBounds.contains(pos)) { if (drag?.appInfo?.isSystemApp != true) { vm.setHoverUninstall(true); return } }
        vm.setHoverRemove(false); vm.setHoverUninstall(false)
        for ((i, b) in dockBounds) { if (b.contains(pos)) { vm.setHover(i, true); return } }
        for ((li, b) in homeBounds) { if (b.contains(pos)) { vm.setHover(currentPage * pageSize + li, false); return } }
        vm.setHover(-1, false)
    }

    // ═══════════════════════════════════════════════════════════════════
    // HOME CONTENT: scales/fades as drawer opens (Launcher3 workspace
    // uses WORKSPACE_SCALE_MANUAL and WORKSPACE_FADE_MANUAL interpolators)
    // ═══════════════════════════════════════════════════════════════════
    // Launcher3: workspace fades out early (step function at 0.4 of transition)
    // and scales down slightly. We use continuous interpolation instead.
    val homeAlpha = (1f - dpVal * 2f).coerceIn(0f, 1f) // Fades out by 50% progress
    val homeScale = 1f - (dpVal * 0.05f).coerceIn(0f, 0.05f) // Subtle shrink

    Box(Modifier.fillMaxSize()) {
        // Wallpaper dim overlay
        val wallpaperDim = settings.wallpaperDim
        if (wallpaperDim > 0) {
            val parallaxOffset = if (settings.wallpaperParallax && numPages > 1) {
                val pageOffset = pagerState.currentPage + pagerState.currentPageOffsetFraction
                val maxOffset = 30f // dp
                val normalized = pageOffset / (numPages - 1).coerceAtLeast(1).toFloat()
                (normalized - 0.5f) * maxOffset * 2f
            } else 0f
            Box(Modifier.fillMaxSize().graphicsLayer { translationX = with(density) { parallaxOffset.dp.toPx() } }.background(Color.Black.copy(alpha = wallpaperDim / 100f)))
        }

        // ═════════════════════════════════════════════════════════════
        // HOME LAYER
        // ═════════════════════════════════════════════════════════════
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer(alpha = homeAlpha, scaleX = homeScale, scaleY = homeScale)
                // ── Swipe-up to open drawer (AllAppsSwipeController port) ──
                // KEY: drawerVisible is NOT a key here. If it were, the gesture
                // coroutine would be cancelled the instant progress crosses 0.01
                // (1-2 frames into the swipe), killing the drag mid-flight.
                .pointerInput(isDragging, settingsOpen) {
                    if (!isDragging && !settingsOpen) {
                        var totalDrag = 0f
                        val velocityTracker = VelocityTracker()
                        detectVerticalDragGestures(
                            onDragStart = {
                                totalDrag = 0f
                                velocityTracker.resetTracking()
                            },
                            onDragEnd = {
                                val velocity = velocityTracker.calculateVelocity()
                                // velocity.y: positive = downward, negative = upward
                                // settleDrawer: positive = open (upward), negative = close
                                val settleVelocity = -velocity.y
                                if (drawerProgress.value > 0.01f) {
                                    settleDrawer(settleVelocity)
                                } else if (totalDrag < -80f && settings.swipeUpAction != GestureAction.APP_DRAWER) {
                                    vm.executeGesture(settings.swipeUpAction, "swipe_up")
                                } else if (totalDrag > 40f) {
                                    vm.executeGesture(settings.swipeDownAction, "swipe_down")
                                }
                            },
                            onDragCancel = {
                                if (drawerProgress.value > 0f) settleDrawer(0f)
                            },
                        ) { change, amount ->
                            if (currentDrawerFullyOpen) return@detectVerticalDragGestures
                            totalDrag += amount
                            velocityTracker.addPosition(change.uptimeMillis, change.position)

                            if (amount < 0f || drawerProgress.value > 0f) {
                                if (settings.swipeUpAction == GestureAction.APP_DRAWER || drawerProgress.value > 0f) {
                                    val delta = -amount / screenHeightPx
                                    val newP = (drawerProgress.value + delta).coerceIn(0f, 1f)
                                    scope.launch { drawerProgress.snapTo(newP) }
                                }
                            }
                        }
                    }
                }
                // ── Double-tap + triple-tap gesture ──
                .pointerInput(isDragging, settingsOpen) {
                    if (!isDragging && !settingsOpen) {
                        var lastDoubleTapTime = 0L
                        detectTapGestures(onDoubleTap = {
                            if (!currentDrawerVisible) {
                                val now = System.currentTimeMillis()
                                if (now - lastDoubleTapTime < 400L && settings.tripleTapAction != GestureAction.NONE) {
                                    vm.executeGesture(settings.tripleTapAction, "triple_tap")
                                } else {
                                    vm.executeGesture(settings.doubleTapAction, "double_tap")
                                }
                                lastDoubleTapTime = now
                            }
                        })
                    }
                }
                // ── Pinch gesture ──
                .pointerInput(isDragging, settingsOpen) {
                    if (!isDragging && !settingsOpen && settings.pinchAction != GestureAction.NONE) {
                        awaitEachGesture {
                            awaitFirstDown(pass = PointerEventPass.Initial)
                            var zoom = 1f
                            do {
                                val event = awaitPointerEvent()
                                if (event.changes.size >= 2) {
                                    zoom *= event.calculateZoom()
                                    if (zoom < 0.7f) {
                                        event.changes.forEach { it.consume() }
                                        if (!currentDrawerVisible) vm.executeGesture(settings.pinchAction, "pinch")
                                        break
                                    }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    }
                }
        ) {
            Column(Modifier.fillMaxSize().then(if (!settings.hideStatusBar) Modifier.statusBarsPadding() else Modifier)) {
                // Drop zones (visible when dragging)
                AnimatedVisibility(isDragging, enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(), exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()) {
                    Row(Modifier.fillMaxWidth()) {
                        Box(Modifier.weight(1f).onGloballyPositioned { removeZoneBounds = it.boundsInRoot() }) { RemoveZone(hoverRemove) }
                        Box(Modifier.weight(1f).onGloballyPositioned { uninstallZoneBounds = it.boundsInRoot() }) { UninstallZone(hoverUninstall, drag?.appInfo?.isSystemApp == true) }
                    }
                }

                // Top bar
                if (!isDragging) Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (editMode) {
                        Text("Done", color = colors.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clip(RoundedCornerShape(10.dp))
                                .background(colors.accent.copy(alpha = 0.12f))
                                .clickable { vm.exitEditMode() }
                                .padding(horizontal = 14.dp, vertical = 6.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Widgets", color = colors.text, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                            modifier = Modifier.clip(RoundedCornerShape(10.dp))
                                .background(colors.card)
                                .clickable { vm.openWidgetPicker() }
                                .padding(horizontal = 14.dp, vertical = 6.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { vm.openSettings() }, Modifier.size(36.dp)) {
                        Icon(Icons.Default.Settings, "Settings", tint = colors.text.copy(alpha = 0.4f), modifier = Modifier.size(16.dp))
                    }
                }

                if (settings.showClock && !isDragging && !editMode) AtAGlanceClock(clockStyle = settings.clockStyle, onDateClick = { vm.openCalendarApp() }, onTimeClick = { vm.openClockApp() }, onCycleStyle = { vm.cycleClockStyle() })

                if (settings.showSuggestions && suggestedApps.isNotEmpty() && !isDragging && !editMode) {
                    SuggestionRow(
                        apps = suggestedApps,
                        shape = settings.iconShape,
                        iconSizeDp = iconDp,
                        iconShadow = settings.iconShadow,
                        grayscale = settings.grayscaleIcons,
                        labelWeight = resolvedLabelWeight,
                        onAppClick = { vm.launch(it) },
                    )
                }

                val paddedGrid = remember(homeGrid, pageSize, numPages) { val t = numPages * pageSize; if (homeGrid.size >= t) homeGrid.take(t) else homeGrid + List(t - homeGrid.size) { null } }

                val gridPadH = settings.gridPaddingH.dp
                val gridPadV = settings.gridPaddingV.dp
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth().weight(1f), userScrollEnabled = !isDragging && !drawerVisible) { page ->
                    val ps = page * pageSize
                    val pageCells = paddedGrid.subList(ps.coerceAtMost(paddedGrid.size), (ps + pageSize).coerceAtMost(paddedGrid.size))
                    LaunchedEffect(page) { if (page == currentPage) homeBounds.clear() }

                    // Page transition effect
                    val pageOffset = pagerState.currentPageOffsetFraction + (page - pagerState.currentPage)
                    val transitionMod = when (settings.pageTransition) {
                        app.lawnchairlite.data.PageTransition.CUBE -> Modifier.graphicsLayer {
                            val rot = pageOffset * -30f
                            rotationY = rot
                            cameraDistance = 12f * this.density
                            alpha = (1f - kotlin.math.abs(pageOffset) * 0.3f).coerceIn(0f, 1f)
                        }
                        app.lawnchairlite.data.PageTransition.STACK -> Modifier.graphicsLayer {
                            val scale = (1f - kotlin.math.abs(pageOffset) * 0.15f).coerceIn(0.85f, 1f)
                            scaleX = scale; scaleY = scale
                            alpha = (1f - kotlin.math.abs(pageOffset) * 0.5f).coerceIn(0f, 1f)
                        }
                        app.lawnchairlite.data.PageTransition.FADE -> Modifier.graphicsLayer {
                            alpha = (1f - kotlin.math.abs(pageOffset) * 1.5f).coerceIn(0f, 1f)
                        }
                        app.lawnchairlite.data.PageTransition.DEPTH -> Modifier.graphicsLayer {
                            if (pageOffset < 0) {
                                // Page moving to the left (behind) — shrink and fade
                                val scale = (1f + pageOffset * 0.25f).coerceIn(0.75f, 1f)
                                scaleX = scale; scaleY = scale
                                alpha = (1f + pageOffset).coerceIn(0f, 1f)
                                translationX = size.width * pageOffset
                            } else {
                                // Page moving to the right (in front) — slide normally
                                translationX = 0f
                                alpha = (1f - pageOffset).coerceIn(0f, 1f)
                            }
                        }
                        app.lawnchairlite.data.PageTransition.CAROUSEL -> Modifier.graphicsLayer {
                            val rot = pageOffset * 15f
                            rotationY = rot
                            val sc = (1f - kotlin.math.abs(pageOffset) * 0.1f).coerceIn(0.9f, 1f)
                            scaleX = sc; scaleY = sc
                            translationX = pageOffset * size.width * 0.15f
                            alpha = (1f - kotlin.math.abs(pageOffset) * 0.4f).coerceIn(0f, 1f)
                            cameraDistance = 8f * this.density
                        }
                        app.lawnchairlite.data.PageTransition.SLIDE -> Modifier
                    }

                    Box(Modifier.fillMaxSize().then(transitionMod).padding(horizontal = gridPadH).padding(vertical = gridPadV)) {
                        // Grid cells
                        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
                            for (row in 0 until rows) Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                                for (col in 0 until cols) {
                                    val li = row * cols + col; val gi = ps + li; val cell = pageCells.getOrNull(li)
                                    val isHovered = !hoverDock && hoverIdx == gi && isDragging
                                    val isDragSrc = drag?.source == DragSource.HOME && drag?.sourceIndex == gi
                                    // Skip widget cells (rendered by overlay)
                                    val isWidgetCell = cell is GridCell.Widget

                                    val hoverScale by animateFloatAsState(if (isHovered) 1.12f else 1f, spring(dampingRatio = 0.5f, stiffness = 400f), label = "hs$gi")
                                    val hoverAlpha by animateFloatAsState(if (isHovered) 0.2f else 0f, tween(150), label = "ha$gi")

                                    Box(
                                        Modifier.weight(1f).aspectRatio(0.85f)
                                            .onGloballyPositioned { if (page == currentPage) homeBounds[li] = it.boundsInRoot() }
                                            .graphicsLayer(scaleX = hoverScale, scaleY = hoverScale, alpha = if (isWidgetCell) 0f else 1f)
                                            .then(if (hoverAlpha > 0f) Modifier.clip(RoundedCornerShape(14.dp)).background(colors.accent.copy(alpha = hoverAlpha)) else Modifier)
                                            .then(if (cell == null && !editMode && !isDragging) Modifier.pointerInput(Unit) {
                                                detectTapGestures(onLongPress = { vm.showHomeSpaceMenu() })
                                            } else Modifier),
                                        Alignment.Center,
                                    ) {
                                        if (!isWidgetCell) {
                                            val cellBadge = if (settings.showNotifBadges && settings.badgeStyle != app.lawnchairlite.data.BadgeStyle.HIDDEN && cell is GridCell.App) notifCounts[cell.appKey.substringBefore("/")] ?: 0 else 0
                                            val folderBadge = if (settings.showNotifBadges && settings.badgeStyle != app.lawnchairlite.data.BadgeStyle.HIDDEN && cell is GridCell.Folder) cell.appKeys.sumOf { notifCounts[it.substringBefore("/")] ?: 0 } else 0
                                            GridCellView(
                                                cell, settings.iconShape, iconDp, { vm.resolveApp(it) }, customLabels,
                                                isDragSrc, homeLabels, editMode,
                                                badgeCount = cellBadge, badgeDotOnly = settings.badgeStyle == app.lawnchairlite.data.BadgeStyle.DOT, iconShadow = settings.iconShadow, labelSizeSp = settings.labelSize.sp, folderBadgeCount = folderBadge, grayscale = settings.grayscaleIcons, labelWeight = resolvedLabelWeight,
                                                onTap = { when (cell) {
                                                    is GridCell.App -> vm.resolveApp(cell.appKey)?.let { vm.launch(it) }
                                                    is GridCell.Folder -> vm.openFolderView(cell, DragSource.HOME, gi)
                                                    is GridCell.Widget -> {}
                                                    null -> {}
                                                }},
                                                onLongPress = { if (cell != null) vm.showHomeMenu(cell, DragSource.HOME, gi) },
                                                onDragStart = { rp -> if (cell != null) vm.startDrag(cell, DragSource.HOME, gi, rp) },
                                                onDrag = { rp -> vm.updateDrag(rp); hitTest(rp) },
                                                onDragEnd = { vm.endDrag() },
                                                onDragCancel = { vm.cancelDrag() },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        // Widget overlays on this page
                        val pageWidgets = widgetInfos.filter { it.page == page }
                        val context = androidx.compose.ui.platform.LocalContext.current
                        pageWidgets.forEach { wi ->
                            val cellW = 1f / cols
                            val cellH = 1f / rows
                            val hostView = remember(wi.appWidgetId) {
                                try {
                                    val info = vm.widgetManager.getAppWidgetInfo(wi.appWidgetId)
                                    if (info != null) {
                                        vm.widgetHost.createView(context, wi.appWidgetId, info)
                                    } else {
                                        // Widget provider uninstalled — clean up stale entry
                                        vm.removeWidget(wi.appWidgetId)
                                        null
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.w("HomeScreen", "Widget create failed: ${wi.appWidgetId}", e)
                                    null
                                }
                            }
                            if (hostView != null) {
                                BoxWithConstraints(
                                    Modifier.fillMaxSize().graphicsLayer(clip = true)
                                ) {
                                    WidgetHostViewComposable(
                                        hostView = hostView,
                                        modifier = Modifier
                                            .width(maxWidth * cellW * wi.spanX)
                                            .height(maxHeight * cellH * wi.spanY)
                                            .absoluteOffset(
                                                x = maxWidth * wi.col * cellW,
                                                y = maxHeight * wi.row * cellH,
                                            )
                                            .then(if (editMode) Modifier.clickable { vm.removeWidget(wi.appWidgetId) } else Modifier),
                                    )
                                }
                            }
                        }
                    }
                }
                if (!isDragging) when (settings.pageIndicatorStyle) {
                    app.lawnchairlite.data.PageIndicatorStyle.DOTS -> PageDots(numPages, currentPage, Modifier.padding(vertical = 6.dp))
                    app.lawnchairlite.data.PageIndicatorStyle.LINE -> PageLineIndicator(numPages, currentPage, Modifier.padding(vertical = 6.dp))
                    app.lawnchairlite.data.PageIndicatorStyle.HIDDEN -> {}
                }

                // Dock
                if (!settings.hideDock) Column(Modifier.fillMaxWidth()) {
                    if (settings.showDockSearch && settings.searchBarStyle != app.lawnchairlite.data.SearchBarStyle.HIDDEN && !isDragging) {
                        when (settings.searchBarStyle) {
                            app.lawnchairlite.data.SearchBarStyle.PILL -> SearchPill(
                                onClick = { openedViaSearch = true; scope.launch { drawerProgress.animateTo(1f, spring(stiffness = Spring.StiffnessMedium)) } },
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 5.dp),
                                searchEngineLabel = settings.searchEngine.label,
                            )
                            app.lawnchairlite.data.SearchBarStyle.BAR -> SearchPill(
                                onClick = { openedViaSearch = true; scope.launch { drawerProgress.animateTo(1f, spring(stiffness = Spring.StiffnessMedium)) } },
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp),
                                searchEngineLabel = settings.searchEngine.label,
                            )
                            app.lawnchairlite.data.SearchBarStyle.MINIMAL -> Box(
                                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 5.dp)
                                    .clickable { scope.launch { drawerProgress.animateTo(1f, spring(stiffness = Spring.StiffnessMedium)) } },
                                Alignment.CenterEnd,
                            ) { Icon(Icons.Default.Search, null, tint = colors.textSecondary, modifier = Modifier.size(22.dp)) }
                            else -> {}
                        }
                    }
                    if (!isDragging) {
                        val pulseAnim = rememberInfiniteTransition(label = "dockPulse")
                        val pulseAlpha by pulseAnim.animateFloat(
                            initialValue = 0.3f, targetValue = 0.6f,
                            animationSpec = infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pa",
                        )
                        Box(
                            Modifier.fillMaxWidth()
                                .clickable {
                                    if (settings.dockTapAction == GestureAction.APP_DRAWER) {
                                        scope.launch { drawerProgress.animateTo(1f, spring(stiffness = Spring.StiffnessMedium)) }
                                    } else {
                                        vm.executeGesture(settings.dockTapAction, "dock_tap")
                                    }
                                }
                                .padding(vertical = 3.dp),
                            Alignment.Center,
                        ) { Box(Modifier.width(32.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(colors.accent.copy(alpha = pulseAlpha))) }
                    }
                    val dockMod = when (settings.dockStyle) {
                        app.lawnchairlite.data.DockStyle.SOLID -> Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)).background(colors.dock)
                        app.lawnchairlite.data.DockStyle.PILL -> Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clip(RoundedCornerShape(24.dp)).background(colors.dock)
                        app.lawnchairlite.data.DockStyle.FLOATING -> Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp).clip(RoundedCornerShape(20.dp)).background(colors.surface)
                        app.lawnchairlite.data.DockStyle.TRANSPARENT -> Modifier.fillMaxWidth()
                    }
                    Row(dockMod.padding(horizontal = 8.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        for (i in 0 until dockCount) {
                            val cell = dockGrid.getOrNull(i)
                            val isH = hoverDock && hoverIdx == i && isDragging
                            val isDS = drag?.source == DragSource.DOCK && drag?.sourceIndex == i
                            val dockHoverScale by animateFloatAsState(if (isH) 1.15f else 1f, spring(dampingRatio = 0.5f, stiffness = 400f), label = "dhs$i")
                            val dockHoverAlpha by animateFloatAsState(if (isH) 0.2f else 0f, tween(150), label = "dha$i")
                            val hasDockSwipe = settings.dockSwipeApps.containsKey(i)

                            Box(
                                Modifier.weight(1f).height(58.dp)
                                    .onGloballyPositioned { dockBounds[i] = it.boundsInRoot() }
                                    .graphicsLayer(scaleX = dockHoverScale, scaleY = dockHoverScale)
                                    .then(if (dockHoverAlpha > 0f) Modifier.clip(RoundedCornerShape(12.dp)).background(colors.accent.copy(alpha = dockHoverAlpha)) else Modifier)
                                    .then(if (hasDockSwipe && cell != null && !isDragging && !editMode) Modifier.pointerInput(i) {
                                        var totalDragY = 0f
                                        detectVerticalDragGestures(
                                            onDragStart = { totalDragY = 0f },
                                            onDragEnd = { if (totalDragY < -80f) vm.launchDockSwipe(i) },
                                            onDragCancel = {},
                                        ) { _, amount -> totalDragY += amount }
                                    } else Modifier),
                                Alignment.Center,
                            ) {
                                val dockBadge = if (settings.showNotifBadges && settings.badgeStyle != app.lawnchairlite.data.BadgeStyle.HIDDEN && cell is GridCell.App) notifCounts[cell.appKey.substringBefore("/")] ?: 0 else 0
                                val dockFolderBadge = if (settings.showNotifBadges && settings.badgeStyle != app.lawnchairlite.data.BadgeStyle.HIDDEN && cell is GridCell.Folder) cell.appKeys.sumOf { notifCounts[it.substringBefore("/")] ?: 0 } else 0
                                GridCellView(
                                    cell, settings.iconShape, iconDp, { vm.resolveApp(it) }, customLabels,
                                    isDS, false, editMode,
                                    badgeCount = dockBadge, badgeDotOnly = settings.badgeStyle == app.lawnchairlite.data.BadgeStyle.DOT, iconShadow = settings.iconShadow, labelSizeSp = settings.labelSize.sp, folderBadgeCount = dockFolderBadge, grayscale = settings.grayscaleIcons, labelWeight = resolvedLabelWeight,
                                    onTap = { when (cell) {
                                        is GridCell.App -> vm.resolveApp(cell.appKey)?.let { vm.launch(it) }
                                        is GridCell.Folder -> vm.openFolderView(cell, DragSource.DOCK, i)
                                        is GridCell.Widget -> {}
                                        null -> {}
                                    }},
                                    onLongPress = { if (cell != null) vm.showHomeMenu(cell, DragSource.DOCK, i) },
                                    onDragStart = { rp -> if (cell != null) vm.startDrag(cell, DragSource.DOCK, i, rp) },
                                    onDrag = { rp -> vm.updateDrag(rp); hitTest(rp) },
                                    onDragEnd = { vm.endDrag() },
                                    onDragCancel = { vm.cancelDrag() },
                                )
                                // Dock swipe indicator
                                if (hasDockSwipe && !isDragging && !editMode) {
                                    Box(Modifier.align(Alignment.BottomCenter).offset(y = 2.dp).width(12.dp).height(2.dp).clip(RoundedCornerShape(1.dp)).background(colors.accent.copy(alpha = 0.5f)))
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.navigationBarsPadding())
            }

            if (isDragging) DragGhost(drag?.item, drag?.appInfo, settings.iconShape, dragOff, { vm.resolveApp(it) }, iconDp)
        }

        // Contact permission launcher
        var contactPermGranted by remember { mutableStateOf(vm.hasContactPermission()) }
        val contactPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            contactPermGranted = granted
        }

        // ═════════════════════════════════════════════════════════════
        // DRAWER LAYER (always composed, hidden via graphicsLayer when closed
        // to preserve scroll position and avoid recomposition on open/close)
        // ═════════════════════════════════════════════════════════════
        Box(Modifier.graphicsLayer { alpha = if (dpVal > 0.005f) 1f else 0f }) {
            AppDrawer(
                progress = dpVal,
                screenHeightPx = screenHeightPx,
                apps = allApps,
                searchQuery = search,
                shape = settings.iconShape,
                iconSizeDp = iconDp,
                columns = if (settings.drawerColumns > 0) settings.drawerColumns else cols,
                recentApps = recentApps,
                categorizedApps = categorizedApps,
                showCategories = settings.drawerCategories,
                selectedCategory = selectedCategory,
                onCategoryChange = { vm.setSelectedCategory(it) },
                notifCounts = notifCounts,
                showBadges = settings.showNotifBadges && settings.badgeStyle != app.lawnchairlite.data.BadgeStyle.HIDDEN,
                badgeDotOnly = settings.badgeStyle == app.lawnchairlite.data.BadgeStyle.DOT,
                showLabels = settings.labelStyle != app.lawnchairlite.data.LabelStyle.HOME_ONLY && settings.labelStyle != app.lawnchairlite.data.LabelStyle.HIDDEN,
                iconShadow = settings.iconShadow,
                grayscale = settings.grayscaleIcons,
                labelWeight = resolvedLabelWeight,
                drawerSort = settings.drawerSort,
                drawerOpacity = settings.drawerOpacity,
                showSectionHeaders = settings.drawerSectionHeaders,
                labelSizeSp = settings.labelSize.sp,
                onSearchChange = { vm.setSearch(it) },
                onAppClick = { clickedApp ->
                    vm.launch(clickedApp)
                    scope.launch { drawerProgress.animateTo(0f, tween(200)) }
                    vm.setSearch("")
                    vm.setSelectedCategory(app.lawnchairlite.data.DrawerCategory.ALL)
                },
                onAppLongClick = { vm.showDrawerMenu(it) },
                autoFocusSearch = openedViaSearch,
                contactResults = vm.contactResults.collectAsState().value,
                contactPermissionGranted = contactPermGranted,
                onRequestContactPermission = { contactPermLauncher.launch(android.Manifest.permission.READ_CONTACTS) },
                onContactTap = { uri -> vm.openContact(uri); scope.launch { drawerProgress.animateTo(0f, tween(200)) }; vm.setSearch(""); vm.setSelectedCategory(app.lawnchairlite.data.DrawerCategory.ALL) },
                onContactCall = { number -> vm.callContact(number); scope.launch { drawerProgress.animateTo(0f, tween(200)) }; vm.setSearch(""); vm.setSelectedCategory(app.lawnchairlite.data.DrawerCategory.ALL) },
                onSearchWeb = { vm.searchWeb(it) },
                calculatorResult = vm.calculatorResult.collectAsState().value,
                searchHistory = vm.searchHistory.collectAsState().value,
                onSearchHistoryTap = { vm.setSearch(it) },
                onSearchHistoryRemove = { vm.removeSearchHistoryItem(it) },
                onSearchHistoryClear = { vm.clearSearchHistory() },
                searchEngineLabel = settings.searchEngine.label,
                onVibrate = { vm.vibrate() },
                onClearRecents = { vm.clearRecentApps() },
                onProgressChange = { newP ->
                    scope.launch { drawerProgress.snapTo(newP) }
                },
                onSettle = { velocityPxPerSec ->
                    // velocityPxPerSec from NestedScroll: positive = downward fling = close
                    // settleDrawer expects: positive = open, negative = close
                    settleDrawer(-velocityPxPerSec)
                },
            )
        }

        // ═════════════════════════════════════════════════════════════
        // OVERLAYS
        // ═════════════════════════════════════════════════════════════
        SettingsPanel(visible = settingsOpen, settings = settings, vm = vm, onClose = { vm.closeSettings() })

        // Widget picker
        if (widgetPickerOpen) {
            val availableWidgets = remember { vm.getAvailableWidgets() }
            val widgetContext = androidx.compose.ui.platform.LocalContext.current
            val widgetCellW = (LocalConfiguration.current.screenWidthDp - settings.gridPaddingH * 2) / cols
            WidgetPickerDialog(
                widgets = availableWidgets,
                onSelect = { providerInfo ->
                    val widgetId = vm.allocateWidgetId()
                    val bound = vm.canBindWidget(widgetId, providerInfo.provider)
                    if (bound) {
                        // Find first empty span for widget — derive cell size from actual grid
                        val minCols = ((providerInfo.minWidth + widgetCellW - 1) / widgetCellW).coerceIn(1, cols)
                        val minRows = ((providerInfo.minHeight + widgetCellW - 1) / widgetCellW).coerceIn(1, rows)
                        val span = vm.findFirstEmptySpan(currentPage, minCols, minRows)
                        if (span != null) {
                            vm.addWidget(WidgetInfo(widgetId, currentPage, span.first, span.second, minCols, minRows, providerInfo.provider.flattenToString()))
                        } else {
                            vm.widgetHost.deleteAppWidgetId(widgetId)
                            android.widget.Toast.makeText(widgetContext, "No room on this page — clear some cells first", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        // Need user permission to bind — clean up and inform user
                        vm.widgetHost.deleteAppWidgetId(widgetId)
                        android.widget.Toast.makeText(widgetContext, "Widget requires permission — try a different widget", android.widget.Toast.LENGTH_SHORT).show()
                    }
                },
                onDismiss = { vm.closeWidgetPicker() },
            )
        }

        // Home space long-press menu (Pixel Launcher-style)
        if (homeSpaceMenu) {
            HomeSpaceMenuOverlay(
                onEditMode = { vm.dismissHomeSpaceMenu(); vm.enterEditMode() },
                onAddWidget = { vm.dismissHomeSpaceMenu(); vm.enterEditMode(); vm.openWidgetPicker() },
                onAddPage = { vm.dismissHomeSpaceMenu(); vm.addPage() },
                onRemovePage = { vm.dismissHomeSpaceMenu(); vm.removePage(currentPage) },
                canRemovePage = vm.numPages() > 1,
                onWallpaper = { vm.dismissHomeSpaceMenu(); vm.openWallpaperPicker() },
                onSettings = { vm.dismissHomeSpaceMenu(); vm.openSettings() },
                onDismiss = { vm.dismissHomeSpaceMenu() },
            )
        }

        val menu = homeMenu
        if (menu != null) HomeContextMenu(menuState = menu, shape = settings.iconShape, vm = vm, shortcuts = appShortcuts, onDismiss = { vm.dismissHomeMenu() })

        val fState = openFolder
        if (fState != null) { val (folder, src, fi) = fState
            FolderOverlay(folder, settings.iconShape, iconDp, { vm.resolveApp(it) }, customLabels, folderColumns = settings.folderColumns, iconShadow = settings.iconShadow, grayscale = settings.grayscaleIcons, labelWeight = resolvedLabelWeight, onAppClick = { vm.launch(it); vm.closeFolderView() }, onRemoveApp = { k -> vm.removeFolderApp(src, fi, k) }, onReorder = { keys -> vm.reorderFolderApps(src, fi, keys) }, onRename = { vm.startFolderRename(src, fi, folder.name) }, onDismiss = { vm.closeFolderView() }) }

        val rn = folderRename
        if (rn != null) RenameDialog(rn.current, "Rename Folder", onConfirm = { vm.renameFolder(rn.source, rn.index, it); vm.closeFolderView() }, onDismiss = { vm.dismissFolderRename() })

        val le = labelEdit
        if (le != null) RenameDialog(le.current, "Rename Shortcut", onConfirm = { vm.saveCustomLabel(le.appKey, it) }, onDismiss = { vm.dismissLabelEdit() })

        // Uninstall confirmation dialog
        val uninstallApp by vm.uninstallConfirm.collectAsState()
        uninstallApp?.let { confirm ->
            UninstallConfirmDialog(
                appName = confirm.app.label,
                onConfirm = { vm.confirmUninstall() },
                onDismiss = { vm.dismissUninstall() },
            )
        }

        val menuApp = drawerMenuApp
        if (menuApp != null) DrawerContextMenu(menuApp, settings.iconShape, vm = vm, shortcuts = appShortcuts, onShortcutClick = { vm.launchShortcut(it); scope.launch { drawerProgress.animateTo(0f, tween(200)) }; vm.setSearch(""); vm.setSelectedCategory(app.lawnchairlite.data.DrawerCategory.ALL) }, onPinHome = { vm.pinToHome(menuApp); scope.launch { drawerProgress.animateTo(0f, tween(200)) }; vm.setSearch(""); vm.setSelectedCategory(app.lawnchairlite.data.DrawerCategory.ALL) }, onPinDock = { vm.pinToDock(menuApp); scope.launch { drawerProgress.animateTo(0f, tween(200)) }; vm.setSearch(""); vm.setSelectedCategory(app.lawnchairlite.data.DrawerCategory.ALL) }, onHide = { vm.hideApp(menuApp.key) }, onAppInfo = { vm.appInfo(menuApp); vm.dismissDrawerMenu() }, onUninstall = { vm.requestUninstall(menuApp); vm.dismissDrawerMenu() }, onDismiss = { vm.dismissDrawerMenu() })
    }
}

// ═════════════════════════════════════════════════════════════════════════
// GridCellView: dual gesture handling for normal and edit modes
// ═════════════════════════════════════════════════════════════════════════
@Composable
private fun GridCellView(
    cell: GridCell?, shape: IconShape, iconSizeDp: androidx.compose.ui.unit.Dp,
    resolveApp: (String) -> AppInfo?, customLabels: Map<String, String>,
    dimmed: Boolean, showLabel: Boolean, editMode: Boolean,
    badgeCount: Int = 0, badgeDotOnly: Boolean = false, iconShadow: Boolean = false, labelSizeSp: Int = 11, folderBadgeCount: Int = 0, grayscale: Boolean = false, labelWeight: FontWeight = FontWeight.Normal,
    onTap: () -> Unit, onLongPress: () -> Unit,
    onDragStart: (Offset) -> Unit, onDrag: (Offset) -> Unit, onDragEnd: () -> Unit, onDragCancel: () -> Unit,
) {
    if (cell == null) return

    val wiggleAnim = rememberInfiniteTransition(label = "wiggle")
    val wiggleAngle by wiggleAnim.animateFloat(
        initialValue = -1.5f, targetValue = 1.5f,
        animationSpec = infiniteRepeatable(animation = tween(300, easing = LinearEasing), repeatMode = RepeatMode.Reverse), label = "wa",
    )

    // Use Ref for root position so it never goes stale during a drag.
    var rootBounds by remember { mutableStateOf(Rect.Zero) }

    // Use editMode as key so gesture detector switches when mode changes.
    // Do NOT use cell as key — that restarts the gesture on recomposition.
    Box(
        Modifier
            .onGloballyPositioned { rootBounds = it.boundsInRoot() }
            .graphicsLayer(rotationZ = if (editMode) wiggleAngle else 0f)
            .then(
                if (editMode) {
                    Modifier.pointerInput(editMode) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { localPos ->
                                onDragStart(Offset(rootBounds.left + localPos.x, rootBounds.top + localPos.y))
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                onDrag(Offset(rootBounds.left + change.position.x, rootBounds.top + change.position.y))
                            },
                            onDragEnd = onDragEnd,
                            onDragCancel = onDragCancel,
                        )
                    }.pointerInput(editMode) {
                        detectTapGestures(onTap = { onTap() })
                    }
                } else {
                    Modifier.pointerInput(Unit) {
                        detectTapGestures(onTap = { onTap() }, onLongPress = { onLongPress() })
                    }
                }
            ),
        Alignment.Center,
    ) {
        when (cell) {
            is GridCell.App -> resolveApp(cell.appKey)?.let { AppIconContent(it, shape, iconSizeDp, showLabel = showLabel, dimmed = dimmed, customLabel = customLabels[cell.appKey], badgeCount = badgeCount, badgeDotOnly = badgeDotOnly, iconShadow = iconShadow, labelSizeSp = labelSizeSp, grayscale = grayscale, labelWeight = labelWeight) }
            is GridCell.Folder -> FolderIconContent(cell, shape, resolveApp, iconSizeDp, showLabel = showLabel, dimmed = dimmed, badgeCount = folderBadgeCount)
            is GridCell.Widget -> { /* Rendered by overlay, skip */ }
        }
    }
}
