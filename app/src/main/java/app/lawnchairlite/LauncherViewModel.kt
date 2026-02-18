package app.lawnchairlite

import android.app.ActivityManager
import android.app.Application
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Toast
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.lawnchairlite.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * Lawnchair Lite v0.9.0 - ViewModel
 */
class LauncherViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx = app.applicationContext
    private val repo = AppRepository(app)
    val prefs = LauncherPrefs(app)
    val iconPackManager = IconPackManager(app)

    private val _allApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val allApps: StateFlow<List<AppInfo>> = _allApps.asStateFlow()
    private val _appMap = MutableStateFlow<Map<String, AppInfo>>(emptyMap())
    val settings = prefs.settings.stateIn(viewModelScope, SharingStarted.Eagerly, LauncherSettings())

    // Icon packs
    private val _availablePacks = MutableStateFlow<List<IconPackInfo>>(emptyList())
    val availablePacks: StateFlow<List<IconPackInfo>> = _availablePacks.asStateFlow()
    private val _iconPackLoading = MutableStateFlow(false)
    val iconPackLoading: StateFlow<Boolean> = _iconPackLoading.asStateFlow()

    private val _homeGrid = MutableStateFlow<List<GridCell?>>(emptyList())
    val homeGrid: StateFlow<List<GridCell?>> = _homeGrid.asStateFlow()
    private val _dockGrid = MutableStateFlow<List<GridCell?>>(List(5) { null })
    val dockGrid: StateFlow<List<GridCell?>> = _dockGrid.asStateFlow()
    private val _initialized = prefs.initialized.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _hiddenApps = MutableStateFlow<Set<String>>(emptySet())
    val hiddenApps: StateFlow<Set<String>> = _hiddenApps.asStateFlow()
    private val _customLabels = MutableStateFlow<Map<String, String>>(emptyMap())
    val customLabels: StateFlow<Map<String, String>> = _customLabels.asStateFlow()

    private val _search = MutableStateFlow("")
    val search: StateFlow<String> = _search.asStateFlow()
    val filteredApps: StateFlow<List<AppInfo>> = combine(_allApps, _search, _hiddenApps) { apps, q, hidden ->
        apps.filter { it.key !in hidden }.let { v -> if (q.isBlank()) v else v.filter { it.label.contains(q, ignoreCase = true) } }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _drawerOpen = MutableStateFlow(false)
    val drawerOpen: StateFlow<Boolean> = _drawerOpen.asStateFlow()
    private val _settingsOpen = MutableStateFlow(false)
    val settingsOpen: StateFlow<Boolean> = _settingsOpen.asStateFlow()

    private val _dragState = MutableStateFlow<DragState?>(null)
    val dragState: StateFlow<DragState?> = _dragState.asStateFlow()
    private val _dragOffset = MutableStateFlow(Offset.Zero)
    val dragOffset: StateFlow<Offset> = _dragOffset.asStateFlow()
    private val _hoverIndex = MutableStateFlow(-1)
    val hoverIndex: StateFlow<Int> = _hoverIndex.asStateFlow()
    private val _hoverDock = MutableStateFlow(false)
    val hoverDock: StateFlow<Boolean> = _hoverDock.asStateFlow()
    private val _hoverRemove = MutableStateFlow(false)
    val hoverRemove: StateFlow<Boolean> = _hoverRemove.asStateFlow()
    private val _hoverUninstall = MutableStateFlow(false)
    val hoverUninstall: StateFlow<Boolean> = _hoverUninstall.asStateFlow()

    private val _openFolder = MutableStateFlow<Triple<GridCell.Folder, DragSource, Int>?>(null)
    val openFolder: StateFlow<Triple<GridCell.Folder, DragSource, Int>?> = _openFolder.asStateFlow()
    data class FolderRename(val source: DragSource, val index: Int, val current: String)
    private val _folderRename = MutableStateFlow<FolderRename?>(null)
    val folderRename: StateFlow<FolderRename?> = _folderRename.asStateFlow()
    private val _drawerMenuApp = MutableStateFlow<AppInfo?>(null)
    val drawerMenuApp: StateFlow<AppInfo?> = _drawerMenuApp.asStateFlow()

    data class LabelEdit(val appKey: String, val current: String)
    private val _labelEdit = MutableStateFlow<LabelEdit?>(null)
    val labelEdit: StateFlow<LabelEdit?> = _labelEdit.asStateFlow()

    init {
        loadApps()
        discoverIconPacks()
        viewModelScope.launch { prefs.homeGrid.collect { if (it.isNotEmpty()) _homeGrid.value = it } }
        viewModelScope.launch { prefs.dockGrid.collect { if (it.isNotEmpty()) _dockGrid.value = it } }
        viewModelScope.launch { prefs.hiddenApps.collect { _hiddenApps.value = it } }
        viewModelScope.launch { prefs.customLabels.collect { _customLabels.value = it } }
        viewModelScope.launch {
            combine(_allApps, _initialized) { a, i -> a to i }.filter { it.first.isNotEmpty() && !it.second }
                .take(1).collect { (apps, _) -> autoPopulate(apps) }
        }
        viewModelScope.launch { _allApps.filter { it.isNotEmpty() }.drop(1).collect { cleanupStaleKeys(it) } }
        // Load saved icon pack on startup
        viewModelScope.launch {
            settings.filter { it.iconPack.isNotBlank() }.take(1).collect { s ->
                applyIconPack(s.iconPack)
            }
        }
    }

    fun loadApps() { viewModelScope.launch {
        val apps = repo.loadApps(iconPackManager)
        _allApps.value = apps; _appMap.value = apps.associateBy { it.key }
    }}
    fun resolveApp(key: String): AppInfo? = _appMap.value[key]
    fun getLabel(key: String): String? = _customLabels.value[key] ?: resolveApp(key)?.label
    fun launch(app: AppInfo) = repo.launchApp(app)
    fun appInfo(app: AppInfo) = repo.openAppInfo(app)
    fun uninstall(app: AppInfo) = repo.uninstallApp(app)
    fun setSearch(q: String) { _search.value = q }
    fun openDrawer() { _drawerOpen.value = true }
    fun closeDrawer() { _drawerOpen.value = false; _search.value = "" }
    fun openSettings() { _settingsOpen.value = true }
    fun closeSettings() { _settingsOpen.value = false }
    fun closeAllOverlays() { _drawerOpen.value = false; _settingsOpen.value = false; _openFolder.value = null; _drawerMenuApp.value = null; _folderRename.value = null; _labelEdit.value = null; _search.value = "" }
    fun hasOpenOverlay(): Boolean = _drawerOpen.value || _settingsOpen.value || _openFolder.value != null || _drawerMenuApp.value != null || _labelEdit.value != null

    // ── Icon Packs ───────────────────────────────────────────────────────

    private fun discoverIconPacks() { viewModelScope.launch { _availablePacks.value = iconPackManager.getInstalledPacks() } }

    fun setIconPack(packageName: String) { viewModelScope.launch {
        pref(LauncherPrefs.ICON_PACK, packageName)
        applyIconPack(packageName)
    }}

    fun clearIconPack() { viewModelScope.launch {
        pref(LauncherPrefs.ICON_PACK, "")
        iconPackManager.clearPack()
        loadApps()
        toast("System icons restored")
    }}

    /** Refresh the list of available icon packs (e.g. after installing a new one). */
    fun refreshIconPacks() { discoverIconPacks() }

    private suspend fun applyIconPack(packageName: String) {
        if (packageName.isBlank()) { iconPackManager.clearPack(); loadApps(); return }
        _iconPackLoading.value = true
        val ok = iconPackManager.loadPack(packageName)
        if (ok) {
            loadApps()
            val count = iconPackManager.mappedCount()
            toast("Icon pack applied ($count icons)")
        } else {
            toast("Failed to load icon pack")
        }
        _iconPackLoading.value = false
    }

    // ── Gestures ─────────────────────────────────────────────────────────

    fun executeGesture(action: GestureAction) = when (action) {
        GestureAction.NONE -> {}
        GestureAction.LOCK_SCREEN -> lockScreen()
        GestureAction.NOTIFICATION_SHADE -> expandNotifications()
        GestureAction.APP_DRAWER -> openDrawer()
        GestureAction.SETTINGS -> openSettings()
        GestureAction.KILL_APPS -> killBackgroundApps()
    }

    fun lockScreen() { runCatching {
        val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        if (dpm.isAdminActive(ComponentName(ctx, AdminReceiver::class.java))) dpm.lockNow()
        else toast("Enable Device Admin in Settings to lock screen")
    }}

    fun isDeviceAdminEnabled(): Boolean = runCatching {
        (ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager).isAdminActive(ComponentName(ctx, AdminReceiver::class.java))
    }.getOrDefault(false)

    fun requestDeviceAdmin() { runCatching {
        ctx.startActivity(Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, ComponentName(ctx, AdminReceiver::class.java))
            putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Required for double-tap to lock screen.")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
    }}

    @Suppress("DEPRECATION")
    fun expandNotifications() { runCatching { ctx.getSystemService("statusbar").javaClass.getMethod("expandNotificationsPanel").invoke(ctx.getSystemService("statusbar")) } }

    fun killBackgroundApps() { runCatching {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val pkgs = am.runningAppProcesses?.flatMap { it.pkgList.toList() }?.distinct() ?: emptyList()
        var n = 0; pkgs.forEach { if (it != ctx.packageName) { am.killBackgroundProcesses(it); n++ } }
        toast("Cleared $n background apps")
    }}

    fun openWallpaperPicker() { runCatching { ctx.startActivity(Intent(Intent.ACTION_SET_WALLPAPER).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }) } }

    // ── Custom Labels ────────────────────────────────────────────────────

    fun startLabelEdit(appKey: String) { _labelEdit.value = LabelEdit(appKey, _customLabels.value[appKey] ?: resolveApp(appKey)?.label ?: "") }
    fun dismissLabelEdit() { _labelEdit.value = null }
    fun saveCustomLabel(appKey: String, label: String) { viewModelScope.launch {
        val orig = resolveApp(appKey)?.label ?: ""; val map = _customLabels.value.toMutableMap()
        if (label.isBlank() || label == orig) map.remove(appKey) else map[appKey] = label.trim()
        _customLabels.value = map; prefs.saveCustomLabels(map); _labelEdit.value = null
    }}

    // ── Hidden Apps ──────────────────────────────────────────────────────

    fun hideApp(key: String) { viewModelScope.launch { val u = _hiddenApps.value + key; _hiddenApps.value = u; prefs.saveHidden(u); toast("Hidden from drawer"); _drawerMenuApp.value = null } }
    fun unhideApp(key: String) { viewModelScope.launch { val u = _hiddenApps.value - key; _hiddenApps.value = u; prefs.saveHidden(u) } }

    // ── Page Helpers ─────────────────────────────────────────────────────

    fun pageSize(): Int { val s = settings.value; return s.gridColumns * s.gridRows }
    fun numPages(): Int { val ps = pageSize(); return if (ps <= 0) 1 else max(1, (_homeGrid.value.size + ps - 1) / ps) }

    private fun padGrid(grid: List<GridCell?>, ps: Int): List<GridCell?> {
        val t = max(ps, ((grid.size + ps - 1) / ps) * ps)
        return if (grid.size >= t) grid.take(t) else grid + List(t - grid.size) { null }
    }
    private fun trimGrid(grid: List<GridCell?>, ps: Int): List<GridCell?> {
        if (ps <= 0) return grid; val p = padGrid(grid, ps).toMutableList()
        while (p.size > ps && p.takeLast(ps).all { it == null }) repeat(ps) { p.removeLastOrNull() }
        return p
    }

    // ── Stale Cleanup ────────────────────────────────────────────────────

    private suspend fun cleanupStaleKeys(apps: List<AppInfo>) {
        val valid = apps.map { it.key }.toSet(); var c = false
        val home = _homeGrid.value.toMutableList()
        for (i in home.indices) { val r = cleanCell(home[i], valid); if (r !== home[i]) { home[i] = r; c = true } }
        if (c) { _homeGrid.value = home; prefs.saveHome(home) }
        c = false; val dock = _dockGrid.value.toMutableList()
        for (i in dock.indices) { val r = cleanCell(dock[i], valid); if (r !== dock[i]) { dock[i] = r; c = true } }
        if (c) { _dockGrid.value = dock; prefs.saveDock(dock) }
    }
    private fun cleanCell(cell: GridCell?, valid: Set<String>): GridCell? = when (cell) {
        is GridCell.App -> if (cell.appKey in valid) cell else null
        is GridCell.Folder -> { val f = cell.appKeys.filter { it in valid }; when { f.isEmpty() -> null; f.size == 1 -> GridCell.App(f[0]); f.size != cell.appKeys.size -> cell.copy(appKeys = f); else -> cell } }
        null -> null
    }

    // ── Folder ───────────────────────────────────────────────────────────

    fun openFolderView(f: GridCell.Folder, src: DragSource, index: Int) { _openFolder.value = Triple(f, src, index) }
    fun closeFolderView() { _openFolder.value = null }
    fun startFolderRename(src: DragSource, index: Int, current: String) { _folderRename.value = FolderRename(src, index, current) }
    fun dismissFolderRename() { _folderRename.value = null }
    fun renameFolder(src: DragSource, index: Int, newName: String) { viewModelScope.launch {
        val g = gridForSource(src).toMutableList(); val c = g.getOrNull(index)
        if (c is GridCell.Folder) {
            val updated = c.copy(name = newName)
            g[index] = updated; saveGrid(src, g)
            // BUG FIX: update live folder overlay with new name
            _openFolder.value?.let { (_, oSrc, oIdx) ->
                if (oSrc == src && oIdx == index) _openFolder.value = Triple(updated, src, index)
            }
        }
        _folderRename.value = null
    }}
    fun removeFolderApp(src: DragSource, idx: Int, key: String) { viewModelScope.launch {
        val g = gridForSource(src).toMutableList(); val c = g.getOrNull(idx) as? GridCell.Folder ?: return@launch
        val f = c.appKeys.filter { it != key }; g[idx] = when { f.isEmpty() -> null; f.size == 1 -> GridCell.App(f[0]); else -> c.copy(appKeys = f) }
        saveGrid(src, g); val nc = g.getOrNull(idx); _openFolder.value = if (nc is GridCell.Folder) Triple(nc, src, idx) else null
    }}
    fun reorderFolderApps(src: DragSource, idx: Int, newKeys: List<String>) { viewModelScope.launch {
        val g = gridForSource(src).toMutableList(); val c = g.getOrNull(idx) as? GridCell.Folder ?: return@launch
        g[idx] = c.copy(appKeys = newKeys); saveGrid(src, g)
        _openFolder.value = Triple(g[idx] as GridCell.Folder, src, idx)
    }}

    // ── Drawer Menu ──────────────────────────────────────────────────────

    fun showDrawerMenu(app: AppInfo) { _drawerMenuApp.value = app }
    fun dismissDrawerMenu() { _drawerMenuApp.value = null }

    fun pinToHome(app: AppInfo) { viewModelScope.launch {
        val ps = pageSize(); val grid = padGrid(_homeGrid.value, ps).toMutableList()
        if (grid.any { it is GridCell.App && it.appKey == app.key }) { toast("Already on home"); _drawerMenuApp.value = null; return@launch }
        var i = grid.indexOfFirst { it == null }; if (i < 0) { grid.addAll(List(ps) { null }); i = grid.indexOfFirst { it == null } }
        if (i >= 0) { grid[i] = GridCell.App(app.key); _homeGrid.value = grid; prefs.saveHome(grid); toast("Added to home") }
        _drawerMenuApp.value = null
    }}

    fun pinToDock(app: AppInfo) { viewModelScope.launch {
        val dc = settings.value.dockCount; val dock = _dockGrid.value.toMutableList(); while (dock.size < dc) dock.add(null)
        if (dock.any { it is GridCell.App && it.appKey == app.key }) { toast("Already in dock"); _drawerMenuApp.value = null; return@launch }
        val i = dock.indexOfFirst { it == null }; if (i < 0) { toast("Dock full"); _drawerMenuApp.value = null; return@launch }
        dock[i] = GridCell.App(app.key); _dockGrid.value = dock; prefs.saveDock(dock); toast("Added to dock"); _drawerMenuApp.value = null
    }}

    // ── Drag and Drop ────────────────────────────────────────────────────

    fun startDrag(item: GridCell, source: DragSource, index: Int, startPos: Offset) {
        val info = if (item is GridCell.App) resolveApp(item.appKey) else null
        _dragState.value = DragState(item, source, index, info)
        _dragOffset.value = startPos; _hoverIndex.value = -1; _hoverRemove.value = false; _hoverUninstall.value = false; _hoverDock.value = false; vibrate()
    }
    fun updateDrag(pos: Offset) { _dragOffset.value = pos }
    fun setHover(index: Int, isDock: Boolean) { _hoverIndex.value = index; _hoverDock.value = isDock; _hoverRemove.value = false; _hoverUninstall.value = false }
    fun setHoverRemove(h: Boolean) { _hoverRemove.value = h; _hoverUninstall.value = false; if (h) { _hoverIndex.value = -1; _hoverDock.value = false } }
    fun setHoverUninstall(h: Boolean) { _hoverUninstall.value = h; _hoverRemove.value = false; if (h) { _hoverIndex.value = -1; _hoverDock.value = false } }
    fun cancelDrag() { _dragState.value = null; _hoverIndex.value = -1; _hoverRemove.value = false; _hoverUninstall.value = false; _hoverDock.value = false }

    fun endDrag() {
        val drag = _dragState.value ?: return; val hi = _hoverIndex.value; val isDock = _hoverDock.value
        viewModelScope.launch {
            when {
                _hoverUninstall.value -> { if (drag.item is GridCell.App) { clearSourceCell(drag); drag.appInfo?.let { uninstall(it) } } }
                _hoverRemove.value -> clearSourceCell(drag)
                hi >= 0 && isDock -> dropOnGrid(drag, hi, DragSource.DOCK)
                hi >= 0 -> dropOnGrid(drag, hi, DragSource.HOME)
            }
            clearDragState()
        }
    }

    private suspend fun dropOnGrid(drag: DragState, ti: Int, target: DragSource) {
        val tg = gridForSource(target).toMutableList(); while (tg.size <= ti) tg.add(null)
        val ex = tg[ti]; val same = drag.source == target

        when (val item = drag.item) {
            is GridCell.Folder -> {
                if (ex == null) { if (same && drag.sourceIndex in tg.indices) tg[drag.sourceIndex] = null; tg[ti] = item; saveTrimmed(target, tg); if (!same) clearSourceCell(drag) }
                else if (same && drag.sourceIndex in tg.indices) { tg[ti] = item; tg[drag.sourceIndex] = ex; saveTrimmed(target, tg) }
                else if (!same) { tg[ti] = item; saveTrimmed(target, tg); clearSourceCell(drag) }
            }
            is GridCell.App -> {
                when {
                    ex == null -> { if (same && drag.sourceIndex in tg.indices) tg[drag.sourceIndex] = null; tg[ti] = item; saveTrimmed(target, tg); if (!same) clearSourceCell(drag) }
                    ex is GridCell.App && ex.appKey == item.appKey -> {}
                    ex is GridCell.App -> { if (same && drag.sourceIndex in tg.indices) tg[drag.sourceIndex] = null; tg[ti] = GridCell.Folder(suggestFolderName(ex.appKey, item.appKey), listOf(ex.appKey, item.appKey)); saveTrimmed(target, tg); if (!same) clearSourceCell(drag) }
                    ex is GridCell.Folder -> { if (item.appKey !in ex.appKeys) { if (same && drag.sourceIndex in tg.indices) tg[drag.sourceIndex] = null; tg[ti] = ex.copy(appKeys = ex.appKeys + item.appKey); saveTrimmed(target, tg); if (!same) clearSourceCell(drag) } }
                }
            }
        }
    }
    private suspend fun saveTrimmed(src: DragSource, grid: List<GridCell?>) { saveGrid(src, if (src == DragSource.HOME) trimGrid(grid, pageSize()) else grid) }
    private suspend fun clearSourceCell(drag: DragState) { if (drag.source == DragSource.DRAWER) return; val g = gridForSource(drag.source).toMutableList(); if (drag.sourceIndex in g.indices) { g[drag.sourceIndex] = null; saveTrimmed(drag.source, g) } }
    private fun clearDragState() { _dragState.value = null; _hoverIndex.value = -1; _hoverRemove.value = false; _hoverUninstall.value = false; _hoverDock.value = false }
    private fun gridForSource(s: DragSource): List<GridCell?> = when (s) { DragSource.HOME -> _homeGrid.value; DragSource.DOCK -> _dockGrid.value; DragSource.DRAWER -> emptyList() }
    private suspend fun saveGrid(s: DragSource, g: List<GridCell?>) = when (s) { DragSource.HOME -> { _homeGrid.value = g; prefs.saveHome(g) }; DragSource.DOCK -> { _dockGrid.value = g; prefs.saveDock(g) }; DragSource.DRAWER -> {} }

    private fun suggestFolderName(k1: String, k2: String): String {
        val b = listOf(k1.substringBefore("/"), k2.substringBefore("/"))
        return when { b.any { "messaging" in it || "dialer" in it || "contacts" in it || "whatsapp" in it } -> "Social"; b.any { "camera" in it || "photos" in it || "gallery" in it } -> "Photos"; b.any { "chrome" in it || "browser" in it || "firefox" in it } -> "Internet"; b.any { "music" in it || "spotify" in it || "youtube" in it } -> "Media"; b.any { "settings" in it || "calculator" in it || "clock" in it } -> "Tools"; b.any { "game" in it } -> "Games"; else -> "Folder" }
    }

    // ── Settings ─────────────────────────────────────────────────────────

    fun setTheme(t: ThemeMode) = pref(LauncherPrefs.THEME, t.name)
    fun setShape(s: IconShape) = pref(LauncherPrefs.ICON_SHAPE, s.name)
    fun setIconSize(s: IconSize) = pref(LauncherPrefs.ICON_SIZE, s.name)
    fun setShowClock(v: Boolean) = pref(LauncherPrefs.SHOW_CLOCK, v)
    fun setShowDockSearch(v: Boolean) = pref(LauncherPrefs.SHOW_DOCK_SEARCH, v)
    fun setDoubleTapAction(a: GestureAction) = pref(LauncherPrefs.DOUBLE_TAP_ACTION, a.name)
    fun setSwipeDownAction(a: GestureAction) = pref(LauncherPrefs.SWIPE_DOWN_ACTION, a.name)

    fun setGridCols(c: Int) { viewModelScope.launch { val old = settings.value.gridColumns; prefs.set(LauncherPrefs.GRID_COLS, c); if (c != old) reflowGrid(c, settings.value.gridRows) } }
    fun setGridRows(r: Int) { viewModelScope.launch { val old = settings.value.gridRows; prefs.set(LauncherPrefs.GRID_ROWS, r); if (r != old) reflowGrid(settings.value.gridColumns, r) } }
    fun setDockCount(c: Int) { viewModelScope.launch {
        prefs.set(LauncherPrefs.DOCK_COUNT, c); val dock = _dockGrid.value.toMutableList()
        while (dock.size < c) dock.add(null)
        if (dock.size > c) { val kept = dock.take(c).toMutableList(); dock.drop(c).filterNotNull().forEach { item -> val i = kept.indexOfFirst { it == null }; if (i >= 0) kept[i] = item }; _dockGrid.value = kept; prefs.saveDock(kept) }
        else { _dockGrid.value = dock; prefs.saveDock(dock) }
    }}

    private suspend fun reflowGrid(cols: Int, rows: Int) {
        val ps = cols * rows; val nn = _homeGrid.value.filterNotNull()
        val total = max(ps, ((nn.size + ps - 1) / ps) * ps); val g = MutableList<GridCell?>(total) { null }
        nn.forEachIndexed { i, c -> if (i < total) g[i] = c }; _homeGrid.value = g; prefs.saveHome(g)
    }

    suspend fun exportBackup(): String = prefs.exportBackup()
    suspend fun importBackup(json: String): Boolean {
        val ok = prefs.importBackup(json)
        if (ok) {
            toast("Layout restored")
            // Re-apply icon pack from restored settings
            val restored = prefs.settings.first()
            if (restored.iconPack.isNotBlank()) applyIconPack(restored.iconPack) else loadApps()
        } else toast("Restore failed")
        return ok
    }

    private fun <T> pref(key: androidx.datastore.preferences.core.Preferences.Key<T>, v: T) { viewModelScope.launch { prefs.set(key, v) } }
    private fun vibrate() { runCatching { val v = ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return; if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) v.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE)) else @Suppress("DEPRECATION") v.vibrate(30) } }
    private fun toast(msg: String) { Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show() }

    private suspend fun autoPopulate(apps: List<AppInfo>) {
        val s = settings.value; val gs = s.gridColumns * s.gridRows; val dc = s.dockCount
        val dock = MutableList<GridCell?>(dc) { null }; val used = mutableSetOf<String>(); var di = 0
        for (pkg in DEFAULT_DOCK_PKGS) { if (di >= dc) break; apps.find { it.packageName == pkg && it.key !in used }?.let { dock[di++] = GridCell.App(it.key); used.add(it.key) } }
        for (a in apps) { if (di >= dc) break; if (a.key !in used) { dock[di++] = GridCell.App(a.key); used.add(a.key) } }
        val home = MutableList<GridCell?>(gs) { null }; var hi = 0
        for (pkg in DEFAULT_HOME_PKGS) { if (hi >= gs) break; apps.find { it.packageName == pkg && it.key !in used }?.let { home[hi++] = GridCell.App(it.key); used.add(it.key) } }
        for (a in apps) { if (hi >= gs / 2) break; if (a.key !in used && home.none { it is GridCell.App && it.appKey == a.key }) { home[hi++] = GridCell.App(a.key); used.add(a.key) } }
        _homeGrid.value = home; _dockGrid.value = dock; prefs.saveHome(home); prefs.saveDock(dock); prefs.markInitialized()
    }
}
