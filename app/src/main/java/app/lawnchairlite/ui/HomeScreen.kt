package app.lawnchairlite.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lawnchairlite.LauncherViewModel
import app.lawnchairlite.data.*
import kotlinx.coroutines.launch

/**
 * Lawnchair Lite v2.1.0 - Home Screen
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
 *   Fling threshold: 500 dp/s (Launcher3 uses ~1dp but has VelocityTracker;
 *     500dp/s is the sweet spot for Compose gesture detection without VelocityTracker)
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
    val colors = LocalLauncherColors.current
    val isDragging = drag != null
    val iconDp = settings.iconSize.dp.dp
    val dockCount = settings.dockCount
    val cols = settings.gridColumns; val rows = settings.gridRows; val pageSize = cols * rows
    val numPages = vm.numPages()
    val homeBounds = remember { mutableStateMapOf<Int, Rect>() }
    val dockBounds = remember { mutableStateMapOf<Int, Rect>() }
    var removeZoneBounds by remember { mutableStateOf(Rect.Zero) }
    var uninstallZoneBounds by remember { mutableStateOf(Rect.Zero) }
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { numPages })
    val currentPage by remember { derivedStateOf { pagerState.currentPage } }

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

            if (shouldOpen && !shouldClose) {
                // Commit open
                drawerProgress.animateTo(1f, spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = if (isFling) Spring.StiffnessMediumLow else Spring.StiffnessMedium,
                ))
            } else {
                // Commit close (or revert from partial open)
                drawerProgress.animateTo(0f, spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = if (isFling) Spring.StiffnessMediumLow else Spring.StiffnessMedium,
                ))
                vm.setSearch("")
            }
        }
    }

    // Keep ViewModel in sync for back-press handling
    LaunchedEffect(dpVal) {
        if (dpVal > 0.5f && !vm.drawerOpen.value) vm.openDrawer()
        else if (dpVal < 0.01f && vm.drawerOpen.value) vm.closeDrawer()
    }

    // ViewModel can request close externally (back press, home button)
    val vmDrawerOpen by vm.drawerOpen.collectAsState()
    LaunchedEffect(vmDrawerOpen) {
        if (!vmDrawerOpen && drawerProgress.value > 0.01f) {
            drawerProgress.animateTo(0f, spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMedium,
            ))
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
                        var velocityPxPerSec = 0f
                        var prevTime = 0L
                        detectVerticalDragGestures(
                            onDragStart = {
                                totalDrag = 0f; velocityPxPerSec = 0f
                                prevTime = System.nanoTime()
                            },
                            onDragEnd = {
                                if (drawerProgress.value > 0.01f) {
                                    // Was opening drawer - settle with tracked velocity
                                    // velocityPxPerSec is already positive for upward movement
                                    settleDrawer(velocityPxPerSec)
                                } else if (totalDrag > 40f) {
                                    // Swipe down on home (notifications, etc.)
                                    vm.executeGesture(settings.swipeDownAction)
                                }
                            },
                            onDragCancel = {
                                if (drawerProgress.value > 0f) settleDrawer(0f)
                            },
                        ) { _, amount ->
                            // If drawer is fully open, don't process home-layer drags
                            if (currentDrawerFullyOpen) return@detectVerticalDragGestures
                            totalDrag += amount
                            // Velocity: track instantaneous. Negate because
                            // negative amount (upward) = positive velocity (opening)
                            val now = System.nanoTime()
                            val dt = ((now - prevTime) / 1_000_000_000f).coerceAtLeast(0.001f)
                            velocityPxPerSec = -amount / dt
                            prevTime = now

                            // Upward swipe (amount < 0) increases progress
                            if (amount < 0f || drawerProgress.value > 0f) {
                                val delta = -amount / screenHeightPx
                                val newP = (drawerProgress.value + delta).coerceIn(0f, 1f)
                                scope.launch { drawerProgress.snapTo(newP) }
                            }
                        }
                    }
                }
                // ── Double-tap gesture ──
                .pointerInput(isDragging, settingsOpen) {
                    if (!isDragging && !settingsOpen) {
                        detectTapGestures(onDoubleTap = {
                            if (!currentDrawerVisible) vm.executeGesture(settings.doubleTapAction)
                        })
                    }
                }
        ) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
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
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { vm.openSettings() }, Modifier.size(36.dp)) {
                        Icon(Icons.Default.Settings, "Settings", tint = colors.text.copy(alpha = 0.4f), modifier = Modifier.size(16.dp))
                    }
                }

                if (settings.showClock && !isDragging && !editMode) AtAGlanceClock()

                val paddedGrid = remember(homeGrid, pageSize, numPages) { val t = numPages * pageSize; if (homeGrid.size >= t) homeGrid.take(t) else homeGrid + List(t - homeGrid.size) { null } }

                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth().weight(1f), userScrollEnabled = !isDragging) { page ->
                    val ps = page * pageSize
                    val pageCells = paddedGrid.subList(ps.coerceAtMost(paddedGrid.size), (ps + pageSize).coerceAtMost(paddedGrid.size))
                    LaunchedEffect(page) { if (page == currentPage) homeBounds.clear() }
                    Column(Modifier.fillMaxSize().padding(horizontal = 6.dp), verticalArrangement = Arrangement.SpaceEvenly) {
                        for (row in 0 until rows) Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                            for (col in 0 until cols) {
                                val li = row * cols + col; val gi = ps + li; val cell = pageCells.getOrNull(li)
                                val isHovered = !hoverDock && hoverIdx == gi && isDragging
                                val isDragSrc = drag?.source == DragSource.HOME && drag?.sourceIndex == gi

                                val hoverScale by animateFloatAsState(if (isHovered) 1.12f else 1f, spring(dampingRatio = 0.5f, stiffness = 400f), label = "hs$gi")
                                val hoverAlpha by animateFloatAsState(if (isHovered) 0.2f else 0f, tween(150), label = "ha$gi")

                                Box(
                                    Modifier.weight(1f).aspectRatio(0.85f)
                                        .onGloballyPositioned { if (page == currentPage) homeBounds[li] = it.boundsInRoot() }
                                        .graphicsLayer(scaleX = hoverScale, scaleY = hoverScale)
                                        .then(if (hoverAlpha > 0f) Modifier.clip(RoundedCornerShape(14.dp)).background(colors.accent.copy(alpha = hoverAlpha)) else Modifier),
                                    Alignment.Center,
                                ) {
                                    GridCellView(
                                        cell, settings.iconShape, iconDp, { vm.resolveApp(it) }, customLabels,
                                        isDragSrc, true, editMode,
                                        onTap = { when (cell) {
                                            is GridCell.App -> vm.resolveApp(cell.appKey)?.let { vm.launch(it) }
                                            is GridCell.Folder -> vm.openFolderView(cell, DragSource.HOME, gi)
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
                if (!isDragging) PageDots(numPages, currentPage, Modifier.padding(vertical = 6.dp))

                // Dock
                Column(Modifier.fillMaxWidth()) {
                    if (settings.showDockSearch && !isDragging) SearchPill(
                        onClick = { scope.launch { drawerProgress.animateTo(1f, spring(stiffness = Spring.StiffnessMedium)) } },
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 5.dp),
                    )
                    if (!isDragging) Box(
                        Modifier.fillMaxWidth()
                            .clickable { scope.launch { drawerProgress.animateTo(1f, spring(stiffness = Spring.StiffnessMedium)) } }
                            .padding(vertical = 3.dp),
                        Alignment.Center,
                    ) { Box(Modifier.width(32.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(colors.textSecondary.copy(alpha = 0.4f))) }
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)).background(colors.dock).padding(horizontal = 8.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        for (i in 0 until dockCount) {
                            val cell = dockGrid.getOrNull(i)
                            val isH = hoverDock && hoverIdx == i && isDragging
                            val isDS = drag?.source == DragSource.DOCK && drag?.sourceIndex == i
                            val dockHoverScale by animateFloatAsState(if (isH) 1.15f else 1f, spring(dampingRatio = 0.5f, stiffness = 400f), label = "dhs$i")
                            val dockHoverAlpha by animateFloatAsState(if (isH) 0.2f else 0f, tween(150), label = "dha$i")

                            Box(
                                Modifier.weight(1f).height(58.dp)
                                    .onGloballyPositioned { dockBounds[i] = it.boundsInRoot() }
                                    .graphicsLayer(scaleX = dockHoverScale, scaleY = dockHoverScale)
                                    .then(if (dockHoverAlpha > 0f) Modifier.clip(RoundedCornerShape(12.dp)).background(colors.accent.copy(alpha = dockHoverAlpha)) else Modifier),
                                Alignment.Center,
                            ) {
                                GridCellView(
                                    cell, settings.iconShape, iconDp, { vm.resolveApp(it) }, customLabels,
                                    isDS, false, editMode,
                                    onTap = { when (cell) {
                                        is GridCell.App -> vm.resolveApp(cell.appKey)?.let { vm.launch(it) }
                                        is GridCell.Folder -> vm.openFolderView(cell, DragSource.DOCK, i)
                                        null -> {}
                                    }},
                                    onLongPress = { if (cell != null) vm.showHomeMenu(cell, DragSource.DOCK, i) },
                                    onDragStart = { rp -> if (cell != null) vm.startDrag(cell, DragSource.DOCK, i, rp) },
                                    onDrag = { rp -> vm.updateDrag(rp); hitTest(rp) },
                                    onDragEnd = { vm.endDrag() },
                                    onDragCancel = { vm.cancelDrag() },
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.navigationBarsPadding())
            }

            if (isDragging) DragGhost(drag?.item, drag?.appInfo, settings.iconShape, dragOff, { vm.resolveApp(it) }, iconDp)
        }

        // ═════════════════════════════════════════════════════════════
        // DRAWER LAYER (always composed when visible, translation-based)
        // ═════════════════════════════════════════════════════════════
        if (drawerVisible) {
            AppDrawer(
                progress = dpVal,
                screenHeightPx = screenHeightPx,
                apps = allApps,
                searchQuery = search,
                shape = settings.iconShape,
                iconSizeDp = iconDp,
                columns = cols,
                onSearchChange = { vm.setSearch(it) },
                onAppClick = { app ->
                    vm.launch(app)
                    scope.launch { drawerProgress.animateTo(0f, tween(200)) }
                    vm.setSearch("")
                },
                onAppLongClick = { vm.showDrawerMenu(it) },
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

        val menu = homeMenu
        if (menu != null) HomeContextMenu(menuState = menu, shape = settings.iconShape, vm = vm, onDismiss = { vm.dismissHomeMenu() })

        val fState = openFolder
        if (fState != null) { val (folder, src, fi) = fState
            FolderOverlay(folder, settings.iconShape, iconDp, { vm.resolveApp(it) }, customLabels, onAppClick = { vm.launch(it); vm.closeFolderView() }, onRemoveApp = { k -> vm.removeFolderApp(src, fi, k) }, onReorder = { keys -> vm.reorderFolderApps(src, fi, keys) }, onRename = { vm.startFolderRename(src, fi, folder.name) }, onDismiss = { vm.closeFolderView() }) }

        val rn = folderRename
        if (rn != null) RenameDialog(rn.current, "Rename Folder", onConfirm = { vm.renameFolder(rn.source, rn.index, it); vm.closeFolderView() }, onDismiss = { vm.dismissFolderRename() })

        val le = labelEdit
        if (le != null) RenameDialog(le.current, "Rename Shortcut", onConfirm = { vm.saveCustomLabel(le.appKey, it) }, onDismiss = { vm.dismissLabelEdit() })

        val menuApp = drawerMenuApp
        if (menuApp != null) DrawerContextMenu(menuApp, settings.iconShape, onPinHome = { vm.pinToHome(menuApp); scope.launch { drawerProgress.animateTo(0f, tween(200)) }; vm.setSearch("") }, onPinDock = { vm.pinToDock(menuApp); scope.launch { drawerProgress.animateTo(0f, tween(200)) }; vm.setSearch("") }, onHide = { vm.hideApp(menuApp.key) }, onAppInfo = { vm.appInfo(menuApp); vm.dismissDrawerMenu() }, onUninstall = { vm.uninstall(menuApp); vm.dismissDrawerMenu() }, onDismiss = { vm.dismissDrawerMenu() })
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
            is GridCell.App -> resolveApp(cell.appKey)?.let { AppIconContent(it, shape, iconSizeDp, showLabel = showLabel, dimmed = dimmed, customLabel = customLabels[cell.appKey]) }
            is GridCell.Folder -> FolderIconContent(cell, shape, resolveApp, iconSizeDp, showLabel = showLabel, dimmed = dimmed)
        }
    }
}
