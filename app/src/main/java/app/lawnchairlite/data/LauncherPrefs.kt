package app.lawnchairlite.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject

/**
 * Lawnchair Lite v0.9.0 - Preferences
 */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "ll_prefs")

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
)

class LauncherPrefs(private val context: Context) {

    companion object {
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
    }

    val settings: Flow<LauncherSettings> = context.dataStore.data.map { p ->
        LauncherSettings(
            themeMode = p[THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.MIDNIGHT,
            iconShape = p[ICON_SHAPE]?.let { runCatching { IconShape.valueOf(it) }.getOrNull() } ?: IconShape.SQUIRCLE,
            iconSize = p[ICON_SIZE]?.let { runCatching { IconSize.valueOf(it) }.getOrNull() } ?: IconSize.MEDIUM,
            iconPack = p[ICON_PACK] ?: "",
            gridColumns = p[GRID_COLS] ?: 4, gridRows = p[GRID_ROWS] ?: 5, dockCount = p[DOCK_COUNT] ?: 5,
            showClock = p[SHOW_CLOCK] ?: true, showDockSearch = p[SHOW_DOCK_SEARCH] ?: true,
            doubleTapAction = p[DOUBLE_TAP_ACTION]?.let { runCatching { GestureAction.valueOf(it) }.getOrNull() } ?: GestureAction.LOCK_SCREEN,
            swipeDownAction = p[SWIPE_DOWN_ACTION]?.let { runCatching { GestureAction.valueOf(it) }.getOrNull() } ?: GestureAction.NOTIFICATION_SHADE,
        )
    }

    val homeGrid: Flow<List<GridCell?>> = context.dataStore.data.map { p -> p[HOME_GRID]?.let { deserializeGrid(it) } ?: emptyList() }
    val dockGrid: Flow<List<GridCell?>> = context.dataStore.data.map { p -> p[DOCK_GRID]?.let { deserializeGrid(it) } ?: emptyList() }
    val initialized: Flow<Boolean> = context.dataStore.data.map { p -> p[INITIALIZED] ?: false }
    val hiddenApps: Flow<Set<String>> = context.dataStore.data.map { p -> p[HIDDEN_APPS]?.split("|")?.filter { it.isNotBlank() }?.toSet() ?: emptySet() }
    val customLabels: Flow<Map<String, String>> = context.dataStore.data.map { p ->
        p[CUSTOM_LABELS]?.split("|")?.filter { it.contains("=") }?.associate { val (k, v) = it.split("=", limit = 2); k to v } ?: emptyMap()
    }

    suspend fun <T> set(key: Preferences.Key<T>, value: T) { context.dataStore.edit { it[key] = value } }
    suspend fun saveHome(cells: List<GridCell?>) { context.dataStore.edit { it[HOME_GRID] = serializeGrid(cells) } }
    suspend fun saveDock(cells: List<GridCell?>) { context.dataStore.edit { it[DOCK_GRID] = serializeGrid(cells) } }
    suspend fun saveHidden(keys: Set<String>) { context.dataStore.edit { it[HIDDEN_APPS] = keys.joinToString("|") } }
    suspend fun saveCustomLabels(map: Map<String, String>) { context.dataStore.edit { it[CUSTOM_LABELS] = map.entries.joinToString("|") { (k, v) -> "$k=$v" } } }
    suspend fun markInitialized() { context.dataStore.edit { it[INITIALIZED] = true } }

    suspend fun exportBackup(): String {
        val p = context.dataStore.data.first()
        return JSONObject().apply {
            put("version", "0.9.0")
            put("theme", p[THEME] ?: "MIDNIGHT"); put("icon_shape", p[ICON_SHAPE] ?: "SQUIRCLE"); put("icon_size", p[ICON_SIZE] ?: "MEDIUM")
            put("icon_pack", p[ICON_PACK] ?: "")
            put("grid_cols", p[GRID_COLS] ?: 4); put("grid_rows", p[GRID_ROWS] ?: 5); put("dock_count", p[DOCK_COUNT] ?: 5)
            put("show_clock", p[SHOW_CLOCK] ?: true); put("show_dock_search", p[SHOW_DOCK_SEARCH] ?: true)
            put("double_tap", p[DOUBLE_TAP_ACTION] ?: "LOCK_SCREEN"); put("swipe_down", p[SWIPE_DOWN_ACTION] ?: "NOTIFICATION_SHADE")
            put("home_grid", p[HOME_GRID] ?: ""); put("dock_grid", p[DOCK_GRID] ?: "")
            put("hidden_apps", p[HIDDEN_APPS] ?: ""); put("custom_labels", p[CUSTOM_LABELS] ?: "")
        }.toString(2)
    }

    suspend fun importBackup(jsonStr: String): Boolean = runCatching {
        val j = JSONObject(jsonStr)
        context.dataStore.edit { p ->
            j.optString("theme").takeIf { it.isNotBlank() }?.let { p[THEME] = it }
            j.optString("icon_shape").takeIf { it.isNotBlank() }?.let { p[ICON_SHAPE] = it }
            j.optString("icon_size").takeIf { it.isNotBlank() }?.let { p[ICON_SIZE] = it }
            j.optString("icon_pack").let { p[ICON_PACK] = it }
            if (j.has("grid_cols")) p[GRID_COLS] = j.getInt("grid_cols")
            if (j.has("grid_rows")) p[GRID_ROWS] = j.getInt("grid_rows")
            if (j.has("dock_count")) p[DOCK_COUNT] = j.getInt("dock_count")
            if (j.has("show_clock")) p[SHOW_CLOCK] = j.getBoolean("show_clock")
            if (j.has("show_dock_search")) p[SHOW_DOCK_SEARCH] = j.getBoolean("show_dock_search")
            j.optString("double_tap").takeIf { it.isNotBlank() }?.let { p[DOUBLE_TAP_ACTION] = it }
            j.optString("swipe_down").takeIf { it.isNotBlank() }?.let { p[SWIPE_DOWN_ACTION] = it }
            j.optString("home_grid").takeIf { it.isNotBlank() }?.let { p[HOME_GRID] = it }
            j.optString("dock_grid").takeIf { it.isNotBlank() }?.let { p[DOCK_GRID] = it }
            j.optString("hidden_apps").let { p[HIDDEN_APPS] = it }
            j.optString("custom_labels").let { p[CUSTOM_LABELS] = it }
            p[INITIALIZED] = true
        }
        true
    }.getOrDefault(false)
}
