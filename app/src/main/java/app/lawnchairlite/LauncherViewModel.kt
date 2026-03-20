package app.lawnchairlite

import android.app.ActivityManager
import android.app.Application
import android.app.admin.DevicePolicyManager
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.ContactsContract
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.lawnchairlite.data.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.max

/**
 * Lawnchair Lite v2.12.0 - ViewModel
 *
 * v2.3.0 additions:
 * - Drawer sort (name, most used, recently installed)
 * - Label style (shown, hidden, home only, drawer only)
 * - Themed icons (Android 13+)
 * - At-a-Glance battery + next alarm
 * - Search web fallback
 * - App launch animation support
 *
 * v2.2.0 additions:
 * - Notification badge counts via NotificationListener companion StateFlow
 * - App shortcuts via ShortcutRepository (LauncherApps API)
 * - Wallpaper dimming setting
 * - App usage tracking for "Recent" section in drawer
 * - Search matches package name in addition to label
 */
class LauncherViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        private const val TAG = "LauncherVM"
        private const val DEBOUNCE_MS = 300L
        private const val MAX_RECENT_APPS = 8
        private const val WIDGET_HOST_ID = 1024
    }

    private val ctx = app.applicationContext
    private val repo = AppRepository(app)
    val prefs = LauncherPrefs(app)
    val iconPackManager = IconPackManager(app)
    val shortcutRepo = ShortcutRepository(app)
    val widgetHost = AppWidgetHost(app, WIDGET_HOST_ID)
    val widgetManager: AppWidgetManager = AppWidgetManager.getInstance(app)

    private val _allApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val allApps: StateFlow<List<AppInfo>> = _allApps.asStateFlow()
    private val _appMap = MutableStateFlow<Map<String, AppInfo>>(emptyMap())
    val settings = prefs.settings.stateIn(viewModelScope, SharingStarted.Eagerly, LauncherSettings())

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

    // App usage tracking (package/activity key -> last launch timestamp)
    private val _appUsage = MutableStateFlow<Map<String, Long>>(emptyMap())
    val appUsage: StateFlow<Map<String, Long>> = _appUsage.asStateFlow()

    // Notification badge counts (package -> count)
    val notifCounts: StateFlow<Map<String, Int>> = NotificationListener.counts

    // App shortcuts state
    private val _shortcuts = MutableStateFlow<List<AppShortcut>>(emptyList())
    val shortcuts: StateFlow<List<AppShortcut>> = _shortcuts.asStateFlow()

    // Widget state
    private val _widgets = MutableStateFlow<List<WidgetInfo>>(emptyList())
    val widgets: StateFlow<List<WidgetInfo>> = _widgets.asStateFlow()
    private val _widgetPickerOpen = MutableStateFlow(false)
    val widgetPickerOpen: StateFlow<Boolean> = _widgetPickerOpen.asStateFlow()

    // Contact search results
    data class ContactResult(val name: String, val number: String?, val lookupUri: String?)
    private val _contactResults = MutableStateFlow<List<ContactResult>>(emptyList())
    val contactResults: StateFlow<List<ContactResult>> = _contactResults.asStateFlow()

    // Search history
    private val _searchHistory = MutableStateFlow<List<String>>(emptyList())
    val searchHistory: StateFlow<List<String>> = _searchHistory.asStateFlow()

    // Suggestion usage (time-bucket:appKey -> launch count)
    private val _suggestionUsage = MutableStateFlow<Map<String, Int>>(emptyMap())

    private val _search = MutableStateFlow("")
    val search: StateFlow<String> = _search.asStateFlow()

    // Inline calculator: evaluates math expressions typed into search
    val calculatorResult: StateFlow<String?> = _search.map { query ->
        tryEvaluate(query) ?: tryConvertUnit(query)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val filteredApps: StateFlow<List<AppInfo>> = combine(_allApps, _search, _hiddenApps, settings, _appUsage) { args ->
        @Suppress("UNCHECKED_CAST")
        val apps = args[0] as List<AppInfo>
        val q = args[1] as String
        val hidden = args[2] as Set<String>
        val s = args[3] as LauncherSettings
        val usage = args[4] as Map<String, Long>
        val visible = apps.filter { it.key !in hidden }
        if (q.isBlank()) {
            when (s.drawerSort) {
                DrawerSort.NAME -> visible.sortedBy { it.label.lowercase() }
                DrawerSort.MOST_USED -> visible.sortedByDescending { usage[it.key] ?: 0L }
                DrawerSort.RECENT_INSTALL -> visible.sortedByDescending { it.firstInstallTime }
            }
        } else {
            // Fuzzy search with relevance scoring
            visible.mapNotNull { app ->
                val score = searchScore(app.label, app.packageName, q)
                if (score > 0) app to score else null
            }.sortedByDescending { it.second }.map { it.first }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Categorized apps for drawer tabs
    val categorizedApps: StateFlow<Map<DrawerCategory, List<AppInfo>>> = filteredApps.map { apps ->
        AppCategorizer.categorizeAll(apps)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private val _selectedCategory = MutableStateFlow(DrawerCategory.ALL)
    val selectedCategory: StateFlow<DrawerCategory> = _selectedCategory.asStateFlow()
    fun setSelectedCategory(c: DrawerCategory) { _selectedCategory.value = c }

    // Recent apps: top N most recently used (not hidden)
    val recentApps: StateFlow<List<AppInfo>> = combine(_allApps, _appUsage, _hiddenApps) { apps, usage, hidden ->
        if (usage.isEmpty()) emptyList()
        else {
            val appMap = apps.associateBy { it.key }
            usage.entries
                .filter { it.key !in hidden && it.key in appMap }
                .sortedByDescending { it.value }
                .take(MAX_RECENT_APPS)
                .mapNotNull { appMap[it.key] }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Time-aware app suggestions
    val suggestedApps: StateFlow<List<AppInfo>> = combine(
        _allApps, _suggestionUsage, _hiddenApps, _homeGrid, _dockGrid
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        val apps = args[0] as List<AppInfo>
        @Suppress("UNCHECKED_CAST")
        val usage = args[1] as Map<String, Int>
        @Suppress("UNCHECKED_CAST")
        val hidden = args[2] as Set<String>
        @Suppress("UNCHECKED_CAST")
        val home = args[3] as List<GridCell?>
        @Suppress("UNCHECKED_CAST")
        val dock = args[4] as List<GridCell?>
        if (usage.isEmpty()) return@combine emptyList<AppInfo>()
        val bucket = currentTimeBucket()
        val onScreen = (home.filterNotNull().filterIsInstance<GridCell.App>().map { it.appKey } +
                        dock.filterNotNull().filterIsInstance<GridCell.App>().map { it.appKey }).toSet()
        apps.filter { it.key !in hidden && it.key !in onScreen }
            .map { app ->
                val bucketCount = usage["$bucket:${app.key}"] ?: 0
                val totalCount = listOf("morning", "afternoon", "evening", "night")
                    .sumOf { b -> usage["$b:${app.key}"] ?: 0 }
                app to (bucketCount * 3 + totalCount)
            }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(5)
            .map { it.first }
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

    data class HomeMenuState(val cell: GridCell, val source: DragSource, val index: Int, val appInfo: AppInfo? = null)
    private val _homeMenu = MutableStateFlow<HomeMenuState?>(null)
    val homeMenu: StateFlow<HomeMenuState?> = _homeMenu.asStateFlow()

    private val _editMode = MutableStateFlow(false)
    val editMode: StateFlow<Boolean> = _editMode.asStateFlow()

    private var reloadJob: Job? = null
    private var flashlightOn = false

    init {
        loadApps()
        discoverIconPacks()
        viewModelScope.launch { prefs.homeGrid.collect { if (it.isNotEmpty()) _homeGrid.value = it } }
        viewModelScope.launch { prefs.dockGrid.collect { if (it.isNotEmpty()) _dockGrid.value = it } }
        viewModelScope.launch { prefs.hiddenApps.collect { _hiddenApps.value = it } }
        viewModelScope.launch { prefs.customLabels.collect { _customLabels.value = it } }
        viewModelScope.launch { prefs.appUsage.collect { _appUsage.value = it } }
        viewModelScope.launch { prefs.widgets.collect { _widgets.value = it } }
        viewModelScope.launch { prefs.searchHistory.collect { _searchHistory.value = it } }
        viewModelScope.launch { prefs.suggestionUsage.collect { _suggestionUsage.value = it } }
        viewModelScope.launch {
            combine(_allApps, _initialized) { a, i -> a to i }.filter { it.first.isNotEmpty() && !it.second }
                .take(1).collect { (apps, _) -> autoPopulate(apps) }
        }
        viewModelScope.launch { _allApps.filter { it.isNotEmpty() }.drop(1).collect { cleanupStaleKeys(it) } }
        viewModelScope.launch {
            settings.filter { it.iconPack.isNotBlank() }.take(1).collect { s ->
                applyIconPack(s.iconPack)
            }
        }
    }

    // -- App Loading --

    fun loadApps() { viewModelScope.launch { loadAppsInternal() } }

    private suspend fun loadAppsInternal() {
        val apps = try {
            repo.loadApps(iconPackManager, useThemedIcons = settings.value.themedIcons)
        } catch (e: Exception) {
            Log.e(TAG, "loadApps failed", e)
            emptyList()
        }
        _allApps.value = apps; _appMap.value = apps.associateBy { it.key }
    }

    fun debouncedReload() {
        reloadJob?.cancel()
        reloadJob = viewModelScope.launch {
            delay(DEBOUNCE_MS)
            loadApps()
        }
    }

    fun resolveApp(key: String): AppInfo? = _appMap.value[key]
    fun getLabel(key: String): String? = _customLabels.value[key] ?: resolveApp(key)?.label

    fun launch(app: AppInfo) {
        if (!repo.isPackageInstalled(app.packageName)) {
            toast("App no longer installed")
            debouncedReload()
            return
        }
        repo.launchApp(app)
        // Track all usage in a single coroutine with batched DataStore write
        viewModelScope.launch {
            try {
                // App usage tracking
                val updated = _appUsage.value.toMutableMap()
                updated[app.key] = System.currentTimeMillis()
                val trimmed = if (updated.size > 50) {
                    updated.entries.sortedByDescending { it.value }.take(50).associate { it.key to it.value }
                } else updated
                _appUsage.value = trimmed

                // Suggestion usage (time-bucketed)
                val bucket = currentTimeBucket()
                val sugKey = "$bucket:${app.key}"
                val sugUsage = _suggestionUsage.value.toMutableMap()
                sugUsage[sugKey] = (sugUsage[sugKey] ?: 0) + 1
                val trimmedSug = if (sugUsage.size > 200) {
                    sugUsage.entries.sortedByDescending { it.value }.take(200).associate { it.key to it.value }
                } else sugUsage
                _suggestionUsage.value = trimmedSug

                // Search history (if user searched for something)
                val q = _search.value
                val histToSave = if (q.isNotBlank() && q.length >= 2) {
                    val hist = _searchHistory.value.toMutableList()
                    hist.remove(q)
                    hist.add(0, q)
                    val trimmedHist = hist.take(10)
                    _searchHistory.value = trimmedHist
                    trimmedHist
                } else null

                // Single DataStore transaction for all tracking data
                prefs.saveLaunchTracking(trimmed, trimmedSug, histToSave)
            } catch (e: Exception) {
                Log.e(TAG, "Launch tracking failed", e)
            }
        }
    }

    fun appInfo(app: AppInfo) = repo.openAppInfo(app)
    fun uninstall(app: AppInfo) = repo.uninstallApp(app)
    fun setSearch(q: String) {
        _search.value = q
        if (q.length >= 2) searchContacts(q) else _contactResults.value = emptyList()
    }
    fun openDrawer() { _drawerOpen.value = true }
    fun closeDrawer() { _drawerOpen.value = false; _search.value = "" }
    fun openSettings() { _settingsOpen.value = true }
    fun closeSettings() { _settingsOpen.value = false }
    fun closeAllOverlays() { _drawerOpen.value = false; _settingsOpen.value = false; _openFolder.value = null; _drawerMenuApp.value = null; _folderRename.value = null; _labelEdit.value = null; _homeMenu.value = null; _editMode.value = false; _search.value = ""; _shortcuts.value = emptyList() }
    fun hasOpenOverlay(): Boolean = _drawerOpen.value || _settingsOpen.value || _openFolder.value != null || _drawerMenuApp.value != null || _labelEdit.value != null || _homeMenu.value != null

    // -- Home/Dock Context Menu --

    fun showHomeMenu(cell: GridCell, source: DragSource, index: Int) {
        if (settings.value.homeLocked) { toast("Home screen is locked"); return }
        val info = if (cell is GridCell.App) resolveApp(cell.appKey) else null
        _homeMenu.value = HomeMenuState(cell, source, index, info)
        // Load shortcuts for the app
        if (cell is GridCell.App) {
            val pkgName = cell.appKey.substringBefore("/")
            viewModelScope.launch { _shortcuts.value = shortcutRepo.getShortcuts(pkgName) }
        } else {
            _shortcuts.value = emptyList()
        }
        vibrate()
    }
    fun dismissHomeMenu() { _homeMenu.value = null; _shortcuts.value = emptyList() }

    fun removeFromGrid(source: DragSource, index: Int) { viewModelScope.launch {
        val g = gridForSource(source).toMutableList()
        if (index in g.indices) { g[index] = null; saveGrid(source, if (source == DragSource.HOME) trimGrid(g, pageSize()) else g) }
        _homeMenu.value = null; _shortcuts.value = emptyList()
    }}

    fun enterEditMode() { if (settings.value.homeLocked) { toast("Home screen is locked"); return }; _homeMenu.value = null; _shortcuts.value = emptyList(); _editMode.value = true }
    fun exitEditMode() { _editMode.value = false }

    // -- App Shortcuts --

    fun launchShortcut(shortcut: AppShortcut) {
        shortcutRepo.launchShortcut(shortcut)
        _homeMenu.value = null; _shortcuts.value = emptyList()
    }

    fun loadShortcutsForDrawerMenu(app: AppInfo) {
        viewModelScope.launch { _shortcuts.value = shortcutRepo.getShortcuts(app.packageName) }
    }

    // -- Icon Packs --

    private fun discoverIconPacks() { viewModelScope.launch {
        _availablePacks.value = try { iconPackManager.getInstalledPacks() } catch (e: Exception) { Log.e(TAG, "Icon pack discovery failed", e); emptyList() }
    }}

    fun setIconPack(packageName: String) { viewModelScope.launch { pref(LauncherPrefs.ICON_PACK, packageName); applyIconPack(packageName) } }

    fun clearIconPack() { viewModelScope.launch {
        pref(LauncherPrefs.ICON_PACK, ""); iconPackManager.clearPack(); loadAppsInternal(); toast("System icons restored")
    }}

    fun refreshIconPacks() { discoverIconPacks() }

    private suspend fun applyIconPack(packageName: String) {
        if (packageName.isBlank()) { iconPackManager.clearPack(); loadAppsInternal(); return }
        _iconPackLoading.value = true
        try {
            val ok = iconPackManager.loadPack(packageName)
            if (ok) { loadAppsInternal(); toast("Icon pack applied (${iconPackManager.mappedCount()} icons)") }
            else toast("Failed to load icon pack")
        } catch (e: Exception) {
            Log.e(TAG, "Icon pack apply failed", e)
            toast("Icon pack error")
        }
        _iconPackLoading.value = false
    }

    // -- Gestures --

    fun executeGesture(action: GestureAction) {
        try {
            when (action) {
                GestureAction.NONE -> {}
                GestureAction.LOCK_SCREEN -> lockScreen()
                GestureAction.NOTIFICATION_SHADE -> expandNotifications()
                GestureAction.APP_DRAWER -> openDrawer()
                GestureAction.SETTINGS -> openSettings()
                GestureAction.KILL_APPS -> killBackgroundApps()
                GestureAction.FLASHLIGHT -> toggleFlashlight()
                GestureAction.EDIT_MODE -> { if (!settings.value.homeLocked) { _editMode.value = !_editMode.value } else toast("Home screen is locked") }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gesture execution failed: $action", e)
        }
    }

    fun toggleFlashlight() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        try {
            val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return
            val cameraId = cm.cameraIdList.firstOrNull() ?: return
            flashlightOn = !flashlightOn
            cm.setTorchMode(cameraId, flashlightOn)
            toast(if (flashlightOn) "Flashlight on" else "Flashlight off")
        } catch (e: Exception) {
            Log.e(TAG, "toggleFlashlight failed", e)
            flashlightOn = false
        }
    }

    fun lockScreen() { runCatching {
        val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager ?: return
        if (dpm.isAdminActive(ComponentName(ctx, AdminReceiver::class.java))) dpm.lockNow()
        else toast("Enable Device Admin in Settings to lock screen")
    }.onFailure { Log.e(TAG, "lockScreen failed", it) }}

    fun isDeviceAdminEnabled(): Boolean = runCatching {
        (ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager)?.isAdminActive(ComponentName(ctx, AdminReceiver::class.java)) ?: false
    }.getOrDefault(false)

    fun requestDeviceAdmin() { runCatching {
        ctx.startActivity(Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, ComponentName(ctx, AdminReceiver::class.java))
            putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Required for double-tap to lock screen.")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
    }.onFailure { Log.e(TAG, "requestDeviceAdmin failed", it) }}

    @Suppress("DEPRECATION")
    fun expandNotifications() {
        try {
            val sbService = ctx.getSystemService("statusbar") ?: return
            val method = sbService.javaClass.getMethod("expandNotificationsPanel")
            method.invoke(sbService)
        } catch (e: Exception) {
            Log.w(TAG, "expandNotifications failed (OEM may block this)", e)
        }
    }

    fun killBackgroundApps() {
        try {
            val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return
            val pkgs = am.runningAppProcesses?.flatMap { it.pkgList?.toList() ?: emptyList() }?.distinct() ?: emptyList()
            var n = 0; pkgs.forEach { if (it != ctx.packageName) { am.killBackgroundProcesses(it); n++ } }
            toast("Cleared $n background apps")
        } catch (e: Exception) {
            Log.e(TAG, "killBackgroundApps failed", e)
        }
    }

    fun openWallpaperPicker() {
        try {
            ctx.startActivity(Intent(Intent.ACTION_SET_WALLPAPER).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
        } catch (e: Exception) {
            Log.e(TAG, "openWallpaperPicker failed", e)
        }
    }

    fun openNotificationAccess() {
        try {
            ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
        } catch (e: Exception) {
            Log.e(TAG, "openNotificationAccess failed", e)
        }
    }

    fun isNotificationAccessGranted(): Boolean {
        return NotificationListener.connected.value
    }

    // -- Auto-Place New Apps --

    fun onAppInstalled(packageName: String) { viewModelScope.launch {
        loadAppsInternal()
        if (!settings.value.autoPlaceNew) return@launch
        val app = _allApps.value.find { it.packageName == packageName } ?: return@launch
        val ps = pageSize(); val grid = padGrid(_homeGrid.value, ps).toMutableList()
        if (grid.any { it is GridCell.App && it.appKey == app.key }) return@launch
        var i = grid.indexOfFirst { it == null }
        if (i < 0) { grid.addAll(List(ps) { null }); i = grid.indexOfFirst { it == null } }
        if (i >= 0) { grid[i] = GridCell.App(app.key); _homeGrid.value = grid; prefs.saveHome(grid) }
    }}

    // -- Custom Labels --

    fun startLabelEdit(appKey: String) { _labelEdit.value = LabelEdit(appKey, _customLabels.value[appKey] ?: resolveApp(appKey)?.label ?: ""); _homeMenu.value = null; _shortcuts.value = emptyList() }
    fun dismissLabelEdit() { _labelEdit.value = null }
    fun saveCustomLabel(appKey: String, label: String) { viewModelScope.launch {
        val orig = resolveApp(appKey)?.label ?: ""; val map = _customLabels.value.toMutableMap()
        if (label.isBlank() || label == orig) map.remove(appKey) else map[appKey] = label.trim()
        _customLabels.value = map; prefs.saveCustomLabels(map); _labelEdit.value = null
    }}

    // -- Hidden Apps --

    fun hideApp(key: String) { viewModelScope.launch { val u = _hiddenApps.value + key; _hiddenApps.value = u; prefs.saveHidden(u); toast("Hidden from drawer"); _drawerMenuApp.value = null; _shortcuts.value = emptyList() } }
    fun unhideApp(key: String) { viewModelScope.launch { val u = _hiddenApps.value - key; _hiddenApps.value = u; prefs.saveHidden(u) } }

    // -- Page Helpers --

    fun pageSize(): Int { val s = settings.value; return (s.gridColumns * s.gridRows).coerceAtLeast(1) }
    fun numPages(): Int { val ps = pageSize(); return max(1, (_homeGrid.value.size + ps - 1) / ps) }

    private fun padGrid(grid: List<GridCell?>, ps: Int): List<GridCell?> {
        if (ps <= 0) return grid
        val t = max(ps, ((grid.size + ps - 1) / ps) * ps)
        return if (grid.size >= t) grid.take(t) else grid + List(t - grid.size) { null }
    }
    private fun trimGrid(grid: List<GridCell?>, ps: Int): List<GridCell?> {
        if (ps <= 0) return grid; val p = padGrid(grid, ps).toMutableList()
        while (p.size > ps && p.takeLast(ps).all { it == null }) repeat(ps) { p.removeLastOrNull() }
        return p
    }

    // -- Stale Cleanup --

    private suspend fun cleanupStaleKeys(apps: List<AppInfo>) {
        try {
            val valid = apps.map { it.key }.toSet(); var c = false
            val home = _homeGrid.value.toMutableList()
            for (i in home.indices) { val r = cleanCell(home[i], valid); if (r !== home[i]) { home[i] = r; c = true } }
            if (c) { _homeGrid.value = home; prefs.saveHome(home) }
            c = false; val dock = _dockGrid.value.toMutableList()
            for (i in dock.indices) { val r = cleanCell(dock[i], valid); if (r !== dock[i]) { dock[i] = r; c = true } }
            if (c) { _dockGrid.value = dock; prefs.saveDock(dock) }
        } catch (e: Exception) {
            Log.e(TAG, "cleanupStaleKeys failed", e)
        }
    }
    private fun cleanCell(cell: GridCell?, valid: Set<String>): GridCell? = when (cell) {
        is GridCell.App -> if (cell.appKey in valid) cell else null
        is GridCell.Folder -> { val f = cell.appKeys.filter { it in valid }; when { f.isEmpty() -> null; f.size == 1 -> GridCell.App(f[0]); f.size != cell.appKeys.size -> cell.copy(appKeys = f); else -> cell } }
        is GridCell.Widget -> cell
        null -> null
    }

    // -- Folder --

    fun openFolderView(f: GridCell.Folder, src: DragSource, index: Int) { _openFolder.value = Triple(f, src, index) }
    fun closeFolderView() { _openFolder.value = null }
    fun startFolderRename(src: DragSource, index: Int, current: String) { _folderRename.value = FolderRename(src, index, current) }
    fun dismissFolderRename() { _folderRename.value = null }
    fun renameFolder(src: DragSource, index: Int, newName: String) { viewModelScope.launch {
        val g = gridForSource(src).toMutableList(); val c = g.getOrNull(index)
        if (c is GridCell.Folder) {
            val updated = c.copy(name = newName)
            g[index] = updated; saveGrid(src, g)
            _openFolder.value?.let { (_, oSrc, oIdx) -> if (oSrc == src && oIdx == index) _openFolder.value = Triple(updated, src, index) }
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
        (g.getOrNull(idx) as? GridCell.Folder)?.let { _openFolder.value = Triple(it, src, idx) }
    }}

    // -- Drawer Menu --

    fun showDrawerMenu(app: AppInfo) {
        _drawerMenuApp.value = app
        viewModelScope.launch { _shortcuts.value = shortcutRepo.getShortcuts(app.packageName) }
    }
    fun dismissDrawerMenu() { _drawerMenuApp.value = null; _shortcuts.value = emptyList() }

    fun pinToHome(app: AppInfo) { viewModelScope.launch {
        val ps = pageSize(); val grid = padGrid(_homeGrid.value, ps).toMutableList()
        if (grid.any { it is GridCell.App && it.appKey == app.key }) { toast("Already on home"); _drawerMenuApp.value = null; _shortcuts.value = emptyList(); return@launch }
        var i = grid.indexOfFirst { it == null }; if (i < 0) { grid.addAll(List(ps) { null }); i = grid.indexOfFirst { it == null } }
        if (i >= 0) { grid[i] = GridCell.App(app.key); _homeGrid.value = grid; prefs.saveHome(grid); toast("Added to home") }
        _drawerMenuApp.value = null; _shortcuts.value = emptyList()
    }}

    fun pinToDock(app: AppInfo) { viewModelScope.launch {
        val dc = settings.value.dockCount; val dock = _dockGrid.value.toMutableList(); while (dock.size < dc) dock.add(null)
        if (dock.any { it is GridCell.App && it.appKey == app.key }) { toast("Already in dock"); _drawerMenuApp.value = null; _shortcuts.value = emptyList(); return@launch }
        val i = dock.indexOfFirst { it == null }; if (i < 0) { toast("Dock full"); _drawerMenuApp.value = null; _shortcuts.value = emptyList(); return@launch }
        dock[i] = GridCell.App(app.key); _dockGrid.value = dock; prefs.saveDock(dock); toast("Added to dock"); _drawerMenuApp.value = null; _shortcuts.value = emptyList()
    }}

    // -- Drag and Drop --

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
            try {
                when {
                    _hoverUninstall.value -> { if (drag.item is GridCell.App) { clearSourceCell(drag); drag.appInfo?.let { uninstall(it) } } }
                    _hoverRemove.value -> clearSourceCell(drag)
                    hi >= 0 && isDock -> dropOnGrid(drag, hi, DragSource.DOCK)
                    hi >= 0 -> dropOnGrid(drag, hi, DragSource.HOME)
                }
            } catch (e: Exception) {
                Log.e(TAG, "endDrag failed", e)
            }
            clearDragState()
        }
    }

    private suspend fun dropOnGrid(drag: DragState, ti: Int, target: DragSource) {
        // Cap target index to prevent unbounded list growth from stale hover indices
        val maxAllowed = if (target == DragSource.DOCK) settings.value.dockCount else (numPages() + 1) * pageSize()
        if (ti < 0 || ti >= maxAllowed) return
        val tg = gridForSource(target).toMutableList(); while (tg.size <= ti) tg.add(null)
        val ex = tg.getOrNull(ti); val same = drag.source == target

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
            is GridCell.Widget -> { /* Widgets cannot be dragged */ }
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

    // -- Fuzzy Search --

    private fun searchScore(label: String, packageName: String, query: String): Int {
        val q = query.lowercase()
        val l = label.lowercase()
        val p = packageName.lowercase()
        return when {
            l == q -> 100
            l.startsWith(q) -> 90
            l.split(" ").any { it.startsWith(q) } -> 80
            l.contains(q) -> 70
            p.contains(q) -> 60
            isSubsequence(q, l) -> 50
            else -> 0
        }
    }

    private fun isSubsequence(query: String, text: String): Boolean {
        var qi = 0
        for (ch in text) {
            if (qi < query.length && ch == query[qi]) qi++
        }
        return qi == query.length
    }

    // -- Inline Calculator --

    private fun tryEvaluate(expr: String): String? {
        val cleaned = expr.replace(" ", "").replace("x", "*").replace("X", "*")
        if (cleaned.length < 3) return null
        if (!cleaned.any { it in "+-*/%" } || !cleaned.any { it.isDigit() }) return null
        return try {
            val (value, consumed) = evalExpression(cleaned, 0)
            if (consumed != cleaned.length) return null
            if (value.isNaN() || value.isInfinite()) return null
            if (value == value.toLong().toDouble()) value.toLong().toString()
            else String.format("%.8f", value).trimEnd('0').trimEnd('.')
        } catch (_: Exception) { null }
    }

    private fun evalExpression(expr: String, pos: Int): Pair<Double, Int> {
        var (left, i) = evalTerm(expr, pos)
        while (i < expr.length && expr[i] in "+-") {
            val op = expr[i]; i++
            val (right, ni) = evalTerm(expr, i)
            left = if (op == '+') left + right else left - right
            i = ni
        }
        return left to i
    }

    private fun evalTerm(expr: String, pos: Int): Pair<Double, Int> {
        var (left, i) = evalFactor(expr, pos)
        while (i < expr.length && expr[i] in "*/%") {
            val op = expr[i]; i++
            val (right, ni) = evalFactor(expr, i)
            left = when (op) { '*' -> left * right; '%' -> left % right; else -> if (right != 0.0) left / right else Double.NaN }
            i = ni
        }
        return left to i
    }

    private fun evalFactor(expr: String, pos: Int): Pair<Double, Int> {
        var i = pos
        if (i < expr.length && expr[i] == '(') {
            i++
            val (result, ni) = evalExpression(expr, i)
            i = ni
            if (i < expr.length && expr[i] == ')') i++
            return result to i
        }
        val start = i
        if (i < expr.length && expr[i] == '-') i++
        while (i < expr.length && (expr[i].isDigit() || expr[i] == '.')) i++
        if (i == start) return 0.0 to i
        return expr.substring(start, i).toDouble() to i
    }

    // -- Unit Converter --

    private fun tryConvertUnit(input: String): String? {
        val cleaned = input.trim()
        val match = Regex("""^(-?\d+\.?\d*)\s*(km|mi|lb|kg|oz|g|ft|m|cm|in|gal|L|F|C)$""", RegexOption.IGNORE_CASE)
            .matchEntire(cleaned) ?: return null
        val value = match.groupValues[1].toDoubleOrNull() ?: return null
        val unit = match.groupValues[2]
        val (result, toUnit) = when (unit.lowercase()) {
            "km" -> (value * 0.621371) to "mi"
            "mi" -> (value * 1.60934) to "km"
            "m" -> (value * 3.28084) to "ft"
            "ft" -> (value * 0.3048) to "m"
            "cm" -> (value * 0.393701) to "in"
            "in" -> (value * 2.54) to "cm"
            "lb" -> (value * 0.453592) to "kg"
            "kg" -> (value * 2.20462) to "lb"
            "oz" -> (value * 28.3495) to "g"
            "g" -> (value * 0.035274) to "oz"
            "gal" -> (value * 3.78541) to "L"
            "l" -> (value * 0.264172) to "gal"
            "f" -> ((value - 32) * 5.0 / 9.0) to "C"
            "c" -> (value * 9.0 / 5.0 + 32) to "F"
            else -> return null
        }
        val formatted = if (result == result.toLong().toDouble()) result.toLong().toString()
            else String.format("%.4f", result).trimEnd('0').trimEnd('.')
        return "$formatted $toUnit"
    }

    // -- Launch Count --

    fun getAppLaunchCount(appKey: String): Int {
        return listOf("morning", "afternoon", "evening", "night")
            .sumOf { bucket -> _suggestionUsage.value["$bucket:$appKey"] ?: 0 }
    }

    // -- Time Bucket (for suggestions) --

    private fun currentTimeBucket(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when {
            hour in 6..11 -> "morning"
            hour in 12..16 -> "afternoon"
            hour in 17..21 -> "evening"
            else -> "night"
        }
    }

    // -- Search History --

    fun removeSearchHistoryItem(term: String) {
        viewModelScope.launch {
            val list = _searchHistory.value.filter { it != term }
            _searchHistory.value = list
            prefs.saveSearchHistory(list)
        }
    }

    fun clearSearchHistory() {
        viewModelScope.launch {
            _searchHistory.value = emptyList()
            prefs.saveSearchHistory(emptyList())
        }
    }

    // -- Settings --

    fun setTheme(t: ThemeMode) = pref(LauncherPrefs.THEME, t.name)
    fun setShape(s: IconShape) = pref(LauncherPrefs.ICON_SHAPE, s.name)
    fun setIconSize(s: IconSize) = pref(LauncherPrefs.ICON_SIZE, s.name)
    fun setShowClock(v: Boolean) = pref(LauncherPrefs.SHOW_CLOCK, v)
    fun setShowDockSearch(v: Boolean) = pref(LauncherPrefs.SHOW_DOCK_SEARCH, v)
    fun setAutoPlaceNew(v: Boolean) = pref(LauncherPrefs.AUTO_PLACE_NEW, v)
    fun setDoubleTapAction(a: GestureAction) = pref(LauncherPrefs.DOUBLE_TAP_ACTION, a.name)
    fun setSwipeDownAction(a: GestureAction) = pref(LauncherPrefs.SWIPE_DOWN_ACTION, a.name)
    fun setWallpaperDim(v: Int) = pref(LauncherPrefs.WALLPAPER_DIM, v.coerceIn(0, 100))
    fun setShowNotifBadges(v: Boolean) = pref(LauncherPrefs.SHOW_NOTIF_BADGES, v)
    fun setDrawerSort(s: DrawerSort) = pref(LauncherPrefs.DRAWER_SORT, s.name)
    fun setLabelStyle(s: LabelStyle) = pref(LauncherPrefs.LABEL_STYLE, s.name)
    fun setThemedIcons(v: Boolean) { pref(LauncherPrefs.THEMED_ICONS, v); viewModelScope.launch { loadAppsInternal() } }
    fun setPageTransition(t: PageTransition) = pref(LauncherPrefs.PAGE_TRANSITION, t.name)
    fun setBadgeStyle(s: BadgeStyle) = pref(LauncherPrefs.BADGE_STYLE, s.name)
    fun setGridPaddingH(v: Int) = pref(LauncherPrefs.GRID_PADDING_H, v.coerceIn(0, 24))
    fun setGridPaddingV(v: Int) = pref(LauncherPrefs.GRID_PADDING_V, v.coerceIn(0, 24))
    fun setHideStatusBar(v: Boolean) = pref(LauncherPrefs.HIDE_STATUS_BAR, v)
    fun setDrawerColumns(c: Int) = pref(LauncherPrefs.DRAWER_COLUMNS, c.coerceIn(0, 8))
    fun setHomeLocked(v: Boolean) { pref(LauncherPrefs.HOME_LOCKED, v); if (v) { _editMode.value = false }; toast(if (v) "Home screen locked" else "Home screen unlocked") }
    fun setIconShadow(v: Boolean) = pref(LauncherPrefs.ICON_SHADOW, v)
    fun setAccentOverride(hex: String) = pref(LauncherPrefs.ACCENT_OVERRIDE, hex)
    fun setDrawerCategories(v: Boolean) = pref(LauncherPrefs.DRAWER_CATEGORIES, v)
    fun setDockStyle(s: DockStyle) = pref(LauncherPrefs.DOCK_STYLE, s.name)
    fun setSearchBarStyle(s: SearchBarStyle) = pref(LauncherPrefs.SEARCH_BAR_STYLE, s.name)
    fun setHapticLevel(l: HapticLevel) = pref(LauncherPrefs.HAPTIC_LEVEL, l.name)
    fun setDrawerOpacity(v: Int) = pref(LauncherPrefs.DRAWER_OPACITY, v.coerceIn(0, 100))
    fun setLabelSize(s: LabelSize) = pref(LauncherPrefs.LABEL_SIZE_PREF, s.name)
    fun setFolderColumns(c: Int) = pref(LauncherPrefs.FOLDER_COLUMNS, c.coerceIn(3, 5))
    fun setDrawerSectionHeaders(v: Boolean) = pref(LauncherPrefs.DRAWER_SECTION_HEADERS, v)
    fun setWallpaperParallax(v: Boolean) = pref(LauncherPrefs.WALLPAPER_PARALLAX, v)
    fun setDrawerAnimation(v: Boolean) = pref(LauncherPrefs.DRAWER_ANIMATION, v)
    fun setTripleTapAction(a: GestureAction) = pref(LauncherPrefs.TRIPLE_TAP_ACTION, a.name)
    fun setPinchAction(a: GestureAction) = pref(LauncherPrefs.PINCH_ACTION, a.name)
    fun setDockTapAction(a: GestureAction) = pref(LauncherPrefs.DOCK_TAP_ACTION, a.name)
    fun setShowSuggestions(v: Boolean) = pref(LauncherPrefs.SHOW_SUGGESTIONS, v)
    fun setClockStyle(s: ClockStyle) = pref(LauncherPrefs.CLOCK_STYLE, s.name)
    fun cycleClockStyle() {
        val styles = ClockStyle.entries
        val next = styles[(styles.indexOf(settings.value.clockStyle) + 1) % styles.size]
        setClockStyle(next)
        vibrate()
    }

    fun getAppVersionInfo(packageName: String): String? {
        return try {
            val pi = ctx.packageManager.getPackageInfo(packageName, 0)
            "v${pi.versionName ?: "?"}"
        } catch (_: Exception) { null }
    }

    fun getAppSizeInfo(packageName: String): String? {
        return try {
            val ai = ctx.packageManager.getApplicationInfo(packageName, 0)
            val file = java.io.File(ai.sourceDir)
            val bytes = file.length()
            when {
                bytes >= 1_000_000_000 -> String.format("%.1f GB", bytes / 1_000_000_000.0)
                bytes >= 1_000_000 -> String.format("%.1f MB", bytes / 1_000_000.0)
                bytes >= 1_000 -> String.format("%.0f KB", bytes / 1_000.0)
                else -> "$bytes B"
            }
        } catch (_: Exception) { null }
    }

    fun resetAllSettings() {
        viewModelScope.launch {
            try {
                val defaults = LauncherSettings()
                prefs.set(LauncherPrefs.THEME, defaults.themeMode.name)
                prefs.set(LauncherPrefs.ICON_SHAPE, defaults.iconShape.name)
                prefs.set(LauncherPrefs.ICON_SIZE, defaults.iconSize.name)
                prefs.set(LauncherPrefs.ICON_PACK, "")
                prefs.set(LauncherPrefs.GRID_COLS, defaults.gridColumns)
                prefs.set(LauncherPrefs.GRID_ROWS, defaults.gridRows)
                prefs.set(LauncherPrefs.DOCK_COUNT, defaults.dockCount)
                prefs.set(LauncherPrefs.SHOW_CLOCK, defaults.showClock)
                prefs.set(LauncherPrefs.WALLPAPER_DIM, defaults.wallpaperDim)
                prefs.set(LauncherPrefs.SHOW_NOTIF_BADGES, defaults.showNotifBadges)
                prefs.set(LauncherPrefs.DRAWER_SORT, defaults.drawerSort.name)
                prefs.set(LauncherPrefs.LABEL_STYLE, defaults.labelStyle.name)
                prefs.set(LauncherPrefs.PAGE_TRANSITION, defaults.pageTransition.name)
                prefs.set(LauncherPrefs.BADGE_STYLE, defaults.badgeStyle.name)
                prefs.set(LauncherPrefs.ACCENT_OVERRIDE, "")
                prefs.set(LauncherPrefs.DOCK_STYLE, defaults.dockStyle.name)
                prefs.set(LauncherPrefs.SEARCH_BAR_STYLE, defaults.searchBarStyle.name)
                prefs.set(LauncherPrefs.HAPTIC_LEVEL, defaults.hapticLevel.name)
                prefs.set(LauncherPrefs.DRAWER_OPACITY, defaults.drawerOpacity)
                prefs.set(LauncherPrefs.LABEL_SIZE_PREF, defaults.labelSize.name)
                prefs.set(LauncherPrefs.CLOCK_STYLE, defaults.clockStyle.name)
                prefs.set(LauncherPrefs.SHOW_SUGGESTIONS, defaults.showSuggestions)
                prefs.set(LauncherPrefs.HIDE_STATUS_BAR, defaults.hideStatusBar)
                prefs.set(LauncherPrefs.ICON_SHADOW, defaults.iconShadow)
                prefs.set(LauncherPrefs.HOME_LOCKED, false)
                iconPackManager.clearPack()
                loadAppsInternal()
                toast("Settings reset to defaults")
            } catch (e: Exception) {
                Log.e(TAG, "resetAllSettings failed", e)
            }
        }
    }

    // -- Dock Swipe Actions --

    fun setDockSwipeApp(dockIndex: Int, appKey: String) { viewModelScope.launch {
        val map = settings.value.dockSwipeApps.toMutableMap()
        if (appKey.isBlank()) map.remove(dockIndex) else map[dockIndex] = appKey
        prefs.saveDockSwipeApps(map)
    }}

    fun clearDockSwipeApp(dockIndex: Int) { viewModelScope.launch {
        val map = settings.value.dockSwipeApps.toMutableMap()
        map.remove(dockIndex)
        prefs.saveDockSwipeApps(map)
    }}

    fun launchDockSwipe(dockIndex: Int): Boolean {
        val appKey = settings.value.dockSwipeApps[dockIndex] ?: return false
        val app = resolveApp(appKey) ?: return false
        launch(app)
        return true
    }

    fun openClockApp() {
        try {
            val i = Intent("android.intent.action.SHOW_ALARMS").apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
            ctx.startActivity(i)
        } catch (_: Exception) {
            try {
                ctx.startActivity(Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    component = ComponentName("com.google.android.deskclock", "com.android.deskclock.DeskClock")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                })
            } catch (e: Exception) { Log.w(TAG, "openClockApp failed", e) }
        }
    }

    fun openCalendarApp() {
        try {
            ctx.startActivity(Intent(Intent.ACTION_VIEW).apply {
                data = android.net.Uri.parse("content://com.android.calendar/time/${System.currentTimeMillis()}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
        } catch (_: Exception) {
            try {
                ctx.startActivity(Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_APP_CALENDAR)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                })
            } catch (e: Exception) { Log.w(TAG, "openCalendarApp failed", e) }
        }
    }

    fun searchWeb(query: String) {
        try {
            val encoded = android.net.Uri.encode(query)
            ctx.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.google.com/search?q=$encoded")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
        } catch (e: Exception) {
            Log.e(TAG, "searchWeb failed", e)
        }
    }

    fun setGridCols(c: Int) { viewModelScope.launch { val old = settings.value.gridColumns; prefs.set(LauncherPrefs.GRID_COLS, c); if (c != old) reflowGrid(c, settings.value.gridRows) } }
    fun setGridRows(r: Int) { viewModelScope.launch { val old = settings.value.gridRows; prefs.set(LauncherPrefs.GRID_ROWS, r); if (r != old) reflowGrid(settings.value.gridColumns, r) } }
    fun setDockCount(c: Int) { viewModelScope.launch {
        prefs.set(LauncherPrefs.DOCK_COUNT, c); val dock = _dockGrid.value.toMutableList()
        while (dock.size < c) dock.add(null)
        if (dock.size > c) { val kept = dock.take(c).toMutableList(); dock.drop(c).filterNotNull().forEach { item -> val i = kept.indexOfFirst { it == null }; if (i >= 0) kept[i] = item }; _dockGrid.value = kept; prefs.saveDock(kept) }
        else { _dockGrid.value = dock; prefs.saveDock(dock) }
    }}

    private suspend fun reflowGrid(cols: Int, rows: Int) {
        val ps = (cols * rows).coerceAtLeast(1); val nn = _homeGrid.value.filterNotNull()
        val total = max(ps, ((nn.size + ps - 1) / ps) * ps); val g = MutableList<GridCell?>(total) { null }
        nn.forEachIndexed { i, c -> if (i < total) g[i] = c }; _homeGrid.value = g; prefs.saveHome(g)
    }

    suspend fun exportBackup(): String = prefs.exportBackup()
    suspend fun importBackup(json: String): Boolean {
        val ok = prefs.importBackup(json)
        if (ok) {
            toast("Layout restored")
            try {
                val restored = prefs.settings.first()
                if (restored.iconPack.isNotBlank()) applyIconPack(restored.iconPack) else loadAppsInternal()
            } catch (e: Exception) {
                Log.e(TAG, "Post-restore reload failed", e)
                loadAppsInternal()
            }
        } else toast("Restore failed")
        return ok
    }

    private fun <T> pref(key: androidx.datastore.preferences.core.Preferences.Key<T>, v: T) { viewModelScope.launch { prefs.set(key, v) } }

    fun vibrate() {
        try {
            val ms = settings.value.hapticLevel.ms
            if (ms <= 0) return
            val v = ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
            else @Suppress("DEPRECATION") v.vibrate(ms)
        } catch (e: Exception) {
            Log.w(TAG, "Vibration failed", e)
        }
    }

    private fun toast(msg: String) { try { Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show() } catch (_: Exception) {} }

    private suspend fun autoPopulate(apps: List<AppInfo>) {
        try {
            val s = settings.value; val gs = (s.gridColumns * s.gridRows).coerceAtLeast(1); val dc = s.dockCount.coerceAtLeast(1)
            val dock = MutableList<GridCell?>(dc) { null }; val used = mutableSetOf<String>(); var di = 0
            for (pkg in DEFAULT_DOCK_PKGS) { if (di >= dc) break; apps.find { it.packageName == pkg && it.key !in used }?.let { dock[di++] = GridCell.App(it.key); used.add(it.key) } }
            for (a in apps) { if (di >= dc) break; if (a.key !in used) { dock[di++] = GridCell.App(a.key); used.add(a.key) } }
            val home = MutableList<GridCell?>(gs) { null }; var hi = 0
            for (pkg in DEFAULT_HOME_PKGS) { if (hi >= gs) break; apps.find { it.packageName == pkg && it.key !in used }?.let { home[hi++] = GridCell.App(it.key); used.add(it.key) } }
            for (a in apps) { if (hi >= gs / 2) break; if (a.key !in used && home.none { it is GridCell.App && it.appKey == a.key }) { home[hi++] = GridCell.App(a.key); used.add(a.key) } }
            _homeGrid.value = home; _dockGrid.value = dock; prefs.saveHome(home); prefs.saveDock(dock); prefs.markInitialized()
        } catch (e: Exception) {
            Log.e(TAG, "autoPopulate failed", e)
        }
    }

    // -- Widgets --

    fun startWidgetHost() { runCatching { widgetHost.startListening() }.onFailure { Log.e(TAG, "startWidgetHost failed", it) } }
    fun stopWidgetHost() { runCatching { widgetHost.stopListening() }.onFailure { Log.e(TAG, "stopWidgetHost failed", it) } }

    fun openWidgetPicker() { _widgetPickerOpen.value = true }
    fun closeWidgetPicker() { _widgetPickerOpen.value = false }

    fun getAvailableWidgets(): List<AppWidgetProviderInfo> = runCatching {
        widgetManager.installedProviders.sortedBy { it.loadLabel(ctx.packageManager).toString().lowercase() }
    }.getOrDefault(emptyList())

    fun allocateWidgetId(): Int = widgetHost.allocateAppWidgetId()

    fun addWidget(info: WidgetInfo) { viewModelScope.launch {
        val list = _widgets.value + info
        _widgets.value = list; prefs.saveWidgets(list)
        // Mark grid cells as occupied
        val ps = pageSize(); val grid = padGrid(_homeGrid.value, ps).toMutableList()
        val pageStart = info.page * ps
        for (r in info.row until (info.row + info.spanY)) {
            for (c in info.col until (info.col + info.spanX)) {
                val idx = pageStart + r * settings.value.gridColumns + c
                if (idx in grid.indices && grid[idx] == null) grid[idx] = GridCell.Widget(info.appWidgetId)
            }
        }
        _homeGrid.value = grid; prefs.saveHome(grid)
        _widgetPickerOpen.value = false
        toast("Widget added")
    }}

    fun removeWidget(appWidgetId: Int) { viewModelScope.launch {
        widgetHost.deleteAppWidgetId(appWidgetId)
        val list = _widgets.value.filter { it.appWidgetId != appWidgetId }
        _widgets.value = list; prefs.saveWidgets(list)
        // Clear grid cells
        val grid = _homeGrid.value.toMutableList()
        for (i in grid.indices) { if (grid[i] is GridCell.Widget && (grid[i] as GridCell.Widget).widgetId == appWidgetId) grid[i] = null }
        _homeGrid.value = trimGrid(grid, pageSize()); prefs.saveHome(_homeGrid.value)
        toast("Widget removed")
    }}

    fun widgetForId(id: Int): WidgetInfo? = _widgets.value.find { it.appWidgetId == id }

    fun canBindWidget(appWidgetId: Int, provider: ComponentName): Boolean =
        widgetManager.bindAppWidgetIdIfAllowed(appWidgetId, provider)

    fun getBindIntent(appWidgetId: Int, provider: ComponentName): Intent =
        Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider)
        }

    fun findFirstEmptySpan(page: Int, spanX: Int, spanY: Int): Pair<Int, Int>? {
        val cols = settings.value.gridColumns; val rows = settings.value.gridRows
        val ps = cols * rows; val pageStart = page * ps
        val grid = padGrid(_homeGrid.value, ps)
        for (r in 0..(rows - spanY)) {
            for (c in 0..(cols - spanX)) {
                var fits = true
                for (dr in 0 until spanY) { for (dc in 0 until spanX) {
                    val idx = pageStart + (r + dr) * cols + (c + dc)
                    if (idx !in grid.indices || grid[idx] != null) { fits = false; break }
                }; if (!fits) break }
                if (fits) return r to c
            }
        }
        return null
    }

    // -- Contact Search --

    private var contactSearchJob: Job? = null

    private fun searchContacts(query: String) {
        contactSearchJob?.cancel()
        contactSearchJob = viewModelScope.launch {
            delay(200) // debounce
            // Check READ_CONTACTS permission before querying to avoid SecurityException spam
            if (ctx.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                _contactResults.value = emptyList()
                return@launch
            }
            val results = mutableListOf<ContactResult>()
            try {
                val uri = ContactsContract.Contacts.CONTENT_URI
                val proj = arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY, ContactsContract.Contacts.LOOKUP_KEY, ContactsContract.Contacts.HAS_PHONE_NUMBER)
                val sel = "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} LIKE ?"
                val args = arrayOf("%$query%")
                val cursor: Cursor? = ctx.contentResolver.query(uri, proj, sel, args, "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} ASC")
                cursor?.use { c ->
                    while (c.moveToNext() && results.size < 5) {
                        val name = c.getString(c.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)) ?: continue
                        val id = c.getString(c.getColumnIndexOrThrow(ContactsContract.Contacts._ID))
                        val lookupKey = c.getString(c.getColumnIndexOrThrow(ContactsContract.Contacts.LOOKUP_KEY))
                        val hasPhone = c.getInt(c.getColumnIndexOrThrow(ContactsContract.Contacts.HAS_PHONE_NUMBER)) > 0
                        var number: String? = null
                        if (hasPhone) {
                            val phoneCursor = ctx.contentResolver.query(
                                ContactsContract.CommonDataKinds.Phone.CONTENT_URI, arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                                "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?", arrayOf(id), null
                            )
                            phoneCursor?.use { pc -> if (pc.moveToFirst()) number = pc.getString(0) }
                        }
                        val lookupUri = ContactsContract.Contacts.getLookupUri(id.toLongOrNull() ?: 0, lookupKey)?.toString()
                        results.add(ContactResult(name, number, lookupUri))
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Contact search failed", e)
            }
            _contactResults.value = results
        }
    }

    fun openContact(lookupUri: String) {
        try {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(lookupUri)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
        } catch (e: Exception) { Log.e(TAG, "openContact failed", e) }
    }

    fun callContact(number: String) {
        try {
            ctx.startActivity(Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:$number")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
        } catch (e: Exception) { Log.e(TAG, "callContact failed", e) }
    }
}
