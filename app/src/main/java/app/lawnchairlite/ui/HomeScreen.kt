package app.lawnchairlite.ui

import androidx.compose.animation.*
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import app.lawnchairlite.LauncherViewModel
import app.lawnchairlite.data.*

/**
 * Lawnchair Lite v0.9.0 - Home Screen
 */
@Composable
fun HomeScreen(vm: LauncherViewModel) {
    val settings by vm.settings.collectAsState()
    val homeGrid by vm.homeGrid.collectAsState()
    val dockGrid by vm.dockGrid.collectAsState()
    val drawerOpen by vm.drawerOpen.collectAsState()
    val settingsOpen by vm.settingsOpen.collectAsState()
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

    fun hitTest(pos: Offset) {
        if (removeZoneBounds != Rect.Zero && removeZoneBounds.contains(pos)) { vm.setHoverRemove(true); return }
        if (uninstallZoneBounds != Rect.Zero && uninstallZoneBounds.contains(pos)) { if (drag?.appInfo?.isSystemApp != true) { vm.setHoverUninstall(true); return } }
        vm.setHoverRemove(false); vm.setHoverUninstall(false)
        for ((i, b) in dockBounds) { if (b.contains(pos)) { vm.setHover(i, true); return } }
        for ((li, b) in homeBounds) { if (b.contains(pos)) { vm.setHover(currentPage * pageSize + li, false); return } }
        vm.setHover(-1, false)
    }

    Box(
        Modifier.fillMaxSize()
            .pointerInput(drawerOpen, isDragging, settingsOpen) {
                if (!drawerOpen && !isDragging && !settingsOpen) {
                    detectVerticalDragGestures { _, amount ->
                        if (amount < -30f) vm.openDrawer()
                        else if (amount > 40f) vm.executeGesture(settings.swipeDownAction)
                    }
                }
            }
            .pointerInput(drawerOpen, isDragging, settingsOpen) {
                if (!drawerOpen && !isDragging && !settingsOpen) {
                    detectTapGestures(onDoubleTap = { vm.executeGesture(settings.doubleTapAction) })
                }
            }
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            AnimatedVisibility(isDragging, enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(), exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()) {
                Row(Modifier.fillMaxWidth()) {
                    Box(Modifier.weight(1f).onGloballyPositioned { removeZoneBounds = it.boundsInRoot() }) { RemoveZone(hoverRemove) }
                    Box(Modifier.weight(1f).onGloballyPositioned { uninstallZoneBounds = it.boundsInRoot() }) { UninstallZone(hoverUninstall, drag?.appInfo?.isSystemApp == true) }
                }
            }
            if (!isDragging) Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f)); IconButton(onClick = { vm.openSettings() }, Modifier.size(36.dp)) { Icon(Icons.Default.Settings, "Settings", tint = colors.text.copy(alpha = 0.4f), modifier = Modifier.size(16.dp)) }
            }
            if (settings.showClock && !isDragging) AtAGlanceClock()

            val paddedGrid = remember(homeGrid, pageSize, numPages) { val t = numPages * pageSize; if (homeGrid.size >= t) homeGrid.take(t) else homeGrid + List(t - homeGrid.size) { null } }

            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth().weight(1f), userScrollEnabled = !isDragging) { page ->
                val ps = page * pageSize
                val pageCells = paddedGrid.subList(ps.coerceAtMost(paddedGrid.size), (ps + pageSize).coerceAtMost(paddedGrid.size))
                LaunchedEffect(page) { if (page == currentPage) homeBounds.clear() }
                Column(Modifier.fillMaxSize().padding(horizontal = 6.dp), verticalArrangement = Arrangement.SpaceEvenly) {
                    for (row in 0 until rows) Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        for (col in 0 until cols) {
                            val li = row * cols + col; val gi = ps + li; val cell = pageCells.getOrNull(li)
                            val isHovered = !hoverDock && hoverIdx == gi && isDragging; val isDragSrc = drag?.source == DragSource.HOME && drag?.sourceIndex == gi
                            Box(Modifier.weight(1f).aspectRatio(0.85f).onGloballyPositioned { if (page == currentPage) homeBounds[li] = it.boundsInRoot() }
                                .then(if (isHovered) Modifier.clip(RoundedCornerShape(14.dp)).background(colors.accent.copy(alpha = 0.15f)) else Modifier), Alignment.Center) {
                                GridCellView(cell, settings.iconShape, iconDp, { vm.resolveApp(it) }, customLabels, isDragSrc, true,
                                    onTap = { when (cell) { is GridCell.App -> vm.resolveApp(cell.appKey)?.let { vm.launch(it) }; is GridCell.Folder -> vm.openFolderView(cell, DragSource.HOME, gi); null -> {} } },
                                    onDragStart = { rp -> if (cell != null) vm.startDrag(cell, DragSource.HOME, gi, rp) },
                                    onDrag = { rp -> vm.updateDrag(rp); hitTest(rp) }, onDragEnd = { vm.endDrag() }, onDragCancel = { vm.cancelDrag() })
                            }
                        }
                    }
                }
            }
            if (!isDragging) PageDots(numPages, currentPage, Modifier.padding(vertical = 6.dp))

            Column(Modifier.fillMaxWidth()) {
                if (settings.showDockSearch && !isDragging) SearchPill(onClick = { vm.openDrawer() }, modifier = Modifier.padding(horizontal = 18.dp, vertical = 5.dp))
                if (!isDragging) Box(Modifier.fillMaxWidth().clickable { vm.openDrawer() }.padding(vertical = 3.dp), Alignment.Center) { Box(Modifier.width(32.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(colors.textSecondary.copy(alpha = 0.4f))) }
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)).background(colors.dock).padding(horizontal = 8.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    for (i in 0 until dockCount) {
                        val cell = dockGrid.getOrNull(i); val isH = hoverDock && hoverIdx == i && isDragging; val isDS = drag?.source == DragSource.DOCK && drag?.sourceIndex == i
                        Box(Modifier.weight(1f).height(58.dp).onGloballyPositioned { dockBounds[i] = it.boundsInRoot() }
                            .then(if (isH) Modifier.clip(RoundedCornerShape(12.dp)).background(colors.accent.copy(alpha = 0.15f)) else Modifier), Alignment.Center) {
                            GridCellView(cell, settings.iconShape, iconDp, { vm.resolveApp(it) }, customLabels, isDS, false,
                                onTap = { when (cell) { is GridCell.App -> vm.resolveApp(cell.appKey)?.let { vm.launch(it) }; is GridCell.Folder -> vm.openFolderView(cell, DragSource.DOCK, i); null -> {} } },
                                onDragStart = { rp -> if (cell != null) vm.startDrag(cell, DragSource.DOCK, i, rp) },
                                onDrag = { rp -> vm.updateDrag(rp); hitTest(rp) }, onDragEnd = { vm.endDrag() }, onDragCancel = { vm.cancelDrag() })
                        }
                    }
                }
            }
            Spacer(Modifier.navigationBarsPadding())
        }

        if (isDragging) DragGhost(drag?.item, drag?.appInfo, settings.iconShape, dragOff, { vm.resolveApp(it) }, iconDp)

        // Overlays
        AppDrawer(visible = drawerOpen, apps = allApps, searchQuery = search, shape = settings.iconShape, iconSizeDp = iconDp, columns = cols,
            onSearchChange = { vm.setSearch(it) }, onAppClick = { vm.launch(it); vm.closeDrawer() }, onAppLongClick = { vm.showDrawerMenu(it) }, onClose = { vm.closeDrawer() })

        SettingsPanel(visible = settingsOpen, settings = settings, vm = vm, onClose = { vm.closeSettings() })

        val fState = openFolder
        if (fState != null) { val (folder, src, fi) = fState
            FolderOverlay(folder, settings.iconShape, iconDp, { vm.resolveApp(it) }, customLabels, onAppClick = { vm.launch(it); vm.closeFolderView() }, onRemoveApp = { k -> vm.removeFolderApp(src, fi, k) }, onReorder = { keys -> vm.reorderFolderApps(src, fi, keys) }, onRename = { vm.startFolderRename(src, fi, folder.name) }, onDismiss = { vm.closeFolderView() }) }

        val rn = folderRename
        if (rn != null) RenameDialog(rn.current, "Rename Folder", onConfirm = { vm.renameFolder(rn.source, rn.index, it); vm.closeFolderView() }, onDismiss = { vm.dismissFolderRename() })

        val le = labelEdit
        if (le != null) RenameDialog(le.current, "Rename Shortcut", onConfirm = { vm.saveCustomLabel(le.appKey, it) }, onDismiss = { vm.dismissLabelEdit() })

        val menuApp = drawerMenuApp
        if (menuApp != null) DrawerContextMenu(menuApp, settings.iconShape, onPinHome = { vm.pinToHome(menuApp); vm.closeDrawer() }, onPinDock = { vm.pinToDock(menuApp); vm.closeDrawer() }, onHide = { vm.hideApp(menuApp.key) }, onAppInfo = { vm.appInfo(menuApp); vm.dismissDrawerMenu() }, onUninstall = { vm.uninstall(menuApp); vm.dismissDrawerMenu() }, onDismiss = { vm.dismissDrawerMenu() })
    }
}

@Composable
private fun GridCellView(
    cell: GridCell?, shape: IconShape, iconSizeDp: androidx.compose.ui.unit.Dp,
    resolveApp: (String) -> AppInfo?, customLabels: Map<String, String>,
    dimmed: Boolean, showLabel: Boolean, onTap: () -> Unit,
    onDragStart: (Offset) -> Unit, onDrag: (Offset) -> Unit, onDragEnd: () -> Unit, onDragCancel: () -> Unit,
) {
    if (cell == null) return
    var crp by remember { mutableStateOf(Offset.Zero) }
    Box(Modifier.onGloballyPositioned { crp = Offset(it.boundsInRoot().left, it.boundsInRoot().top) }
        .pointerInput(cell) { detectDragGesturesAfterLongPress(onDragStart = { onDragStart(crp + it) }, onDrag = { ch, _ -> ch.consume(); onDrag(crp + ch.position) }, onDragEnd = onDragEnd, onDragCancel = onDragCancel) }
        .pointerInput(cell) { detectTapGestures(onTap = { onTap() }) }, Alignment.Center) {
        when (cell) {
            is GridCell.App -> resolveApp(cell.appKey)?.let { AppIconContent(it, shape, iconSizeDp, showLabel = showLabel, dimmed = dimmed, customLabel = customLabels[cell.appKey]) }
            is GridCell.Folder -> FolderIconContent(cell, shape, resolveApp, iconSizeDp, showLabel = showLabel, dimmed = dimmed)
        }
    }
}
