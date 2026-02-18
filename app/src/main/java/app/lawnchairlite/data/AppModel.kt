package app.lawnchairlite.data

import android.content.ComponentName
import android.graphics.drawable.Drawable

/**
 * Lawnchair Lite v0.9.0 - Data Model
 */
data class AppInfo(
    val label: String, val packageName: String, val activityName: String,
    val icon: Drawable?, val isSystemApp: Boolean = false,
) {
    val componentName: ComponentName get() = ComponentName(packageName, activityName)
    val key: String get() = "$packageName/$activityName"
}

enum class IconShape(val label: String) {
    SQUIRCLE("Squircle"), CIRCLE("Circle"), SQUARE("Square"), TEARDROP("Teardrop");
}
enum class ThemeMode(val label: String) {
    MIDNIGHT("Midnight"), GLASS("Glass"), OLED("OLED"), MOCHA("Mocha"), AURORA("Aurora");
}
enum class IconSize(val label: String, val dp: Int) {
    SMALL("Small", 42), MEDIUM("Medium", 50), LARGE("Large", 58);
}
enum class GestureAction(val label: String) {
    NONE("None"), LOCK_SCREEN("Lock Screen"), NOTIFICATION_SHADE("Notification Shade"),
    APP_DRAWER("App Drawer"), SETTINGS("Settings"), KILL_APPS("Kill Background Apps");
}

sealed class GridCell {
    data class App(val appKey: String) : GridCell()
    data class Folder(val name: String, val appKeys: List<String>) : GridCell()
}

enum class DragSource { HOME, DOCK, DRAWER }

data class DragState(
    val item: GridCell, val source: DragSource,
    val sourceIndex: Int = -1, val appInfo: AppInfo? = null,
)

fun GridCell.serialize(): String = when (this) {
    is GridCell.App -> "A:${appKey}"
    is GridCell.Folder -> "F:${name}:${appKeys.joinToString(",")}"
}

fun deserializeCell(s: String): GridCell? = when {
    s == "_" || s.isBlank() -> null
    s.startsWith("A:") -> GridCell.App(s.removePrefix("A:"))
    s.startsWith("F:") -> {
        val parts = s.removePrefix("F:").split(":", limit = 2)
        if (parts.size == 2) GridCell.Folder(parts[0], parts[1].split(",").filter { it.isNotBlank() }) else null
    }
    else -> null
}

fun serializeGrid(cells: List<GridCell?>): String = cells.joinToString("|") { it?.serialize() ?: "_" }
fun deserializeGrid(s: String): List<GridCell?> = if (s.isBlank()) emptyList() else s.split("|").map { deserializeCell(it) }

val DEFAULT_DOCK_PKGS = listOf(
    "com.google.android.dialer", "com.android.dialer",
    "com.google.android.apps.messaging", "com.android.mms",
    "com.android.chrome", "org.mozilla.firefox",
    "com.google.android.GoogleCamera", "com.android.camera", "com.android.camera2",
    "com.google.android.apps.photos",
)

val DEFAULT_HOME_PKGS = listOf(
    "com.google.android.gm", "com.google.android.apps.maps",
    "com.google.android.youtube", "com.google.android.calendar",
    "com.android.settings", "com.android.vending",
    "com.spotify.music", "com.whatsapp", "com.instagram.android",
    "com.google.android.keep", "com.google.android.deskclock",
    "com.google.android.apps.docs",
)
