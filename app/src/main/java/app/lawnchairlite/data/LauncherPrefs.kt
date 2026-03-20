package app.lawnchairlite.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import java.io.IOException

/**
 * Lawnchair Lite v2.7.0 - Preferences
 *
 * Stability improvements:
 * - ReplaceFileCorruptionHandler: if DataStore file is corrupted, reset to defaults
 *   instead of crashing in a loop (addresses Lawnchair's identified architectural gap)
 * - All Flow emissions wrapped with .catch{} to emit safe defaults on IO errors
 * - Defensive deserialization: malformed grid strings never crash, just return empty
 * - Atomic writes via DataStore (prevents half-written state on process death)
 * - Backup import validates JSON structure before applying
 */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "ll_prefs",
    corruptionHandler = ReplaceFileCorruptionHandler {
        Log.e("LauncherPrefs", "DataStore corrupted, resetting to defaults", it)
        emptyPreferences()
    }
)

data class LauncherSettings(
    val themeMode: ThemeMode = ThemeMode.MIDNIGHT,
    val iconShape: IconShape = IconShape.SQUIRCLE,
    val iconSize: IconSize = IconSize.MEDIUM,
    val iconPack: String = "",
    val gridColumns: Int = 4,
    val gridRows: Int = 5,
    val dockCount: Int = 5,
    val showClock: Boolean = true,
    val showDockSearch: Boolean = true,
    val doubleTapAction: GestureAction = GestureAction.LOCK_SCREEN,
    val swipeDownAction: GestureAction = GestureAction.NOTIFICATION_SHADE,
    val autoPlaceNew: Boolean = true,
    val wallpaperDim: Int = 0, // 0-100
    val showNotifBadges: Boolean = true,
    val drawerSort: DrawerSort = DrawerSort.NAME,
    val labelStyle: LabelStyle = LabelStyle.SHOWN,
    val themedIcons: Boolean = false,
    val pageTransition: PageTransition = PageTransition.SLIDE,
    val badgeStyle: BadgeStyle = BadgeStyle.COUNT,
    val gridPaddingH: Int = 6, // dp
    val gridPaddingV: Int = 0, // dp
    val hideStatusBar: Boolean = false,
    val dockSwipeApps: Map<Int, String> = emptyMap(), // dock index -> app key
    val drawerColumns: Int = 0, // 0 = use gridColumns
    val homeLocked: Boolean = false,
    val iconShadow: Boolean = false,
    val accentOverride: String = "", // hex color or empty for theme default
    val drawerCategories: Boolean = false,
    val dockStyle: DockStyle = DockStyle.SOLID,
    val searchBarStyle: SearchBarStyle = SearchBarStyle.PILL,
    val hapticLevel: HapticLevel = HapticLevel.MEDIUM,
    val drawerOpacity: Int = 97, // 0-100
    val labelSize: LabelSize = LabelSize.MEDIUM,
    val folderColumns: Int = 4,
    val drawerSectionHeaders: Boolean = false,
    val wallpaperParallax: Boolean = true,
    val drawerAnimation: Boolean = true,
    val tripleTapAction: GestureAction = GestureAction.NONE,
    val pinchAction: GestureAction = GestureAction.SETTINGS,
    val dockTapAction: GestureAction = GestureAction.APP_DRAWER,
)

class LauncherPrefs(private val context: Context) {

    companion object {
        private const val TAG = "LauncherPrefs"
        val THEME = stringPreferencesKey("theme")
        val ICON_SHAPE = stringPreferencesKey("icon_shape")
        val ICON_SIZE = stringPreferencesKey("icon_size")
        val ICON_PACK = stringPreferencesKey("icon_pack")
        val GRID_COLS = intPreferencesKey("grid_cols")
        val GRID_ROWS = intPreferencesKey("grid_rows")
        val DOCK_COUNT = intPreferencesKey("dock_count")
        val SHOW_CLOCK = booleanPreferencesKey("show_clock")
        val SHOW_DOCK_SEARCH = booleanPreferencesKey("show_dock_search")
        val DOUBLE_TAP_ACTION = stringPreferencesKey("double_tap_action")
        val SWIPE_DOWN_ACTION = stringPreferencesKey("swipe_down_action")
        val HOME_GRID = stringPreferencesKey("home_grid_v3")
        val DOCK_GRID = stringPreferencesKey("dock_grid_v3")
        val INITIALIZED = booleanPreferencesKey("init_v3")
        val HIDDEN_APPS = stringPreferencesKey("hidden_apps")
        val CUSTOM_LABELS = stringPreferencesKey("custom_labels")
        val AUTO_PLACE_NEW = booleanPreferencesKey("auto_place_new")
        val WALLPAPER_DIM = intPreferencesKey("wallpaper_dim")
        val SHOW_NOTIF_BADGES = booleanPreferencesKey("show_notif_badges")
        val APP_USAGE = stringPreferencesKey("app_usage")
        val DRAWER_SORT = stringPreferencesKey("drawer_sort")
        val LABEL_STYLE = stringPreferencesKey("label_style")
        val THEMED_ICONS = booleanPreferencesKey("themed_icons")
        val PAGE_TRANSITION = stringPreferencesKey("page_transition")
        val BADGE_STYLE = stringPreferencesKey("badge_style")
        val GRID_PADDING_H = intPreferencesKey("grid_padding_h")
        val GRID_PADDING_V = intPreferencesKey("grid_padding_v")
        val HIDE_STATUS_BAR = booleanPreferencesKey("hide_status_bar")
        val DOCK_SWIPE_APPS = stringPreferencesKey("dock_swipe_apps")
        val DRAWER_COLUMNS = intPreferencesKey("drawer_columns")
        val HOME_LOCKED = booleanPreferencesKey("home_locked")
        val ICON_SHADOW = booleanPreferencesKey("icon_shadow")
        val ACCENT_OVERRIDE = stringPreferencesKey("accent_override")
        val DRAWER_CATEGORIES = booleanPreferencesKey("drawer_categories")
        val DOCK_STYLE = stringPreferencesKey("dock_style")
        val SEARCH_BAR_STYLE = stringPreferencesKey("search_bar_style")
        val HAPTIC_LEVEL = stringPreferencesKey("haptic_level")
        val DRAWER_OPACITY = intPreferencesKey("drawer_opacity")
        val LABEL_SIZE_PREF = stringPreferencesKey("label_size")
        val FOLDER_COLUMNS = intPreferencesKey("folder_columns")
        val DRAWER_SECTION_HEADERS = booleanPreferencesKey("drawer_section_headers")
        val WALLPAPER_PARALLAX = booleanPreferencesKey("wallpaper_parallax")
        val DRAWER_ANIMATION = booleanPreferencesKey("drawer_animation")
        val TRIPLE_TAP_ACTION = stringPreferencesKey("triple_tap_action")
        val PINCH_ACTION = stringPreferencesKey("pinch_action")
        val DOCK_TAP_ACTION = stringPreferencesKey("dock_tap_action")
    }

    // Safe data flow: catches IOException (disk errors) and emits defaults
    private val safeData: Flow<Preferences> = context.dataStore.data.catch { e ->
        if (e is IOException) {
            Log.e(TAG, "DataStore read error, emitting defaults", e)
            emit(emptyPreferences())
        } else throw e
    }

    val settings: Flow<LauncherSettings> = safeData.map { p ->
        LauncherSettings(
            themeMode = p[THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.MIDNIGHT,
            iconShape = p[ICON_SHAPE]?.let { runCatching { IconShape.valueOf(it) }.getOrNull() } ?: IconShape.SQUIRCLE,
            iconSize = p[ICON_SIZE]?.let { runCatching { IconSize.valueOf(it) }.getOrNull() } ?: IconSize.MEDIUM,
            iconPack = p[ICON_PACK] ?: "",
            gridColumns = (p[GRID_COLS] ?: 4).coerceIn(3, 8),
            gridRows = (p[GRID_ROWS] ?: 5).coerceIn(3, 10),
            dockCount = (p[DOCK_COUNT] ?: 5).coerceIn(3, 7),
            showClock = p[SHOW_CLOCK] ?: true,
            showDockSearch = p[SHOW_DOCK_SEARCH] ?: true,
            doubleTapAction = p[DOUBLE_TAP_ACTION]?.let { runCatching { GestureAction.valueOf(it) }.getOrNull() } ?: GestureAction.LOCK_SCREEN,
            swipeDownAction = p[SWIPE_DOWN_ACTION]?.let { runCatching { GestureAction.valueOf(it) }.getOrNull() } ?: GestureAction.NOTIFICATION_SHADE,
            autoPlaceNew = p[AUTO_PLACE_NEW] ?: true,
            wallpaperDim = (p[WALLPAPER_DIM] ?: 0).coerceIn(0, 100),
            showNotifBadges = p[SHOW_NOTIF_BADGES] ?: true,
            drawerSort = p[DRAWER_SORT]?.let { runCatching { DrawerSort.valueOf(it) }.getOrNull() } ?: DrawerSort.NAME,
            labelStyle = p[LABEL_STYLE]?.let { runCatching { LabelStyle.valueOf(it) }.getOrNull() } ?: LabelStyle.SHOWN,
            themedIcons = p[THEMED_ICONS] ?: false,
            pageTransition = p[PAGE_TRANSITION]?.let { runCatching { PageTransition.valueOf(it) }.getOrNull() } ?: PageTransition.SLIDE,
            badgeStyle = p[BADGE_STYLE]?.let { runCatching { BadgeStyle.valueOf(it) }.getOrNull() } ?: BadgeStyle.COUNT,
            gridPaddingH = (p[GRID_PADDING_H] ?: 6).coerceIn(0, 24),
            gridPaddingV = (p[GRID_PADDING_V] ?: 0).coerceIn(0, 24),
            hideStatusBar = p[HIDE_STATUS_BAR] ?: false,
            dockSwipeApps = p[DOCK_SWIPE_APPS]?.let { parseDockSwipeApps(it) } ?: emptyMap(),
            drawerColumns = (p[DRAWER_COLUMNS] ?: 0).coerceIn(0, 8),
            homeLocked = p[HOME_LOCKED] ?: false,
            iconShadow = p[ICON_SHADOW] ?: false,
            accentOverride = p[ACCENT_OVERRIDE] ?: "",
            drawerCategories = p[DRAWER_CATEGORIES] ?: false,
            dockStyle = p[DOCK_STYLE]?.let { runCatching { DockStyle.valueOf(it) }.getOrNull() } ?: DockStyle.SOLID,
            searchBarStyle = p[SEARCH_BAR_STYLE]?.let { runCatching { SearchBarStyle.valueOf(it) }.getOrNull() } ?: SearchBarStyle.PILL,
            hapticLevel = p[HAPTIC_LEVEL]?.let { runCatching { HapticLevel.valueOf(it) }.getOrNull() } ?: HapticLevel.MEDIUM,
            drawerOpacity = (p[DRAWER_OPACITY] ?: 97).coerceIn(0, 100),
            labelSize = p[LABEL_SIZE_PREF]?.let { runCatching { LabelSize.valueOf(it) }.getOrNull() } ?: LabelSize.MEDIUM,
            folderColumns = (p[FOLDER_COLUMNS] ?: 4).coerceIn(3, 5),
            drawerSectionHeaders = p[DRAWER_SECTION_HEADERS] ?: false,
            wallpaperParallax = p[WALLPAPER_PARALLAX] ?: true,
            drawerAnimation = p[DRAWER_ANIMATION] ?: true,
            tripleTapAction = p[TRIPLE_TAP_ACTION]?.let { runCatching { GestureAction.valueOf(it) }.getOrNull() } ?: GestureAction.NONE,
            pinchAction = p[PINCH_ACTION]?.let { runCatching { GestureAction.valueOf(it) }.getOrNull() } ?: GestureAction.SETTINGS,
            dockTapAction = p[DOCK_TAP_ACTION]?.let { runCatching { GestureAction.valueOf(it) }.getOrNull() } ?: GestureAction.APP_DRAWER,
        )
    }

    val homeGrid: Flow<List<GridCell?>> = safeData.map { p -> p[HOME_GRID]?.let { safeDeserializeGrid(it) } ?: emptyList() }
    val dockGrid: Flow<List<GridCell?>> = safeData.map { p -> p[DOCK_GRID]?.let { safeDeserializeGrid(it) } ?: emptyList() }
    val initialized: Flow<Boolean> = safeData.map { p -> p[INITIALIZED] ?: false }
    val hiddenApps: Flow<Set<String>> = safeData.map { p ->
        p[HIDDEN_APPS]?.split("|")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
    }
    val customLabels: Flow<Map<String, String>> = safeData.map { p ->
        p[CUSTOM_LABELS]?.split("|")?.mapNotNull { entry ->
            val eq = entry.indexOf('=')
            if (eq > 0 && eq < entry.length - 1) entry.substring(0, eq) to unescapeLabel(entry.substring(eq + 1)) else null
        }?.toMap() ?: emptyMap()
    }
    val appUsage: Flow<Map<String, Long>> = safeData.map { p ->
        p[APP_USAGE]?.split("|")?.mapNotNull { entry ->
            val eq = entry.indexOf('=')
            if (eq > 0 && eq < entry.length - 1) {
                val key = entry.substring(0, eq)
                val ts = entry.substring(eq + 1).toLongOrNull()
                if (ts != null) key to ts else null
            } else null
        }?.toMap() ?: emptyMap()
    }

    /**
     * Defensive grid deserialization. Never throws.
     * Malformed cells become null (empty slot) rather than crashing the launcher.
     */
    private fun safeDeserializeGrid(s: String): List<GridCell?> {
        if (s.isBlank()) return emptyList()
        return try {
            s.split("|").map { token ->
                runCatching { deserializeCell(token) }.getOrNull()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Grid deserialization failed, returning empty", e)
            emptyList()
        }
    }

    suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        runCatching { context.dataStore.edit { it[key] = value } }
            .onFailure { Log.e(TAG, "Failed to write pref: $key", it) }
    }

    suspend fun saveHome(cells: List<GridCell?>) {
        runCatching { context.dataStore.edit { it[HOME_GRID] = serializeGrid(cells) } }
            .onFailure { Log.e(TAG, "Failed to save home grid", it) }
    }

    suspend fun saveDock(cells: List<GridCell?>) {
        runCatching { context.dataStore.edit { it[DOCK_GRID] = serializeGrid(cells) } }
            .onFailure { Log.e(TAG, "Failed to save dock grid", it) }
    }

    suspend fun saveHidden(keys: Set<String>) {
        runCatching { context.dataStore.edit { it[HIDDEN_APPS] = keys.joinToString("|") } }
            .onFailure { Log.e(TAG, "Failed to save hidden apps", it) }
    }

    suspend fun saveCustomLabels(map: Map<String, String>) {
        runCatching {
            context.dataStore.edit { it[CUSTOM_LABELS] = map.entries.joinToString("|") { (k, v) ->
                "$k=${escapeLabel(v)}"
            } }
        }.onFailure { Log.e(TAG, "Failed to save custom labels", it) }
    }

    /** Escape label values for pipe-delimited storage. Uses same scheme as AppModel. */
    private fun escapeLabel(s: String): String = s
        .replace("\\", "\\b").replace("|", "\\p").replace("=", "\\e")
    private fun unescapeLabel(s: String): String = s
        .replace("\\e", "=").replace("\\p", "|").replace("\\b", "\\")

    private fun parseDockSwipeApps(s: String): Map<Int, String> {
        if (s.isBlank()) return emptyMap()
        return s.split("|").mapNotNull { entry ->
            val eq = entry.indexOf('=')
            if (eq > 0 && eq < entry.length - 1) {
                val idx = entry.substring(0, eq).toIntOrNull()
                if (idx != null) idx to entry.substring(eq + 1) else null
            } else null
        }.toMap()
    }

    suspend fun saveDockSwipeApps(map: Map<Int, String>) {
        runCatching {
            context.dataStore.edit { it[DOCK_SWIPE_APPS] = map.entries.joinToString("|") { (k, v) -> "$k=$v" } }
        }.onFailure { Log.e(TAG, "Failed to save dock swipe apps", it) }
    }

    suspend fun saveAppUsage(map: Map<String, Long>) {
        runCatching {
            context.dataStore.edit { it[APP_USAGE] = map.entries.joinToString("|") { (k, v) -> "$k=$v" } }
        }.onFailure { Log.e(TAG, "Failed to save app usage", it) }
    }

    suspend fun markInitialized() {
        runCatching { context.dataStore.edit { it[INITIALIZED] = true } }
            .onFailure { Log.e(TAG, "Failed to mark initialized", it) }
    }

    suspend fun exportBackup(): String {
        val p = runCatching { context.dataStore.data.first() }.getOrDefault(emptyPreferences())
        return JSONObject().apply {
            put("version", app.lawnchairlite.BuildConfig.VERSION_NAME)
            put("theme", p[THEME] ?: "MIDNIGHT"); put("icon_shape", p[ICON_SHAPE] ?: "SQUIRCLE"); put("icon_size", p[ICON_SIZE] ?: "MEDIUM")
            put("icon_pack", p[ICON_PACK] ?: "")
            put("grid_cols", p[GRID_COLS] ?: 4); put("grid_rows", p[GRID_ROWS] ?: 5); put("dock_count", p[DOCK_COUNT] ?: 5)
            put("show_clock", p[SHOW_CLOCK] ?: true); put("show_dock_search", p[SHOW_DOCK_SEARCH] ?: true)
            put("double_tap", p[DOUBLE_TAP_ACTION] ?: "LOCK_SCREEN"); put("swipe_down", p[SWIPE_DOWN_ACTION] ?: "NOTIFICATION_SHADE")
            put("auto_place_new", p[AUTO_PLACE_NEW] ?: true)
            put("wallpaper_dim", p[WALLPAPER_DIM] ?: 0)
            put("show_notif_badges", p[SHOW_NOTIF_BADGES] ?: true)
            put("drawer_sort", p[DRAWER_SORT] ?: "NAME")
            put("label_style", p[LABEL_STYLE] ?: "SHOWN")
            put("themed_icons", p[THEMED_ICONS] ?: false)
            put("page_transition", p[PAGE_TRANSITION] ?: "SLIDE")
            put("badge_style", p[BADGE_STYLE] ?: "COUNT")
            put("grid_padding_h", p[GRID_PADDING_H] ?: 6)
            put("grid_padding_v", p[GRID_PADDING_V] ?: 0)
            put("hide_status_bar", p[HIDE_STATUS_BAR] ?: false)
            put("dock_swipe_apps", p[DOCK_SWIPE_APPS] ?: "")
            put("drawer_columns", p[DRAWER_COLUMNS] ?: 0)
            put("home_locked", p[HOME_LOCKED] ?: false)
            put("icon_shadow", p[ICON_SHADOW] ?: false)
            put("accent_override", p[ACCENT_OVERRIDE] ?: "")
            put("drawer_categories", p[DRAWER_CATEGORIES] ?: false)
            put("dock_style", p[DOCK_STYLE] ?: "SOLID")
            put("search_bar_style", p[SEARCH_BAR_STYLE] ?: "PILL")
            put("haptic_level", p[HAPTIC_LEVEL] ?: "MEDIUM")
            put("drawer_opacity", p[DRAWER_OPACITY] ?: 97)
            put("label_size", p[LABEL_SIZE_PREF] ?: "MEDIUM")
            put("folder_columns", p[FOLDER_COLUMNS] ?: 4)
            put("drawer_section_headers", p[DRAWER_SECTION_HEADERS] ?: false)
            put("wallpaper_parallax", p[WALLPAPER_PARALLAX] ?: true)
            put("drawer_animation", p[DRAWER_ANIMATION] ?: true)
            put("triple_tap", p[TRIPLE_TAP_ACTION] ?: "NONE")
            put("pinch_action", p[PINCH_ACTION] ?: "SETTINGS")
            put("dock_tap_action", p[DOCK_TAP_ACTION] ?: "APP_DRAWER")
            put("home_grid", p[HOME_GRID] ?: ""); put("dock_grid", p[DOCK_GRID] ?: "")
            put("hidden_apps", p[HIDDEN_APPS] ?: ""); put("custom_labels", p[CUSTOM_LABELS] ?: "")
        }.toString(2)
    }

    /**
     * Import with validation: verifies JSON structure before applying.
     * Invalid fields are silently skipped rather than failing the whole import.
     */
    suspend fun importBackup(jsonStr: String): Boolean = runCatching {
        val j = JSONObject(jsonStr)
        context.dataStore.edit { p ->
            j.optString("theme").takeIf { it.isNotBlank() && runCatching { ThemeMode.valueOf(it) }.isSuccess }?.let { p[THEME] = it }
            j.optString("icon_shape").takeIf { it.isNotBlank() && runCatching { IconShape.valueOf(it) }.isSuccess }?.let { p[ICON_SHAPE] = it }
            j.optString("icon_size").takeIf { it.isNotBlank() && runCatching { IconSize.valueOf(it) }.isSuccess }?.let { p[ICON_SIZE] = it }
            j.optString("icon_pack").let { p[ICON_PACK] = it }
            if (j.has("grid_cols")) p[GRID_COLS] = j.getInt("grid_cols").coerceIn(3, 8)
            if (j.has("grid_rows")) p[GRID_ROWS] = j.getInt("grid_rows").coerceIn(3, 10)
            if (j.has("dock_count")) p[DOCK_COUNT] = j.getInt("dock_count").coerceIn(3, 7)
            if (j.has("show_clock")) p[SHOW_CLOCK] = j.getBoolean("show_clock")
            if (j.has("show_dock_search")) p[SHOW_DOCK_SEARCH] = j.getBoolean("show_dock_search")
            j.optString("double_tap").takeIf { it.isNotBlank() && runCatching { GestureAction.valueOf(it) }.isSuccess }?.let { p[DOUBLE_TAP_ACTION] = it }
            j.optString("swipe_down").takeIf { it.isNotBlank() && runCatching { GestureAction.valueOf(it) }.isSuccess }?.let { p[SWIPE_DOWN_ACTION] = it }
            if (j.has("auto_place_new")) p[AUTO_PLACE_NEW] = j.getBoolean("auto_place_new")
            if (j.has("wallpaper_dim")) p[WALLPAPER_DIM] = j.getInt("wallpaper_dim").coerceIn(0, 100)
            if (j.has("show_notif_badges")) p[SHOW_NOTIF_BADGES] = j.getBoolean("show_notif_badges")
            j.optString("drawer_sort").takeIf { it.isNotBlank() && runCatching { DrawerSort.valueOf(it) }.isSuccess }?.let { p[DRAWER_SORT] = it }
            j.optString("label_style").takeIf { it.isNotBlank() && runCatching { LabelStyle.valueOf(it) }.isSuccess }?.let { p[LABEL_STYLE] = it }
            if (j.has("themed_icons")) p[THEMED_ICONS] = j.getBoolean("themed_icons")
            j.optString("page_transition").takeIf { it.isNotBlank() && runCatching { PageTransition.valueOf(it) }.isSuccess }?.let { p[PAGE_TRANSITION] = it }
            j.optString("badge_style").takeIf { it.isNotBlank() && runCatching { BadgeStyle.valueOf(it) }.isSuccess }?.let { p[BADGE_STYLE] = it }
            if (j.has("grid_padding_h")) p[GRID_PADDING_H] = j.getInt("grid_padding_h").coerceIn(0, 24)
            if (j.has("grid_padding_v")) p[GRID_PADDING_V] = j.getInt("grid_padding_v").coerceIn(0, 24)
            if (j.has("hide_status_bar")) p[HIDE_STATUS_BAR] = j.getBoolean("hide_status_bar")
            j.optString("dock_swipe_apps").takeIf { it.isNotBlank() }?.let { p[DOCK_SWIPE_APPS] = it }
            if (j.has("drawer_columns")) p[DRAWER_COLUMNS] = j.getInt("drawer_columns").coerceIn(0, 8)
            if (j.has("home_locked")) p[HOME_LOCKED] = j.getBoolean("home_locked")
            if (j.has("icon_shadow")) p[ICON_SHADOW] = j.getBoolean("icon_shadow")
            j.optString("accent_override").let { p[ACCENT_OVERRIDE] = it }
            if (j.has("drawer_categories")) p[DRAWER_CATEGORIES] = j.getBoolean("drawer_categories")
            j.optString("dock_style").takeIf { it.isNotBlank() && runCatching { DockStyle.valueOf(it) }.isSuccess }?.let { p[DOCK_STYLE] = it }
            j.optString("search_bar_style").takeIf { it.isNotBlank() && runCatching { SearchBarStyle.valueOf(it) }.isSuccess }?.let { p[SEARCH_BAR_STYLE] = it }
            j.optString("haptic_level").takeIf { it.isNotBlank() && runCatching { HapticLevel.valueOf(it) }.isSuccess }?.let { p[HAPTIC_LEVEL] = it }
            if (j.has("drawer_opacity")) p[DRAWER_OPACITY] = j.getInt("drawer_opacity").coerceIn(0, 100)
            j.optString("label_size").takeIf { it.isNotBlank() && runCatching { LabelSize.valueOf(it) }.isSuccess }?.let { p[LABEL_SIZE_PREF] = it }
            if (j.has("folder_columns")) p[FOLDER_COLUMNS] = j.getInt("folder_columns").coerceIn(3, 5)
            if (j.has("drawer_section_headers")) p[DRAWER_SECTION_HEADERS] = j.getBoolean("drawer_section_headers")
            if (j.has("wallpaper_parallax")) p[WALLPAPER_PARALLAX] = j.getBoolean("wallpaper_parallax")
            if (j.has("drawer_animation")) p[DRAWER_ANIMATION] = j.getBoolean("drawer_animation")
            j.optString("triple_tap").takeIf { it.isNotBlank() && runCatching { GestureAction.valueOf(it) }.isSuccess }?.let { p[TRIPLE_TAP_ACTION] = it }
            j.optString("pinch_action").takeIf { it.isNotBlank() && runCatching { GestureAction.valueOf(it) }.isSuccess }?.let { p[PINCH_ACTION] = it }
            j.optString("dock_tap_action").takeIf { it.isNotBlank() && runCatching { GestureAction.valueOf(it) }.isSuccess }?.let { p[DOCK_TAP_ACTION] = it }
            j.optString("home_grid").takeIf { it.isNotBlank() }?.let { p[HOME_GRID] = it }
            j.optString("dock_grid").takeIf { it.isNotBlank() }?.let { p[DOCK_GRID] = it }
            j.optString("hidden_apps").let { p[HIDDEN_APPS] = it }
            j.optString("custom_labels").let { p[CUSTOM_LABELS] = it }
            p[INITIALIZED] = true
        }
        true
    }.getOrElse { e ->
        Log.e(TAG, "Backup import failed", e)
        false
    }
}
