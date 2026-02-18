package app.lawnchairlite.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Lawnchair Lite v0.9.0 - App Repository */
class AppRepository(private val context: Context) {
    private val pm: PackageManager = context.packageManager

    suspend fun loadApps(iconPackManager: IconPackManager? = null): List<AppInfo> = withContext(Dispatchers.IO) {
        val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        val packLoaded = iconPackManager?.isLoaded() == true
        pm.queryIntentActivities(intent, 0).filter { it.activityInfo != null }.mapNotNull { ri ->
            runCatching {
                val ai = ri.activityInfo
                val sys = (ai.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val systemIcon = ri.loadIcon(pm)
                val icon = if (packLoaded) {
                    iconPackManager?.resolveIcon(ComponentName(ai.packageName, ai.name)) ?: systemIcon
                } else systemIcon
                AppInfo(ri.loadLabel(pm).toString(), ai.packageName, ai.name, icon, sys)
            }.getOrNull()
        }.filter { it.packageName != context.packageName }.sortedBy { it.label.lowercase() }.distinctBy { it.key }
    }

    fun launchApp(app: AppInfo) { runCatching {
        val i = pm.getLaunchIntentForPackage(app.packageName) ?: Intent(Intent.ACTION_MAIN).apply { component = app.componentName; addCategory(Intent.CATEGORY_LAUNCHER) }
        i.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED; context.startActivity(i)
    }}

    fun openAppInfo(app: AppInfo) { runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = Uri.parse("package:${app.packageName}"); flags = Intent.FLAG_ACTIVITY_NEW_TASK }) } }
    fun uninstallApp(app: AppInfo) { runCatching { context.startActivity(Intent(Intent.ACTION_DELETE).apply { data = Uri.parse("package:${app.packageName}"); flags = Intent.FLAG_ACTIVITY_NEW_TASK }) } }
}
