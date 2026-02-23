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
 * Lawnchair Lite v2.1.0 - Preferences
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
