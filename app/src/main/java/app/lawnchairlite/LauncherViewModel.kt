package app.lawnchairlite

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.Application
import android.app.admin.DevicePolicyManager
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.ContactsContract
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.ui.geometry.Offset
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.lawnchairlite.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import kotlin.math.max

/** Lawnchair Lite - ViewModel */
class LauncherViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        private const val TAG = "LauncherVM"
        private const val DEBOUNCE_MS = 300L
        private const val MAX_RECENT_APPS = 8
        private const val WIDGET_HOST_ID = 1024
        private const val SMARTSPACE_REFRESH_MS = 15 * 60 * 1000L
    }

    private val ctx = app.applicationContext
    private val repo = AppRepository(app)
    val prefs = LauncherPrefs(app)
    val iconPackManager = IconPackManager(app)
    val shortcutRepo = ShortcutRepository(app)
    val widgetHost = AppWidgetHost(app, WIDGET_HOST_ID)
    val widgetManager: AppWidgetManager = AppWidgetManager.getInstance(app)
    private val smartspaceService = SmartspaceService(ctx)
    private val webSuggestionService = WebSuggestionService()
    private val backupService = LauncherBackupService(LauncherPrefsBackupGateway(prefs))
    private val backupImportPreparer = BackupImportPreparer(ctx)

    private val _allApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val allApps: StateFlow<List<AppInfo>> = _allApps.asStateFlow()
    private val _appMap = MutableStateFlow<Map<String, AppInfo>>(emptyMap())
    val settings = prefs.settings.stateIn(viewModelScope, SharingStarted.Eagerly, LauncherSettings())
    val cloudBackupTarget = prefs.cloudBackupTarget.stateIn(viewModelScope, SharingStarted.Eagerly, CloudBackupTarget())
    val backupScheduleState = prefs.backupScheduleState.stateIn(viewModelScope, SharingStarted.Eagerly, BackupScheduleState())

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
    private val _favoriteApps = MutableStateFlow<Set<String>>(emptySet())
    val favoriteAppKeys: StateFlow<Set<String>> = _favoriteApps.asStateFlow()
    private val _customLabels = MutableStateFlow<Map<String, String>>(emptyMap())
    val customLabels: StateFlow<Map<String, String>> = _customLabels.asStateFlow()
    private val _iconOverrides = MutableStateFlow<Map<String, String>>(emptyMap())
    val iconOverrides: StateFlow<Map<String, String>> = _iconOverrides.asStateFlow()

    // App usage tracking (package/activity key -> last launch timestamp)
    private val _appUsage = MutableStateFlow<Map<String, Long>>(emptyMap())
    val appUsage: StateFlow<Map<String, Long>> = _appUsage.asStateFlow()

    // Notification badge counts (package -> count)
    val notifCounts: StateFlow<Map<String, Int>> = NotificationListener.counts

    // App shortcuts state
    private val _shortcuts = MutableStateFlow<List<AppShortcut>>(emptyList())
    val shortcuts: StateFlow<List<AppShortcut>> = _shortcuts.asStateFlow()
    private val _shortcutShelf = MutableStateFlow<List<GridCell.Shortcut>>(emptyList())
    val shortcutShelf: StateFlow<List<GridCell.Shortcut>> = _shortcutShelf.asStateFlow()

    // Widget state
    private val _widgets = MutableStateFlow<List<WidgetInfo>>(emptyList())
    val widgets: StateFlow<List<WidgetInfo>> = _widgets.asStateFlow()
    private val _widgetPickerOpen = MutableStateFlow(false)
    val widgetPickerOpen: StateFlow<Boolean> = _widgetPickerOpen.asStateFlow()
    private val _widgetStackTargetId = MutableStateFlow<Int?>(null)
    private val _pendingWidgetPlacement = MutableStateFlow<PendingWidgetPlacement?>(null)
    val pendingWidgetPlacement: StateFlow<PendingWidgetPlacement?> = _pendingWidgetPlacement.asStateFlow()
    data class WidgetRemoveConfirm(val appWidgetId: Int, val label: String)
    private val _widgetRemoveConfirm = MutableStateFlow<WidgetRemoveConfirm?>(null)
    val widgetRemoveConfirm: StateFlow<WidgetRemoveConfirm?> = _widgetRemoveConfirm.asStateFlow()
    private val _homeSpaceMenu = MutableStateFlow(false)
    val homeSpaceMenu: StateFlow<Boolean> = _homeSpaceMenu.asStateFlow()
    fun showHomeSpaceMenu() { _homeSpaceMenu.value = true; vibrate() }
    fun dismissHomeSpaceMenu() { _homeSpaceMenu.value = false }

    // Contact search results
    data class ContactResult(val name: String, val number: String?, val lookupUri: String?)
    private val _contactResults = MutableStateFlow<List<ContactResult>>(emptyList())
    val contactResults: StateFlow<List<ContactResult>> = _contactResults.asStateFlow()

    // Search history
    private val _searchHistory = MutableStateFlow<List<String>>(emptyList())
    val searchHistory: StateFlow<List<String>> = _searchHistory.asStateFlow()
    private val _webSuggestions = MutableStateFlow<List<String>>(emptyList())
    val webSuggestions: StateFlow<List<String>> = _webSuggestions.asStateFlow()

    // First-party Smartspace state
    private val _smartspace = MutableStateFlow(SmartspaceState())
    val smartspace: StateFlow<SmartspaceState> = _smartspace.asStateFlow()

    // Suggestion usage (time-bucket:appKey -> launch count)
    private val _suggestionUsage = MutableStateFlow<Map<String, Int>>(emptyMap())

    private val _search = MutableStateFlow("")
    val search: StateFlow<String> = _search.asStateFlow()

    // Unit converter regex (must be declared before calculatorResult which uses SharingStarted.Eagerly)
    private val unitRegex = Regex("""^(-?\d+\.?\d*)\s*(km|mi|lb|kg|oz|g|ft|m|cm|in|gal|L|F|C)$""", RegexOption.IGNORE_CASE)

    // Inline calculator: evaluates math expressions typed into search
    val calculatorResult: StateFlow<String?> = _search.map { query ->
        tryEvaluate(query) ?: tryConvertUnit(query)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val filteredApps: StateFlow<List<AppInfo>> = combine(_allApps, _search, _hiddenApps, settings, _appUsage, _iconOverrides) { args ->
        @Suppress("UNCHECKED_CAST")
        val apps = args[0] as List<AppInfo>
        val q = args[1] as String
        @Suppress("UNCHECKED_CAST")
        val hidden = args[2] as Set<String>
        val s = args[3] as LauncherSettings
        @Suppress("UNCHECKED_CAST")
        val usage = args[4] as Map<String, Long>
        @Suppress("UNCHECKED_CAST")
        val overrides = args[5] as Map<String, String>
        val sourceMap = apps.associateBy { it.key }
        fun AppInfo.withOverride(): AppInfo {
            val sourceIcon = overrides[key]?.let { sourceMap[it]?.icon } ?: return this
            return copy(icon = sourceIcon)
        }
        val visible = apps.filter { it.key !in hidden }.map { it.withOverride() }
        if (q.isBlank()) {
            when (s.drawerSort) {
                DrawerSort.NAME -> visible.sortedBy { it.label.lowercase() }
                DrawerSort.REVERSE_NAME -> visible.sortedByDescending { it.label.lowercase() }
                DrawerSort.MOST_USED -> visible.sortedByDescending { usage[it.key] ?: 0L }
                DrawerSort.RECENT_INSTALL -> visible.sortedByDescending { it.firstInstallTime }
            }
        } else {
            // Fuzzy search with relevance scoring
            visible.mapNotNull { app ->
                val score = SearchScorer.score(app.label, app.packageName, q)
                if (score > 0) app to score else null
            }.sortedByDescending { it.second }.map { it.first }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Categorized apps for drawer tabs — skip computation during search (categories hidden)
    val categorizedApps: StateFlow<Map<DrawerCategory, List<AppInfo>>> = combine(filteredApps, _search, settings) { apps, query, s ->
        if (query.isNotBlank() || !s.drawerCategories) emptyMap()
        else AppCategorizer.categorizeAll(apps, s.categoryRules)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private val _selectedCategory = MutableStateFlow(DrawerCategory.ALL)
    val selectedCategory: StateFlow<DrawerCategory> = _selectedCategory.asStateFlow()
    fun setSelectedCategory(c: DrawerCategory) {
        _selectedCategory.value = c
        if (c != DrawerCategory.ALL) _selectedDrawerGroupId.value = null
    }
    private val _selectedDrawerTab = MutableStateFlow(DrawerTab.ALL)
    val selectedDrawerTab: StateFlow<DrawerTab> = _selectedDrawerTab.asStateFlow()
    fun setSelectedDrawerTab(tab: DrawerTab) {
        _selectedDrawerTab.value = tab
        if (tab != DrawerTab.ALL) _selectedCategory.value = DrawerCategory.ALL
    }
    private val _selectedDrawerGroupId = MutableStateFlow<String?>(null)
    val selectedDrawerGroupId: StateFlow<String?> = _selectedDrawerGroupId.asStateFlow()
    fun setSelectedDrawerGroup(groupId: String?) {
        val selected = groupId?.takeIf { id -> settings.value.drawerGroups.any { it.id == id && it.enabled } }
        _selectedDrawerGroupId.value = selected
        if (selected != null) _selectedCategory.value = DrawerCategory.ALL
    }
    fun resetDrawerFilters() {
        _selectedCategory.value = DrawerCategory.ALL
        _selectedDrawerTab.value = DrawerTab.ALL
        _selectedDrawerGroupId.value = null
    }

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

    val favoriteApps: StateFlow<List<AppInfo>> = combine(filteredApps, _favoriteApps) { apps, favorites ->
        if (favorites.isEmpty()) emptyList()
        else apps.filter { it.key in favorites }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val workProfileApps: StateFlow<List<AppInfo>> = filteredApps
        .map { apps -> apps.filter { it.isWorkProfile } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

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

    // Uninstall confirmation
    data class UninstallConfirm(val app: AppInfo, val source: DragSource?, val sourceIndex: Int?)
    private val _uninstallConfirm = MutableStateFlow<UninstallConfirm?>(null)
    val uninstallConfirm: StateFlow<UninstallConfirm?> = _uninstallConfirm.asStateFlow()
    fun requestUninstall(app: AppInfo, source: DragSource? = null, sourceIndex: Int? = null) { _uninstallConfirm.value = UninstallConfirm(app, source, sourceIndex) }
    fun dismissUninstall() { _uninstallConfirm.value = null }
    fun confirmUninstall() {
        val confirm = _uninstallConfirm.value ?: return
        uninstall(confirm.app)
        if (confirm.source != null && confirm.sourceIndex != null) removeFromGrid(confirm.source, confirm.sourceIndex)
        _uninstallConfirm.value = null; _homeMenu.value = null; _shortcuts.value = emptyList()
    }

    private val _editMode = MutableStateFlow(false)
    val editMode: StateFlow<Boolean> = _editMode.asStateFlow()

    private var reloadJob: Job? = null
    private var smartspaceRefreshJob: Job? = null
    private var webSuggestionJob: Job? = null
    private var flashlightOn = false
    private var torchCallback: CameraManager.TorchCallback? = null

    init {
        loadApps()
        discoverIconPacks()
        startSmartspaceRefresh()
        viewModelScope.launch {
            NotificationListener.counts.collect { counts ->
                _smartspace.value = _smartspace.value.withUnread(counts)
            }
        }
        viewModelScope.launch { prefs.homeGrid.collect { if (it.isNotEmpty()) _homeGrid.value = it } }
        viewModelScope.launch { prefs.dockGrid.collect { if (it.isNotEmpty()) _dockGrid.value = it } }
        viewModelScope.launch { prefs.shortcutShelf.collect { _shortcutShelf.value = it } }
        viewModelScope.launch { prefs.hiddenApps.collect { _hiddenApps.value = it } }
        viewModelScope.launch { prefs.favoriteApps.collect { _favoriteApps.value = it } }
        viewModelScope.launch { prefs.customLabels.collect { _customLabels.value = it } }
        viewModelScope.launch { prefs.iconOverrides.collect { _iconOverrides.value = it } }
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
            settings.filter { it.iconPacks.isNotEmpty() }.take(1).collect { s ->
                applyIconPacks(s.iconPacks)
            }
        }
        viewModelScope.launch {
            backupScheduleState.filter { it.enabled }.take(1).collect {
                BackupScheduler.schedule(ctx)
            }
        }
        // Sync flashlight state when user toggles via quick settings
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
                if (cm != null) {
                    torchCallback = object : CameraManager.TorchCallback() {
                        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                            flashlightOn = enabled
                        }
                    }
                    cm.registerTorchCallback(torchCallback!!, null)
                }
            } catch (e: Exception) { Log.w(TAG, "Torch callback registration failed", e) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && torchCallback != null) {
            try {
                val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
                cm?.unregisterTorchCallback(torchCallback!!)
            } catch (_: Exception) {}
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

    fun resolveApp(key: String): AppInfo? = _appMap.value[key]?.let { app ->
        val sourceKey = _iconOverrides.value[app.key] ?: return@let app
        val sourceIcon = _appMap.value[sourceKey]?.icon ?: return@let app
        app.copy(icon = sourceIcon)
    }
    fun getLabel(key: String): String? = _customLabels.value[key] ?: resolveApp(key)?.label

    fun resolveShortcutIcon(cell: GridCell.Shortcut): android.graphics.drawable.Drawable? {
        val overrideIcon = _iconOverrides.value[cell.key]?.let { sourceKey -> _appMap.value[sourceKey]?.icon }
        return overrideIcon ?: resolveApp(cell.sourceAppKey)?.icon
    }

    fun setIconOverride(targetKey: String, sourceAppKey: String) { viewModelScope.launch {
        val source = _appMap.value[sourceAppKey] ?: return@launch
        val map = _iconOverrides.value.toMutableMap()
        if (targetKey == source.key) map.remove(targetKey) else map[targetKey] = source.key
        val cleaned = sanitizeIconOverrides(map)
        _iconOverrides.value = cleaned
        prefs.saveIconOverrides(cleaned)
        toast(R.string.icon_override_applied)
    }}

    fun clearIconOverride(targetKey: String) { viewModelScope.launch {
        val map = _iconOverrides.value.toMutableMap()
        if (map.remove(targetKey) != null) {
            _iconOverrides.value = map
            prefs.saveIconOverrides(map)
            toast(R.string.icon_override_reset)
        }
    }}

    fun launch(app: AppInfo) {
        if (!repo.isAppAvailable(app)) {
            toast(R.string.app_no_longer_installed)
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
        if (q.length >= 2) {
            searchContacts(q)
            loadWebSuggestions(q)
        } else {
            _contactResults.value = emptyList()
            webSuggestionJob?.cancel()
            _webSuggestions.value = emptyList()
        }
    }
    fun openDrawer() { _drawerOpen.value = true }
    fun closeDrawer() { _drawerOpen.value = false; _search.value = ""; _webSuggestions.value = emptyList(); webSuggestionJob?.cancel(); resetDrawerFilters() }
    fun openSettings() { _settingsOpen.value = true }
    fun closeSettings() { _settingsOpen.value = false }
    fun closeAllOverlays() { _drawerOpen.value = false; _settingsOpen.value = false; _openFolder.value = null; _drawerMenuApp.value = null; _folderRename.value = null; _labelEdit.value = null; _homeMenu.value = null; _editMode.value = false; _widgetPickerOpen.value = false; _widgetRemoveConfirm.value = null; _homeSpaceMenu.value = false; _search.value = ""; _webSuggestions.value = emptyList(); webSuggestionJob?.cancel(); _shortcuts.value = emptyList(); resetDrawerFilters() }
    fun hasOpenOverlay(): Boolean = _drawerOpen.value || _settingsOpen.value || _openFolder.value != null || _drawerMenuApp.value != null || _labelEdit.value != null || _homeMenu.value != null || _widgetPickerOpen.value || _widgetRemoveConfirm.value != null || _editMode.value || _homeSpaceMenu.value

    // -- Home/Dock Context Menu --

    fun showHomeMenu(cell: GridCell, source: DragSource, index: Int) {
        if (settings.value.homeLocked) { toast(R.string.home_screen_locked); return }
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

    fun enterEditMode() { if (settings.value.homeLocked) { toast(R.string.home_screen_locked); return }; _homeMenu.value = null; _shortcuts.value = emptyList(); _editMode.value = true }
    fun exitEditMode() { _editMode.value = false }

    // -- App Shortcuts --

    fun launchShortcut(shortcut: AppShortcut) {
        if (!shortcutRepo.launchShortcut(shortcut)) toast(R.string.shortcut_unavailable)
        _homeMenu.value = null; _shortcuts.value = emptyList()
    }

    fun launchShortcutCell(cell: GridCell.Shortcut) {
        if (!shortcutRepo.launchShortcut(cell.packageName, cell.shortcutId)) {
            toast(R.string.shortcut_unavailable)
            debouncedReload()
        }
    }

    private fun appShortcutCell(shortcut: AppShortcut, sourceAppKey: String): GridCell.Shortcut =
        GridCell.Shortcut(
            packageName = shortcut.packageName,
            shortcutId = shortcut.id,
            label = shortcut.shortLabel.toString().take(80),
            sourceAppKey = sourceAppKey,
        )

    fun pinShortcutToHome(shortcut: AppShortcut, sourceAppKey: String) { viewModelScope.launch {
        val cell = appShortcutCell(shortcut, sourceAppKey)
        val ps = pageSize()
        val grid = padGrid(_homeGrid.value, ps).toMutableList()
        if (grid.any { it is GridCell.Shortcut && it.key == cell.key }) {
            toast(R.string.shortcut_already_pinned)
            _homeMenu.value = null; _drawerMenuApp.value = null; _shortcuts.value = emptyList()
            return@launch
        }
        var i = grid.indexOfFirst { it == null }
        if (i < 0) { grid.addAll(List(ps) { null }); i = grid.indexOfFirst { it == null } }
        if (i >= 0) {
            grid[i] = cell
            _homeGrid.value = grid
            prefs.saveHome(grid)
            toast(R.string.shortcut_added_to_home)
        }
        _homeMenu.value = null; _drawerMenuApp.value = null; _shortcuts.value = emptyList()
    }}

    fun pinShortcutToDock(shortcut: AppShortcut, sourceAppKey: String) { viewModelScope.launch {
        val cell = appShortcutCell(shortcut, sourceAppKey)
        val dc = settings.value.dockCount
        val dock = _dockGrid.value.toMutableList()
        while (dock.size < dc) dock.add(null)
        if (dock.any { it is GridCell.Shortcut && it.key == cell.key }) {
            toast(R.string.shortcut_already_pinned)
            _homeMenu.value = null; _drawerMenuApp.value = null; _shortcuts.value = emptyList()
            return@launch
        }
        val i = dock.indexOfFirst { it == null }
        if (i < 0) {
            toast(R.string.dock_full)
        } else {
            dock[i] = cell
            _dockGrid.value = dock
            prefs.saveDock(dock)
            toast(R.string.shortcut_added_to_dock)
        }
        _homeMenu.value = null; _drawerMenuApp.value = null; _shortcuts.value = emptyList()
    }}

    fun pinShortcutToShelf(shortcut: AppShortcut, sourceAppKey: String) { viewModelScope.launch {
        val cell = appShortcutCell(shortcut, sourceAppKey)
        val shelf = sanitizeShortcutShelf(_shortcutShelf.value).toMutableList()
        if (shelf.any { it.key == cell.key }) {
            toast(R.string.shortcut_already_pinned)
            _homeMenu.value = null; _drawerMenuApp.value = null; _shortcuts.value = emptyList()
            return@launch
        }
        if (shelf.size >= MAX_SHORTCUT_SHELF_ITEMS) {
            toast(R.string.shortcut_shelf_full)
            _homeMenu.value = null; _drawerMenuApp.value = null; _shortcuts.value = emptyList()
            return@launch
        }
        shelf.add(cell)
        val cleaned = sanitizeShortcutShelf(shelf)
        _shortcutShelf.value = cleaned
        prefs.saveShortcutShelf(cleaned)
        toast(R.string.shortcut_added_to_shelf)
        _homeMenu.value = null; _drawerMenuApp.value = null; _shortcuts.value = emptyList()
    }}

    fun removeShortcutFromShelf(cell: GridCell.Shortcut) { viewModelScope.launch {
        val shelf = sanitizeShortcutShelf(_shortcutShelf.value).filterNot { it.key == cell.key }
        if (shelf.size != _shortcutShelf.value.size) {
            _shortcutShelf.value = shelf
            prefs.saveShortcutShelf(shelf)
            toast(R.string.shortcut_removed_from_shelf)
        }
    }}

    fun bindAppSwipeShortcut(appKey: String, shortcut: AppShortcut) { viewModelScope.launch {
        val app = _appMap.value[appKey] ?: return@launch
        val map = settings.value.appGestureShortcuts.toMutableMap()
        map[app.key] = shortcut.key
        val cleaned = sanitizeAppGestureShortcuts(map)
        prefs.saveAppGestureShortcuts(cleaned)
        toast(R.string.app_swipe_shortcut_bound)
        _homeMenu.value = null; _drawerMenuApp.value = null; _shortcuts.value = emptyList()
    }}

    fun clearAppSwipeShortcut(appKey: String) { viewModelScope.launch {
        val map = settings.value.appGestureShortcuts.toMutableMap()
        if (map.remove(appKey) != null) {
            prefs.saveAppGestureShortcuts(map)
            toast(R.string.app_swipe_shortcut_cleared)
        }
        _homeMenu.value = null; _drawerMenuApp.value = null; _shortcuts.value = emptyList()
    }}

    fun launchAppSwipeShortcut(appKey: String): Boolean {
        val shortcutKey = settings.value.appGestureShortcuts[appKey] ?: return false
        val parts = shortcutPartsFromKey(shortcutKey) ?: return false
        val launched = shortcutRepo.launchShortcut(parts.first, parts.second)
        if (!launched) {
            toast(R.string.shortcut_unavailable)
            debouncedReload()
        }
        return launched
    }

    fun loadShortcutsForDrawerMenu(app: AppInfo) {
        viewModelScope.launch { _shortcuts.value = shortcutRepo.getShortcuts(app.packageName) }
    }

    // -- Icon Packs --

    private fun discoverIconPacks() { viewModelScope.launch {
        _availablePacks.value = try { iconPackManager.getInstalledPacks() } catch (e: Exception) { Log.e(TAG, "Icon pack discovery failed", e); emptyList() }
    }}

    fun setIconPack(packageName: String) { setIconPackChain(listOf(packageName)) }

    fun setIconPackChain(packageNames: List<String>) { viewModelScope.launch {
        val chain = sanitizeIconPackChain(packageNames)
        prefs.set(LauncherPrefs.ICON_PACK, chain.firstOrNull().orEmpty())
        prefs.set(LauncherPrefs.ICON_PACKS, serializeIconPackChain(chain))
        applyIconPacks(chain)
    }}

    fun clearIconPack() { viewModelScope.launch {
        prefs.set(LauncherPrefs.ICON_PACK, "")
        prefs.set(LauncherPrefs.ICON_PACKS, "")
        iconPackManager.clearPack(); loadAppsInternal(); toast(R.string.system_icons_restored)
    }}

    fun refreshIconPacks() { discoverIconPacks() }
    fun getIconPackPreviewAsync(packageName: String, callback: (List<android.graphics.drawable.Drawable?>) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val icons = iconPackManager.previewIcons(packageName, 4)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { callback(icons) }
        }
    }

    private suspend fun applyIconPack(packageName: String) {
        applyIconPacks(listOf(packageName))
    }

    private suspend fun applyIconPacks(packageNames: List<String>) {
        val chain = sanitizeIconPackChain(packageNames)
        if (chain.isEmpty()) { iconPackManager.clearPack(); loadAppsInternal(); return }
        _iconPackLoading.value = true
        try {
            val ok = iconPackManager.loadPacks(chain)
            if (ok) {
                loadAppsInternal()
                if (chain.size == 1) {
                    toast(R.string.icon_pack_applied, iconPackManager.mappedCount())
                } else {
                    toast(R.string.icon_pack_mixer_applied, chain.size, iconPackManager.mappedCount())
                }
            }
            else toast(R.string.failed_to_load_icon_pack)
        } catch (e: Exception) {
            Log.e(TAG, "Icon pack apply failed", e)
            toast(R.string.icon_pack_error)
        } finally {
            _iconPackLoading.value = false
        }
    }

    // -- Gestures --

    fun executeGesture(action: GestureAction, gestureSource: String = "") {
        try {
            when (action) {
                GestureAction.NONE -> {}
                GestureAction.LOCK_SCREEN -> lockScreen()
                GestureAction.NOTIFICATION_SHADE -> expandNotifications()
                GestureAction.APP_DRAWER -> openDrawer()
                GestureAction.SETTINGS -> openSettings()
                GestureAction.KILL_APPS -> killBackgroundApps()
                GestureAction.FLASHLIGHT -> toggleFlashlight()
                GestureAction.EDIT_MODE -> { if (!settings.value.homeLocked) { _editMode.value = !_editMode.value } else toast(R.string.home_screen_locked) }
                GestureAction.RECENT_APP -> {
                    val hidden = _hiddenApps.value
                    val lastUsed = _appUsage.value.entries
                        .filter { it.key !in hidden }
                        .sortedByDescending { it.value }
                        .drop(1) // skip the most recent (current) app
                        .firstOrNull()
                    lastUsed?.let { resolveApp(it.key)?.let { app -> launch(app) } }
                }
                GestureAction.LAUNCH_APP -> {
                    val appKey = when (gestureSource) {
                        "double_tap" -> settings.value.gestureAppDoubleTap
                        "swipe_down" -> settings.value.gestureAppSwipeDown
                        "triple_tap" -> settings.value.gestureAppTripleTap
                        "pinch" -> settings.value.gestureAppPinch
                        "dock_tap" -> settings.value.gestureAppDockTap
                        "swipe_up" -> settings.value.gestureAppSwipeUp
                        "custom_gesture" -> settings.value.gestureAppCustom
                        else -> ""
                    }
                    if (appKey.isNotBlank()) resolveApp(appKey)?.let { launch(it) }
                    else toast(R.string.no_app_configured_for_gesture)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gesture execution failed: $action", e)
        }
    }

    fun executeCustomGesture(points: List<CustomGesturePoint>): Boolean {
        val current = settings.value
        if (current.customGesturePattern.isBlank() || current.customGestureAction == GestureAction.NONE) return false
        if (!customGestureMatches(current.customGesturePattern, points)) return false
        executeGesture(current.customGestureAction, "custom_gesture")
        return true
    }

    fun launchAssistantReplacement() {
        try {
            val assistantApp = settings.value.assistantApp
            if (assistantApp.isNotBlank()) {
                val app = resolveApp(assistantApp)
                if (app != null) {
                    launch(app)
                } else {
                    toast(R.string.app_no_longer_installed)
                    debouncedReload()
                }
                return
            }

            val intent = Intent(Intent.ACTION_ASSIST).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            if (intent.resolveActivity(ctx.packageManager) != null) {
                ctx.startActivity(intent)
            } else {
                toast(R.string.assistant_unavailable)
            }
        } catch (e: Exception) {
            Log.e(TAG, "launchAssistantReplacement failed", e)
            toast(R.string.assistant_unavailable)
        }
    }

    fun toggleFlashlight() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        try {
            val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return
            val cameraId = cm.cameraIdList.firstOrNull() ?: return
            flashlightOn = !flashlightOn
            cm.setTorchMode(cameraId, flashlightOn)
            toast(if (flashlightOn) R.string.flashlight_on else R.string.flashlight_off)
        } catch (e: Exception) {
            Log.e(TAG, "toggleFlashlight failed", e)
            flashlightOn = false
        }
    }

    fun lockScreen() { runCatching {
        val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager ?: return
        if (dpm.isAdminActive(ComponentName(ctx, AdminReceiver::class.java))) dpm.lockNow()
        else toast(R.string.enable_device_admin_to_lock)
    }.onFailure { Log.e(TAG, "lockScreen failed", it) }}

    fun isDeviceAdminEnabled(): Boolean = runCatching {
        (ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager)?.isAdminActive(ComponentName(ctx, AdminReceiver::class.java)) ?: false
    }.getOrDefault(false)

    fun requestDeviceAdmin() { runCatching {
        ctx.startActivity(Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, ComponentName(ctx, AdminReceiver::class.java))
            putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, ctx.getString(R.string.device_admin_lock_explanation))
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
    }.onFailure { Log.e(TAG, "requestDeviceAdmin failed", it) }}

    @Suppress("DEPRECATION")
    @SuppressLint("WrongConstant")
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
            toast(R.string.cleared_background_apps, n)
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

    fun areCrashNotificationsEnabled(): Boolean =
        NotificationManagerCompat.from(ctx).areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

    fun openAppNotificationSettings() {
        try {
            ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
        } catch (e: Exception) {
            Log.e(TAG, "openAppNotificationSettings failed", e)
        }
    }

    fun diagnosticReports(): List<DiagnosticReportSummary> = DiagnosticsStore.listReports(ctx)

    fun copyDiagnosticReport(fileName: String? = null) {
        val text = if (fileName == null) DiagnosticsStore.buildSupportBundle(ctx) else DiagnosticsStore.readReport(ctx, fileName)
        if (text.isNullOrBlank()) {
            toast(R.string.no_diagnostics_available)
            return
        }
        runCatching {
            val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText(ctx.getString(R.string.diagnostics_clip_label), text))
            toast(R.string.diagnostics_copied)
        }.onFailure { Log.e(TAG, "copyDiagnosticReport failed", it) }
    }

    fun shareDiagnosticReport(fileName: String? = null) {
        val text = if (fileName == null) DiagnosticsStore.buildSupportBundle(ctx) else DiagnosticsStore.readReport(ctx, fileName)
        if (text.isNullOrBlank()) {
            toast(R.string.no_diagnostics_available)
            return
        }
        runCatching {
            ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, ctx.getString(R.string.diagnostics_share_subject))
                putExtra(Intent.EXTRA_TEXT, text)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }, ctx.getString(R.string.share_diagnostics)).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
        }.onFailure { Log.e(TAG, "shareDiagnosticReport failed", it) }
    }

    fun deleteDiagnosticReport(fileName: String) {
        if (DiagnosticsStore.deleteReport(ctx, fileName)) toast(R.string.diagnostic_report_deleted)
        else toast(R.string.delete_failed)
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

    fun hideApp(key: String) { viewModelScope.launch { val u = _hiddenApps.value + key; _hiddenApps.value = u; prefs.saveHidden(u); toast(R.string.hidden_from_drawer); _drawerMenuApp.value = null; _shortcuts.value = emptyList() } }
    fun unhideApp(key: String) { viewModelScope.launch { val u = _hiddenApps.value - key; _hiddenApps.value = u; prefs.saveHidden(u) } }

    // -- Page Helpers --

    fun pageSize(): Int { val s = settings.value; return (s.gridColumns * s.gridRows).coerceAtLeast(1) }
    fun numPages(): Int { val ps = pageSize(); return max(1, (_homeGrid.value.size + ps - 1) / ps) }
    fun addPage() { viewModelScope.launch {
        val ps = pageSize()
        val grid = _homeGrid.value.toMutableList()
        grid.addAll(List(ps) { null })
        _homeGrid.value = grid; prefs.saveHome(grid)
        toast(R.string.page_added)
    }}
    fun removePage(pageIndex: Int) { viewModelScope.launch {
        val ps = pageSize(); val np = numPages()
        if (np <= 1) { toast(R.string.cant_remove_last_page); return@launch }
        val start = pageIndex * ps; val end = (start + ps).coerceAtMost(_homeGrid.value.size)
        val pageSlice = _homeGrid.value.subList(start, end)
        if (pageSlice.any { it != null }) { toast(R.string.page_not_empty_clear_first); return@launch }
        val grid = _homeGrid.value.toMutableList()
        grid.subList(start, end).clear()
        _homeGrid.value = grid; prefs.saveHome(grid)
        // Remove widgets on this page and shift widgets on later pages
        val updated = _widgets.value.mapNotNull { w ->
            when { w.page == pageIndex -> null; w.page > pageIndex -> w.copy(page = w.page - 1); else -> w }
        }
        _widgets.value = updated; prefs.saveWidgets(updated)
        val shiftedDims = settings.value.pageWallpaperDims.mapNotNull { (page, dim) ->
            when {
                page == pageIndex -> null
                page > pageIndex -> page - 1 to dim
                else -> page to dim
            }
        }.toMap()
        prefs.savePageWallpaperDims(shiftedDims)
        toast(R.string.page_removed)
    }}

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
            val valid = apps.map { it.key }.toSet()
            val validPackages = apps.map { it.packageName }.toSet()
            var c = false
            val home = _homeGrid.value.toMutableList()
            for (i in home.indices) { val r = cleanCell(home[i], valid); if (r !== home[i]) { home[i] = r; c = true } }
            if (c) { _homeGrid.value = home; prefs.saveHome(home) }
            c = false; val dock = _dockGrid.value.toMutableList()
            for (i in dock.indices) { val r = cleanCell(dock[i], valid); if (r !== dock[i]) { dock[i] = r; c = true } }
            if (c) { _dockGrid.value = dock; prefs.saveDock(dock) }
            val favorites = _favoriteApps.value.filter { it in valid }.toSet()
            if (favorites.size != _favoriteApps.value.size) {
                _favoriteApps.value = favorites
                prefs.saveFavoriteApps(favorites)
            }
            val groups = settings.value.drawerGroups
            val cleanedGroups = sanitizeDrawerGroups(groups.map { group ->
                group.copy(appKeys = group.appKeys.filter { it in valid }.toSet())
            })
            if (cleanedGroups != groups) {
                prefs.saveDrawerGroups(cleanedGroups)
                if (_selectedDrawerGroupId.value !in cleanedGroups.map { it.id }.toSet()) {
                    _selectedDrawerGroupId.value = null
                }
            }
            val overrides = sanitizeIconOverrides(_iconOverrides.value)
                .filter { (target, source) ->
                    source in valid && (target in valid || (target.startsWith("shortcut:") && target.removePrefix("shortcut:").substringBefore("/") in validPackages))
                }
            if (overrides.size != _iconOverrides.value.size) {
                _iconOverrides.value = overrides
                prefs.saveIconOverrides(overrides)
            }
            val gestureShortcuts = settings.value.appGestureShortcuts
            val cleanedGestureShortcuts = sanitizeAppGestureShortcuts(gestureShortcuts)
                .filter { (appKey, shortcutKey) ->
                    val shortcutPackage = shortcutPartsFromKey(shortcutKey)?.first
                    appKey in valid && shortcutPackage != null && shortcutPackage in validPackages
                }
            if (cleanedGestureShortcuts != gestureShortcuts) {
                prefs.saveAppGestureShortcuts(cleanedGestureShortcuts)
            }
            val shelf = _shortcutShelf.value
            val cleanedShelf = sanitizeShortcutShelf(shelf).filter { shortcut ->
                shortcut.sourceAppKey in valid && shortcut.packageName in validPackages
            }
            if (cleanedShelf != shelf) {
                _shortcutShelf.value = cleanedShelf
                prefs.saveShortcutShelf(cleanedShelf)
            }
            if (settings.value.assistantApp.isNotBlank() && settings.value.assistantApp !in valid) {
                prefs.set(LauncherPrefs.ASSISTANT_APP, "")
            }
        } catch (e: Exception) {
            Log.e(TAG, "cleanupStaleKeys failed", e)
        }
    }
    private fun cleanCell(cell: GridCell?, valid: Set<String>): GridCell? = when (cell) {
        is GridCell.App -> if (cell.appKey in valid) cell else null
        is GridCell.Shortcut -> if (cell.sourceAppKey in valid) cell else null
        is GridCell.Folder -> {
            val f = cell.appKeys.filter { it in valid }
            val coverAppKey = cell.coverAppKey.takeIf { it in valid }.orEmpty()
            when {
                f.isEmpty() -> null
                f.size == 1 -> GridCell.App(f[0])
                f.size != cell.appKeys.size || coverAppKey != cell.coverAppKey -> cell.copy(appKeys = f, coverAppKey = coverAppKey)
                else -> cell
            }
        }
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
    private fun updateFolder(src: DragSource, idx: Int, transform: (GridCell.Folder) -> GridCell.Folder) { viewModelScope.launch {
        val g = gridForSource(src).toMutableList()
        val folder = g.getOrNull(idx) as? GridCell.Folder ?: return@launch
        val updated = transform(folder)
        g[idx] = updated
        saveGrid(src, g)
        _openFolder.value?.let { (_, oSrc, oIdx) ->
            if (oSrc == src && oIdx == idx) _openFolder.value = Triple(updated, src, idx)
        }
    }}
    fun setFolderCoverEmoji(src: DragSource, idx: Int, emoji: String) {
        val clean = emoji.trim().take(4)
        if (clean.isBlank()) return
        updateFolder(src, idx) { it.copy(coverEmoji = clean, coverAppKey = "") }
    }
    fun setFolderCoverApp(src: DragSource, idx: Int, appKey: String) {
        if (resolveApp(appKey) == null) return
        updateFolder(src, idx) { it.copy(coverEmoji = "", coverAppKey = appKey) }
    }
    fun clearFolderCover(src: DragSource, idx: Int) {
        updateFolder(src, idx) { it.copy(coverEmoji = "", coverAppKey = "") }
    }
    fun removeFolderApp(src: DragSource, idx: Int, key: String) { viewModelScope.launch {
        val g = gridForSource(src).toMutableList(); val c = g.getOrNull(idx) as? GridCell.Folder ?: return@launch
        val f = c.appKeys.filter { it != key }
        g[idx] = when {
            f.isEmpty() -> null
            f.size == 1 -> GridCell.App(f[0])
            else -> c.copy(appKeys = f, coverAppKey = c.coverAppKey.takeIf { it != key }.orEmpty())
        }
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

    fun toggleFavorite(app: AppInfo) { viewModelScope.launch {
        val favorites = _favoriteApps.value.toMutableSet()
        val added = favorites.add(app.key)
        if (!added) favorites.remove(app.key)
        _favoriteApps.value = favorites
        prefs.saveFavoriteApps(favorites)
        toast(if (added) R.string.added_to_favorites else R.string.removed_from_favorites)
        _drawerMenuApp.value = null; _shortcuts.value = emptyList()
    }}

    fun pinToHome(app: AppInfo) { viewModelScope.launch {
        val ps = pageSize(); val grid = padGrid(_homeGrid.value, ps).toMutableList()
        if (grid.any { it is GridCell.App && it.appKey == app.key }) { toast(R.string.already_on_home); _drawerMenuApp.value = null; _shortcuts.value = emptyList(); return@launch }
        var i = grid.indexOfFirst { it == null }; if (i < 0) { grid.addAll(List(ps) { null }); i = grid.indexOfFirst { it == null } }
        if (i >= 0) { grid[i] = GridCell.App(app.key); _homeGrid.value = grid; prefs.saveHome(grid); toast(R.string.added_to_home) }
        _drawerMenuApp.value = null; _shortcuts.value = emptyList()
    }}

    fun pinToDock(app: AppInfo) { viewModelScope.launch {
        val dc = settings.value.dockCount; val dock = _dockGrid.value.toMutableList(); while (dock.size < dc) dock.add(null)
        if (dock.any { it is GridCell.App && it.appKey == app.key }) { toast(R.string.already_in_dock); _drawerMenuApp.value = null; _shortcuts.value = emptyList(); return@launch }
        val i = dock.indexOfFirst { it == null }; if (i < 0) { toast(R.string.dock_full); _drawerMenuApp.value = null; _shortcuts.value = emptyList(); return@launch }
        dock[i] = GridCell.App(app.key); _dockGrid.value = dock; prefs.saveDock(dock); toast(R.string.added_to_dock); _drawerMenuApp.value = null; _shortcuts.value = emptyList()
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
                    _hoverUninstall.value -> { if (drag.item is GridCell.App) { clearSourceCell(drag); drag.appInfo?.let { requestUninstall(it) } } }
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
            is GridCell.Shortcut -> {
                when {
                    ex == null -> { if (same && drag.sourceIndex in tg.indices) tg[drag.sourceIndex] = null; tg[ti] = item; saveTrimmed(target, tg); if (!same) clearSourceCell(drag) }
                    ex is GridCell.Shortcut && ex.key == item.key -> {}
                    same && drag.sourceIndex in tg.indices -> { tg[ti] = item; tg[drag.sourceIndex] = ex; saveTrimmed(target, tg) }
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
        val tokens = listOf(k1.substringBefore("/"), k2.substringBefore("/"))
            .flatMap { it.lowercase().split('.', '_', '-') }.toSet()
        return when {
            tokens.any { it in setOf("messaging", "dialer", "contacts", "whatsapp", "telegram", "messenger", "sms", "mms", "chat") } -> "Social"
            tokens.any { it in setOf("camera", "photos", "gallery") } -> "Photos"
            tokens.any { it in setOf("chrome", "browser", "firefox", "edge", "brave", "opera") } -> "Internet"
            tokens.any { it in setOf("music", "spotify", "youtube", "video", "player", "podcast") } -> "Media"
            tokens.any { it in setOf("settings", "calculator", "clock", "calendar", "weather", "files") } -> "Tools"
            tokens.any { it in setOf("game", "games", "puzzle", "arcade") } -> "Games"
            else -> "Folder"
        }
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
        val match = unitRegex.matchEntire(cleaned) ?: return null
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

    // -- Smartspace --

    fun refreshSmartspace() {
        if (smartspaceRefreshJob?.isActive == true) return
        smartspaceRefreshJob = viewModelScope.launch { refreshSmartspaceInternal() }
    }

    private fun startSmartspaceRefresh() {
        viewModelScope.launch {
            while (true) {
                refreshSmartspaceInternal()
                delay(SMARTSPACE_REFRESH_MS)
            }
        }
    }

    private suspend fun refreshSmartspaceInternal() {
        _smartspace.value = smartspaceService.refresh().withUnread(NotificationListener.counts.value)
    }

    private fun SmartspaceState.withUnread(counts: Map<String, Int>): SmartspaceState =
        copy(unread = SmartspaceUnreadAggregator.summarize(counts, ctx.packageName))

    fun hasCalendarPermission(): Boolean = smartspaceService.hasCalendarPermission()

    fun hasLocationPermission(): Boolean = smartspaceService.hasLocationPermission()

    fun openWeatherApp() {
        try {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.google.com/search?q=weather")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
        } catch (e: Exception) {
            Log.e(TAG, "openWeatherApp failed", e)
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

    fun clearRecentApps() {
        viewModelScope.launch {
            _appUsage.value = emptyMap()
            prefs.saveAppUsage(emptyMap())
            toast(R.string.recents_cleared)
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
    fun setPageWallpaperDim(pageIndex: Int, v: Int) {
        if (pageIndex < 0) return
        viewModelScope.launch {
            val dims = settings.value.pageWallpaperDims.toMutableMap()
            dims[pageIndex] = v.coerceIn(0, 80)
            prefs.savePageWallpaperDims(dims)
        }
    }
    fun resetPageWallpaperDim(pageIndex: Int) {
        if (pageIndex < 0) return
        viewModelScope.launch {
            val dims = settings.value.pageWallpaperDims.toMutableMap()
            dims.remove(pageIndex)
            prefs.savePageWallpaperDims(dims)
        }
    }
    fun setShowNotifBadges(v: Boolean) = pref(LauncherPrefs.SHOW_NOTIF_BADGES, v)
    fun setDrawerSort(s: DrawerSort) = pref(LauncherPrefs.DRAWER_SORT, s.name)
    fun setLabelStyle(s: LabelStyle) = pref(LauncherPrefs.LABEL_STYLE, s.name)
    fun setThemedIcons(v: Boolean) { pref(LauncherPrefs.THEMED_ICONS, v); viewModelScope.launch { loadAppsInternal() } }
    fun setPageTransition(t: PageTransition) = pref(LauncherPrefs.PAGE_TRANSITION, t.name)
    fun setBadgeStyle(s: BadgeStyle) = pref(LauncherPrefs.BADGE_STYLE, s.name)
    fun setGridPaddingH(v: Int) = pref(LauncherPrefs.GRID_PADDING_H, v.coerceIn(0, 24))
    fun setGridPaddingV(v: Int) = pref(LauncherPrefs.GRID_PADDING_V, v.coerceIn(0, 24))
    fun setHideStatusBar(v: Boolean) = pref(LauncherPrefs.HIDE_STATUS_BAR, v)
    fun setDrawerColumns(c: Int) = pref(LauncherPrefs.DRAWER_COLUMNS, c.coerceIn(0, 6))
    fun setHomeLocked(v: Boolean) { pref(LauncherPrefs.HOME_LOCKED, v); if (v) { _editMode.value = false }; toast(if (v) R.string.home_screen_locked_toast else R.string.home_screen_unlocked_toast) }
    fun setIconShadow(v: Boolean) = pref(LauncherPrefs.ICON_SHADOW, v)
    fun setAccentOverride(hex: String) = pref(LauncherPrefs.ACCENT_OVERRIDE, hex)
    fun setDynamicColor(v: Boolean) = pref(LauncherPrefs.DYNAMIC_COLOR, v)
    fun setCustomFont(uri: String, name: String) { viewModelScope.launch {
        val cleanUri = sanitizeCustomFontUri(uri)
        val cleanName = sanitizeCustomFontName(name, cleanUri)
        val canLoad = withContext(Dispatchers.IO) { loadCustomAndroidTypeface(ctx, cleanUri) != null }
        if (!canLoad) {
            toast(R.string.custom_font_failed)
            return@launch
        }
        prefs.set(LauncherPrefs.CUSTOM_FONT_URI, cleanUri)
        prefs.set(LauncherPrefs.CUSTOM_FONT_NAME, cleanName)
        toast(R.string.custom_font_applied)
    }}
    fun clearCustomFont() { viewModelScope.launch {
        prefs.set(LauncherPrefs.CUSTOM_FONT_URI, "")
        prefs.set(LauncherPrefs.CUSTOM_FONT_NAME, "")
        toast(R.string.custom_font_cleared)
    }}
    fun setDrawerCategories(v: Boolean) = pref(LauncherPrefs.DRAWER_CATEGORIES, v)
    fun addDrawerGroup(name: String) {
        val cleanName = normalizeDrawerGroupName(name)
        if (cleanName.isBlank()) {
            toast(R.string.choose_drawer_group_name)
            return
        }
        val existing = settings.value.drawerGroups
        if (existing.any { it.name.equals(cleanName, ignoreCase = true) }) {
            toast(R.string.drawer_group_duplicate)
            return
        }
        viewModelScope.launch {
            val group = DrawerGroup(
                id = drawerGroupIdFromName(cleanName, existing.map { it.id }.toSet()),
                name = cleanName,
            )
            val groups = sanitizeDrawerGroups(existing + group)
            prefs.saveDrawerGroups(groups)
            _selectedDrawerGroupId.value = group.id
            toast(R.string.drawer_group_added)
        }
    }

    fun renameDrawerGroup(groupId: String, name: String) {
        val cleanName = normalizeDrawerGroupName(name)
        if (cleanName.isBlank()) {
            toast(R.string.choose_drawer_group_name)
            return
        }
        val existing = settings.value.drawerGroups
        if (existing.any { it.id != groupId && it.name.equals(cleanName, ignoreCase = true) }) {
            toast(R.string.drawer_group_duplicate)
            return
        }
        viewModelScope.launch {
            prefs.saveDrawerGroups(existing.map { if (it.id == groupId) it.copy(name = cleanName) else it })
            toast(R.string.drawer_group_updated)
        }
    }

    fun removeDrawerGroup(groupId: String) {
        viewModelScope.launch {
            val groups = settings.value.drawerGroups.filterNot { it.id == groupId }
            prefs.saveDrawerGroups(groups)
            if (_selectedDrawerGroupId.value == groupId) _selectedDrawerGroupId.value = null
            toast(R.string.drawer_group_removed)
        }
    }

    fun setDrawerGroupEnabled(groupId: String, enabled: Boolean) {
        viewModelScope.launch {
            val groups = settings.value.drawerGroups.map { if (it.id == groupId) it.copy(enabled = enabled) else it }
            prefs.saveDrawerGroups(groups)
            if (!enabled && _selectedDrawerGroupId.value == groupId) _selectedDrawerGroupId.value = null
        }
    }

    fun addAppToDrawerGroup(groupId: String, appKey: String) {
        if (appKey.isBlank()) return
        viewModelScope.launch {
            val groups = settings.value.drawerGroups.map { group ->
                if (group.id == groupId) group.copy(appKeys = (group.appKeys + appKey).take(200).toSet()) else group
            }
            prefs.saveDrawerGroups(groups)
        }
    }

    fun removeAppFromDrawerGroup(groupId: String, appKey: String) {
        viewModelScope.launch {
            val groups = settings.value.drawerGroups.map { group ->
                if (group.id == groupId) group.copy(appKeys = group.appKeys - appKey) else group
            }
            prefs.saveDrawerGroups(groups)
        }
    }

    fun addDrawerGroupPrefix(groupId: String, prefix: String) {
        val cleanPrefix = normalizeDrawerGroupPrefix(prefix)
        if (cleanPrefix.isBlank() || !cleanPrefix.contains(".")) {
            toast(R.string.invalid_package_prefix)
            return
        }
        viewModelScope.launch {
            val groups = settings.value.drawerGroups.map { group ->
                if (group.id == groupId) {
                    group.copy(packagePrefixes = (group.packagePrefixes + cleanPrefix).distinct().take(20))
                } else group
            }
            prefs.saveDrawerGroups(groups)
        }
    }

    fun removeDrawerGroupPrefix(groupId: String, prefix: String) {
        val cleanPrefix = normalizeDrawerGroupPrefix(prefix)
        viewModelScope.launch {
            val groups = settings.value.drawerGroups.map { group ->
                if (group.id == groupId) group.copy(packagePrefixes = group.packagePrefixes - cleanPrefix) else group
            }
            prefs.saveDrawerGroups(groups)
        }
    }

    fun addCategoryRule(type: CategoryRuleType, pattern: String, category: DrawerCategory) {
        val cleanPattern = pattern.trim().take(120)
        if (cleanPattern.isBlank() || category == DrawerCategory.ALL) {
            toast(R.string.choose_pattern_and_category)
            return
        }
        if (type == CategoryRuleType.APP_NAME_REGEX && runCatching { Regex(cleanPattern) }.isFailure) {
            toast(R.string.invalid_name_regex)
            return
        }
        viewModelScope.launch {
            val rules = (settings.value.categoryRules + AppCategoryRule(type, cleanPattern, category)).take(40)
            prefs.saveCategoryRules(rules)
            pref(LauncherPrefs.DRAWER_CATEGORIES, true)
            toast(R.string.category_rule_added)
        }
    }
    fun removeCategoryRule(index: Int) {
        viewModelScope.launch {
            val rules = settings.value.categoryRules.toMutableList()
            if (index in rules.indices) {
                rules.removeAt(index)
                prefs.saveCategoryRules(rules)
            }
        }
    }
    fun setCategoryRuleEnabled(index: Int, enabled: Boolean) {
        viewModelScope.launch {
            val rules = settings.value.categoryRules.toMutableList()
            if (index in rules.indices) {
                rules[index] = rules[index].copy(enabled = enabled)
                prefs.saveCategoryRules(rules)
            }
        }
    }
    fun setDockStyle(s: DockStyle) = pref(LauncherPrefs.DOCK_STYLE, s.name)
    fun setDockLabels(v: Boolean) = pref(LauncherPrefs.DOCK_LABELS, v)
    fun setDockLabelOpacity(v: Int) = pref(LauncherPrefs.DOCK_LABEL_OPACITY, v.coerceIn(35, 100))
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
    fun setSwipeUpAction(a: GestureAction) = pref(LauncherPrefs.SWIPE_UP_ACTION, a.name)
    fun setCustomGestureAction(a: GestureAction) = pref(LauncherPrefs.CUSTOM_GESTURE_ACTION, a.name)
    fun setAssistantApp(appKey: String) = pref(LauncherPrefs.ASSISTANT_APP, appKey)
    fun saveCustomGesturePattern(pattern: String) {
        val cleaned = sanitizeCustomGesturePattern(pattern)
        if (cleaned.isBlank()) return
        pref(LauncherPrefs.CUSTOM_GESTURE_PATTERN, cleaned)
        toast(R.string.custom_gesture_saved)
    }
    fun clearCustomGesturePattern() {
        pref(LauncherPrefs.CUSTOM_GESTURE_PATTERN, "")
        toast(R.string.custom_gesture_cleared)
    }
    fun setShowSuggestions(v: Boolean) = pref(LauncherPrefs.SHOW_SUGGESTIONS, v)
    fun setClockStyle(s: ClockStyle) = pref(LauncherPrefs.CLOCK_STYLE, s.name)
    fun setHideDock(v: Boolean) = pref(LauncherPrefs.HIDE_DOCK, v)
    fun setGrayscaleIcons(v: Boolean) = pref(LauncherPrefs.GRAYSCALE_ICONS, v)
    fun setPageIndicatorStyle(s: PageIndicatorStyle) = pref(LauncherPrefs.PAGE_INDICATOR_STYLE, s.name)
    fun setLabelWeight(w: LabelWeight) = pref(LauncherPrefs.LABEL_WEIGHT, w.name)
    fun setSearchEngine(e: SearchEngine) {
        pref(LauncherPrefs.SEARCH_ENGINE, e.name)
        if (_search.value.length >= 2) loadWebSuggestions(_search.value, e)
    }
    fun setGestureApp(gestureSource: String, appKey: String) {
        val key = when (gestureSource) {
            "double_tap" -> LauncherPrefs.GESTURE_APP_DOUBLE_TAP
            "swipe_down" -> LauncherPrefs.GESTURE_APP_SWIPE_DOWN
            "triple_tap" -> LauncherPrefs.GESTURE_APP_TRIPLE_TAP
            "pinch" -> LauncherPrefs.GESTURE_APP_PINCH
            "dock_tap" -> LauncherPrefs.GESTURE_APP_DOCK_TAP
            "swipe_up" -> LauncherPrefs.GESTURE_APP_SWIPE_UP
            "custom_gesture" -> LauncherPrefs.GESTURE_APP_CUSTOM
            else -> return
        }
        pref(key, appKey)
    }
    fun getGestureApp(gestureSource: String): String = when (gestureSource) {
        "double_tap" -> settings.value.gestureAppDoubleTap
        "swipe_down" -> settings.value.gestureAppSwipeDown
        "triple_tap" -> settings.value.gestureAppTripleTap
        "pinch" -> settings.value.gestureAppPinch
        "dock_tap" -> settings.value.gestureAppDockTap
        "swipe_up" -> settings.value.gestureAppSwipeUp
        "custom_gesture" -> settings.value.gestureAppCustom
        else -> ""
    }
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
            var bytes = java.io.File(ai.sourceDir).length()
            ai.splitSourceDirs?.forEach { bytes += java.io.File(it).length() }
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
                prefs.resetToDefaults()
                iconPackManager.clearPack()
                loadAppsInternal()
                toast(R.string.settings_reset_to_defaults)
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
            val engine = settings.value.searchEngine
            val url = engine.urlTemplate.replace("%s", encoded)
            ctx.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
        } catch (e: Exception) {
            Log.e(TAG, "searchWeb failed", e)
        }
    }

    fun searchWebSuggestion(query: String) {
        val normalized = SearchSuggestionParser.normalizeQuery(query)
        if (normalized.isBlank()) return
        _search.value = normalized
        _webSuggestions.value = emptyList()
        webSuggestionJob?.cancel()
        searchWeb(normalized)
    }

    private fun loadWebSuggestions(query: String, engine: SearchEngine = settings.value.searchEngine) {
        webSuggestionJob?.cancel()
        val normalized = SearchSuggestionParser.normalizeQuery(query)
        if (normalized.length < 2 || engine.suggestionUrlTemplate == null) {
            _webSuggestions.value = emptyList()
            return
        }
        webSuggestionJob = viewModelScope.launch {
            delay(180)
            if (SearchSuggestionParser.normalizeQuery(_search.value) != normalized) return@launch
            val suggestions = webSuggestionService.suggestions(engine, normalized)
            if (
                SearchSuggestionParser.normalizeQuery(_search.value) == normalized &&
                settings.value.searchEngine == engine
            ) {
                _webSuggestions.value = suggestions
            }
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

    suspend fun exportBackup(options: BackupExportOptions = BackupExportOptions()): String = backupService.export(options)
    suspend fun saveCloudBackupTarget(uriString: String, displayName: String): Boolean {
        val uri = Uri.parse(uriString)
        val persisted = runCatching {
            ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }.onFailure {
            Log.e(TAG, "Cloud backup target permission failed", it)
        }.isSuccess
        if (!persisted) {
            toast(R.string.cloud_backup_target_failed)
            return false
        }
        prefs.saveCloudBackupTarget(uriString, displayName)
        return true
    }

    suspend fun clearCloudBackupTarget() {
        val target = cloudBackupTarget.value
        if (target.isConfigured) {
            runCatching {
                ctx.contentResolver.releasePersistableUriPermission(
                    Uri.parse(target.uri),
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
        }
        prefs.clearCloudBackupTarget()
        toast(R.string.cloud_backup_target_cleared)
    }

    suspend fun exportBackupToCloud(options: BackupExportOptions = BackupExportOptions()): Boolean {
        val target = cloudBackupTarget.value
        if (!target.isConfigured) {
            toast(R.string.cloud_backup_not_configured)
            return false
        }
        val json = backupService.export(options)
        val ok = withContext(Dispatchers.IO) {
            runCatching {
                ctx.contentResolver.openOutputStream(Uri.parse(target.uri), "wt")?.use { stream ->
                    stream.write(json.toByteArray(Charsets.UTF_8))
                } ?: error("Cloud backup output stream unavailable")
                prefs.markCloudBackupSuccess()
            }.onFailure {
                Log.e(TAG, "Cloud backup export failed", it)
            }.isSuccess
        }
        toast(if (ok) R.string.cloud_backup_exported else R.string.cloud_backup_failed)
        return ok
    }

    fun setBackupScheduleEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setBackupScheduleEnabled(enabled)
            if (enabled) {
                BackupScheduler.schedule(ctx)
                toast(R.string.backup_schedule_enabled)
            } else {
                BackupScheduler.cancel(ctx)
                toast(R.string.backup_schedule_disabled)
            }
        }
    }

    fun runScheduledBackupNow() {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { BackupScheduler.runBackupNow(ctx) }
            if (result.success) {
                if (backupScheduleState.value.enabled) BackupScheduler.schedule(ctx)
                toast(R.string.backup_schedule_saved)
            } else {
                toast(R.string.backup_schedule_failed)
            }
        }
    }

    suspend fun exportTheme(): String = prefs.exportTheme()
    suspend fun importTheme(json: String): Boolean {
        val imported = prefs.importTheme(json)
        if (imported) {
            val restored = runCatching { prefs.settings.first() }.getOrNull()
            val iconChain = restored?.iconPacks.orEmpty().ifEmpty {
                restored?.iconPack?.takeIf { it.isNotBlank() }?.let { listOf(it) } ?: emptyList()
            }
            if (iconChain.isEmpty()) {
                iconPackManager.clearPack()
                loadAppsInternal()
            } else {
                applyIconPacks(iconChain)
            }
            toast(R.string.theme_imported)
        } else {
            toast(R.string.theme_import_failed)
        }
        return imported
    }
    fun previewBackup(json: String): BackupImportPreview = backupService.preview(json)
    suspend fun prepareBackupImport(payload: ByteArray): PreparedBackupImport =
        withContext(Dispatchers.IO) { backupImportPreparer.prepare(payload) }
    suspend fun importBackup(json: String): Boolean {
        val result = backupService.importAndLoadRestoredSettings(json)
        if (result.imported) {
            toast(R.string.layout_restored)
            try {
                val restored = result.restoredSettings ?: prefs.settings.first()
                val iconChain = restored.iconPacks.ifEmpty {
                    restored.iconPack.takeIf { it.isNotBlank() }?.let { listOf(it) } ?: emptyList()
                }
                if (iconChain.isNotEmpty()) applyIconPacks(iconChain) else loadAppsInternal()
            } catch (e: Exception) {
                Log.e(TAG, "Post-restore reload failed", e)
                loadAppsInternal()
            }
        } else toast(R.string.restore_failed)
        return result.imported
    }

    private fun <T> pref(key: androidx.datastore.preferences.core.Preferences.Key<T>, v: T) { viewModelScope.launch { prefs.set(key, v) } }

    fun vibrate() {
        try {
            val ms = settings.value.hapticLevel.ms
            if (ms <= 0) return
            val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            } ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
            else @Suppress("DEPRECATION") v.vibrate(ms)
        } catch (e: Exception) {
            Log.w(TAG, "Vibration failed", e)
        }
    }

    private fun toast(@StringRes messageRes: Int, vararg args: Any) {
        try { Toast.makeText(ctx, ctx.getString(messageRes, *args), Toast.LENGTH_SHORT).show() } catch (_: Exception) {}
    }

    private fun toast(msg: String) { try { Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show() } catch (_: Exception) {} }

    private suspend fun autoPopulate(apps: List<AppInfo>) {
        try {
            val s = settings.value; val gs = (s.gridColumns * s.gridRows).coerceAtLeast(1); val dc = s.dockCount.coerceAtLeast(1)
            val dock = MutableList<GridCell?>(dc) { null }; val used = mutableSetOf<String>(); var di = 0
            for (slot in DEFAULT_DOCK_SLOTS) { if (di >= dc) break; slot.firstNotNullOfOrNull { pkg -> apps.find { it.packageName == pkg && it.key !in used } }?.let { dock[di++] = GridCell.App(it.key); used.add(it.key) } }
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

    fun openWidgetPicker() { _widgetStackTargetId.value = null; _widgetPickerOpen.value = true }
    fun openWidgetPickerForStack(appWidgetId: Int) {
        _widgetStackTargetId.value = appWidgetId
        _widgetPickerOpen.value = true
    }
    fun closeWidgetPicker() { _widgetPickerOpen.value = false; _widgetStackTargetId.value = null }

    fun getAvailableWidgets(): List<AppWidgetProviderInfo> = runCatching {
        widgetManager.installedProviders.sortedBy { it.loadLabel(ctx.packageManager).toString().lowercase() }
    }.getOrDefault(emptyList())

    fun beginWidgetPlacement(providerInfo: AppWidgetProviderInfo, page: Int, spanX: Int, spanY: Int): PendingWidgetPlacement? {
        discardPendingWidget()
        val widgetId = runCatching { widgetHost.allocateAppWidgetId() }.getOrElse {
            Log.e(TAG, "allocateWidgetId failed", it)
            toast(R.string.couldnt_start_widget_setup)
            return null
        }
        val stackTargetId = _widgetStackTargetId.value
        val stackTarget = stackTargetId?.let { targetId -> _widgets.value.find { it.appWidgetId == targetId } }
        if (stackTargetId != null && stackTarget == null) {
            _widgetStackTargetId.value = null
            runCatching { widgetHost.deleteAppWidgetId(widgetId) }
            toast(R.string.couldnt_start_widget_setup)
            return null
        }
        if (stackTarget != null) {
            if (spanX > stackTarget.spanX || spanY > stackTarget.spanY) {
                runCatching { widgetHost.deleteAppWidgetId(widgetId) }
                toast(R.string.widget_too_large_for_stack)
                return null
            }
            return PendingWidgetPlacement(
                appWidgetId = widgetId,
                providerInfo = providerInfo,
                page = stackTarget.page,
                row = stackTarget.row,
                col = stackTarget.col,
                spanX = stackTarget.spanX,
                spanY = stackTarget.spanY,
                stackTargetWidgetId = stackTarget.appWidgetId,
            ).also { _pendingWidgetPlacement.value = it }
        }
        val span = findFirstEmptySpan(page, spanX, spanY)
        if (span == null) {
            runCatching { widgetHost.deleteAppWidgetId(widgetId) }
            toast(R.string.no_room_on_page)
            return null
        }
        return PendingWidgetPlacement(widgetId, providerInfo, page, span.first, span.second, spanX, spanY)
            .also { _pendingWidgetPlacement.value = it }
    }

    fun bindPendingWidgetIfAllowed(): Boolean {
        val pending = _pendingWidgetPlacement.value ?: return false
        return runCatching {
            widgetManager.bindAppWidgetIdIfAllowed(pending.appWidgetId, pending.providerInfo.provider)
        }.onFailure {
            Log.w(TAG, "bindAppWidgetIdIfAllowed failed for ${pending.providerInfo.provider}", it)
        }.getOrDefault(false)
    }

    fun pendingWidgetNeedsConfiguration(): Boolean {
        val hasConfigureActivity = _pendingWidgetPlacement.value?.providerInfo?.configure != null
        return WidgetPlacementService.setupStep(hasConfigureActivity) == WidgetSetupStep.CONFIGURE_PROVIDER
    }

    fun getPendingWidgetBindIntent(): Intent? {
        val pending = _pendingWidgetPlacement.value ?: return null
        return Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pending.appWidgetId)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, pending.providerInfo.provider)
        }
    }

    fun getPendingWidgetConfigureIntent(): Intent? {
        val pending = _pendingWidgetPlacement.value ?: return null
        val configure = pending.providerInfo.configure ?: return null
        return Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
            component = configure
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pending.appWidgetId)
        }
    }

    fun completePendingWidget() {
        val pending = _pendingWidgetPlacement.value ?: return
        _pendingWidgetPlacement.value = null
        addWidget(WidgetPlacementService.createWidgetInfo(pending), pending.stackTargetWidgetId)
    }

    fun cancelPendingWidget(@StringRes messageRes: Int = R.string.widget_setup_canceled) {
        val pending = _pendingWidgetPlacement.value ?: return
        _pendingWidgetPlacement.value = null
        _widgetStackTargetId.value = null
        runCatching { widgetHost.deleteAppWidgetId(pending.appWidgetId) }
            .onFailure { Log.w(TAG, "delete pending widget id failed: ${pending.appWidgetId}", it) }
        toast(messageRes)
    }

    private fun discardPendingWidget() {
        val pending = _pendingWidgetPlacement.value ?: return
        _pendingWidgetPlacement.value = null
        _widgetStackTargetId.value = null
        runCatching { widgetHost.deleteAppWidgetId(pending.appWidgetId) }
            .onFailure { Log.w(TAG, "discard pending widget id failed: ${pending.appWidgetId}", it) }
    }

    fun addWidget(info: WidgetInfo, stackTargetWidgetId: Int = 0) { viewModelScope.launch {
        var existing = _widgets.value
        var widgetInfo = info
        val stackTarget = stackTargetWidgetId.takeIf { it != 0 }?.let { targetId -> existing.find { it.appWidgetId == targetId } }
        if (stackTargetWidgetId != 0 && stackTarget == null) {
            runCatching { widgetHost.deleteAppWidgetId(info.appWidgetId) }
            _widgetPickerOpen.value = false
            _widgetStackTargetId.value = null
            toast(R.string.couldnt_start_widget_setup)
            return@launch
        }
        if (stackTarget != null) {
            val stackId = WidgetPlacementService.stackIdForTarget(stackTarget)
            existing = existing.map { widget ->
                if (widget.appWidgetId == stackTarget.appWidgetId || widget.stackId == stackId) {
                    widget.copy(stackId = stackId)
                } else {
                    widget
                }
            }
            widgetInfo = info.copy(
                page = stackTarget.page,
                row = stackTarget.row,
                col = stackTarget.col,
                spanX = stackTarget.spanX,
                spanY = stackTarget.spanY,
                stackId = stackId,
                stackOrder = WidgetPlacementService.nextStackOrder(existing, stackId),
            )
        }
        val list = existing + widgetInfo
        _widgets.value = list; prefs.saveWidgets(list)
        if (stackTarget == null) {
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
        }
        _widgetPickerOpen.value = false
        _widgetStackTargetId.value = null
        toast(if (stackTarget == null) R.string.widget_added else R.string.widget_added_to_stack)
    }}

    fun removeWidget(appWidgetId: Int) { viewModelScope.launch {
        val removed = _widgets.value.find { it.appWidgetId == appWidgetId }
        widgetHost.deleteAppWidgetId(appWidgetId)
        val list = _widgets.value.filter { it.appWidgetId != appWidgetId }
        _widgets.value = list; prefs.saveWidgets(list)
        val replacement = removed?.stackId?.takeIf { it.isNotBlank() }?.let { stackId ->
            list.filter { it.stackId == stackId }.minByOrNull { it.stackOrder }
        }
        // Clear grid cells
        val grid = _homeGrid.value.toMutableList()
        for (i in grid.indices) {
            if (grid[i] is GridCell.Widget && (grid[i] as GridCell.Widget).widgetId == appWidgetId) {
                grid[i] = replacement?.let { GridCell.Widget(it.appWidgetId) }
            }
        }
        _homeGrid.value = trimGrid(grid, pageSize()); prefs.saveHome(_homeGrid.value)
        toast(R.string.widget_removed)
    }}

    fun requestRemoveWidget(appWidgetId: Int) {
        val label = runCatching {
            widgetManager.getAppWidgetInfo(appWidgetId)?.loadLabel(ctx.packageManager)
        }.getOrNull().takeIf { !it.isNullOrBlank() } ?: ctx.getString(R.string.widgets)
        _widgetRemoveConfirm.value = WidgetRemoveConfirm(appWidgetId, label)
        _homeMenu.value = null
        _shortcuts.value = emptyList()
    }

    fun dismissRemoveWidget() {
        _widgetRemoveConfirm.value = null
    }

    fun confirmRemoveWidget() {
        val confirm = _widgetRemoveConfirm.value ?: return
        _widgetRemoveConfirm.value = null
        if (WidgetPlacementService.removalOutcome(confirmed = true) == WidgetRemovalOutcome.DELETE_HOST_ID) {
            removeWidget(confirm.appWidgetId)
        }
    }

    fun widgetForId(id: Int): WidgetInfo? = _widgets.value.find { it.appWidgetId == id }

    fun findFirstEmptySpan(page: Int, spanX: Int, spanY: Int): Pair<Int, Int>? {
        return WidgetPlacementService.findFirstEmptySpan(_homeGrid.value, settings.value, page, spanX, spanY)
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

    fun hasContactPermission(): Boolean =
        ctx.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) == android.content.pm.PackageManager.PERMISSION_GRANTED

    fun callContact(number: String) {
        try {
            ctx.startActivity(Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:$number")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
        } catch (e: Exception) { Log.e(TAG, "callContact failed", e) }
    }
}
