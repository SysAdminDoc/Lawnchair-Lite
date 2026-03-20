package app.lawnchairlite.data

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.drawable.Drawable
import android.os.Process
import android.util.Log

/**
 * Lawnchair Lite v2.2.0 - App Shortcuts
 *
 * Queries dynamic/static/pinned shortcuts via LauncherApps.
 * Launches shortcuts safely with fallback handling.
 */
data class AppShortcut(
    val id: String,
    val shortLabel: CharSequence,
    val longLabel: CharSequence?,
    val icon: Drawable?,
    val packageName: String,
    val shortcutInfo: ShortcutInfo,
)

class ShortcutRepository(private val context: Context) {

    companion object {
        private const val TAG = "ShortcutRepo"
    }

    private val launcherApps: LauncherApps? by lazy {
        try {
            context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps
        } catch (e: Exception) {
            Log.w(TAG, "LauncherApps unavailable", e)
            null
        }
    }

    fun getShortcuts(packageName: String): List<AppShortcut> {
        val la = launcherApps ?: return emptyList()
        if (!la.hasShortcutHostPermission()) return emptyList()
        return try {
            val query = LauncherApps.ShortcutQuery().apply {
                setPackage(packageName)
                setQueryFlags(
                    LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST
                )
            }
            val shortcuts = la.getShortcuts(query, Process.myUserHandle()) ?: emptyList()
            shortcuts.filter { it.isEnabled }.take(5).map { si ->
                AppShortcut(
                    id = si.id,
                    shortLabel = si.shortLabel ?: si.id,
                    longLabel = si.longLabel,
                    icon = try { la.getShortcutIconDrawable(si, context.resources.displayMetrics.densityDpi) } catch (_: Exception) { null },
                    packageName = si.`package`,
                    shortcutInfo = si,
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "getShortcuts failed for $packageName", e)
            emptyList()
        }
    }

    fun launchShortcut(shortcut: AppShortcut): Boolean {
        val la = launcherApps ?: return false
        return try {
            la.startShortcut(shortcut.packageName, shortcut.id, null, null, Process.myUserHandle())
            true
        } catch (e: Exception) {
            Log.e(TAG, "launchShortcut failed: ${shortcut.id}", e)
            false
        }
    }
}
