package app.lawnchairlite.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.DeadSystemException
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Lawnchair Lite - App Repository
 *
 * Stability improvements:
 * - All PackageManager calls wrapped in try-catch for DeadSystemException,
 *   SecurityException, NameNotFoundException (Lawnchair crash patterns #6225, #982, #2670)
 * - Icon loading failures return null gracefully (never crashes the app list)
 * - Launch failures show fallback behavior instead of silent swallow
 * - queryIntentActivities guarded against OEM-modified PM behavior
 */
class AppRepository(private val context: Context) {

    companion object {
        private const val TAG = "AppRepository"
    }

    private val pm: PackageManager = context.packageManager
    private val launcherApps: LauncherApps? = context.getSystemService(LauncherApps::class.java)
    private val userManager: UserManager? = context.getSystemService(UserManager::class.java)

    /**
     * Load all launchable apps with defensive error handling per-app.
     * Individual app resolution failures are logged and skipped,
     * ensuring one bad app entry doesn't prevent loading the rest.
     */
    suspend fun loadApps(iconPackManager: IconPackManager? = null, useThemedIcons: Boolean = false): List<AppInfo> = withContext(Dispatchers.IO) {
        val profileApps = loadProfileApps(iconPackManager)
        if (profileApps.isNotEmpty()) return@withContext profileApps

        val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        val packLoaded = iconPackManager?.isLoaded() == true

        val resolvedList = try {
            pm.queryIntentActivities(intent, 0)
        } catch (e: Exception) {
            Log.e(TAG, "queryIntentActivities failed", e)
            return@withContext emptyList()
        }

        resolvedList.mapNotNull { ri ->
            try {
                val ai = ri.activityInfo ?: return@mapNotNull null
                val appInfo = ai.applicationInfo ?: return@mapNotNull null
                val sys = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val systemIcon: Drawable? = try {
                    if (useThemedIcons && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        try { ai.loadIcon(pm) } catch (_: Exception) { ri.loadIcon(pm) }
                    } else ri.loadIcon(pm)
                } catch (_: Exception) { null }
                val icon = if (packLoaded && systemIcon != null) {
                    try {
                        iconPackManager?.resolveIcon(ComponentName(ai.packageName, ai.name)) ?: systemIcon
                    } catch (_: Exception) { systemIcon }
                } else systemIcon
                val installTime = try { pm.getPackageInfo(ai.packageName, 0).firstInstallTime } catch (_: Exception) { 0L }
                AppInfo(
                    label = ri.loadLabel(pm)?.toString() ?: ai.packageName,
                    packageName = ai.packageName,
                    activityName = ai.name,
                    icon = icon,
                    isSystemApp = sys,
                    firstInstallTime = installTime,
                    installSource = installSourceFor(ai.packageName),
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed to resolve app: ${ri.activityInfo?.packageName}", e)
                null
            }
        }.filter { it.packageName != context.packageName }
            .sortedBy { it.label.lowercase() }
            .distinctBy { it.key }
    }

    private fun loadProfileApps(iconPackManager: IconPackManager?): List<AppInfo> {
        val launcher = launcherApps ?: return emptyList()
        val currentUser = Process.myUserHandle()
        val profiles = runCatching { launcher.profiles }.getOrDefault(emptyList())
        if (profiles.isEmpty()) return emptyList()

        return profiles.flatMap { user ->
            val activities = runCatching { launcher.getActivityList(null, user) }
                .onFailure { Log.w(TAG, "getActivityList failed for profile $user", it) }
                .getOrDefault(emptyList())
            activities.mapNotNull { info -> appInfoFromLauncher(info, user, currentUser, iconPackManager) }
        }.filter { it.packageName != context.packageName }
            .sortedBy { it.label.lowercase() }
            .distinctBy { it.key }
    }

    private fun appInfoFromLauncher(
        info: LauncherActivityInfo,
        user: UserHandle,
        currentUser: UserHandle,
        iconPackManager: IconPackManager?,
    ): AppInfo? = try {
        val component = info.componentName
        val appInfo = info.applicationInfo
        val sys = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        val systemIcon: Drawable? = runCatching { info.getIcon(0) }.getOrNull()
        val packLoaded = iconPackManager?.isLoaded() == true
        val icon = if (packLoaded && systemIcon != null) {
            runCatching { iconPackManager?.resolveIcon(component) ?: systemIcon }.getOrDefault(systemIcon)
        } else systemIcon
        val workProfile = user != currentUser
        val serial = if (workProfile) {
            runCatching { userManager?.getSerialNumberForUser(user) ?: 0L }.getOrDefault(0L)
        } else 0L
        AppInfo(
            label = info.label?.toString() ?: component.packageName,
            packageName = component.packageName,
            activityName = component.className,
            icon = icon,
            isSystemApp = sys,
            firstInstallTime = runCatching { info.firstInstallTime }.getOrDefault(0L),
            installSource = installSourceFor(component.packageName),
            isWorkProfile = workProfile,
            profileSerial = serial,
            userHandle = if (workProfile) user else null,
        )
    } catch (e: Exception) {
        Log.w(TAG, "Failed to resolve launcher profile app", e)
        null
    }

    private fun installSourceFor(packageName: String): String = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val info = pm.getInstallSourceInfo(packageName)
            info.installingPackageName ?: info.initiatingPackageName ?: info.originatingPackageName ?: ""
        } else {
            @Suppress("DEPRECATION")
            pm.getInstallerPackageName(packageName) ?: ""
        }
    } catch (_: Exception) {
        ""
    }

    /**
     * Check if a package still exists before performing operations on it.
     * Prevents the race condition Lawnchair fixed in 15 Beta 2 where
     * customizing an app being simultaneously uninstalled caused a crash.
     */
    fun isPackageInstalled(packageName: String): Boolean {
        return try {
            pm.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    fun isAppAvailable(app: AppInfo): Boolean {
        val user = app.userHandle
        if (user != null) {
            return runCatching {
                launcherApps?.getActivityList(app.packageName, user)
                    ?.any { it.componentName == app.componentName } == true
            }.getOrDefault(false)
        }
        return isPackageInstalled(app.packageName)
    }

    fun launchApp(app: AppInfo) {
        if (!isAppAvailable(app)) {
            Log.w(TAG, "Package not installed: ${app.packageName}")
            return
        }
        try {
            val user = app.userHandle
            if (user != null) {
                launcherApps?.startMainActivity(app.componentName, user, null, null)
                return
            }
            val i = pm.getLaunchIntentForPackage(app.packageName)
                ?: Intent(Intent.ACTION_MAIN).apply {
                    component = app.componentName
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
            i.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            context.startActivity(i)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch ${app.packageName}", e)
        }
    }

    fun openAppInfo(app: AppInfo) {
        try {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${app.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open app info for ${app.packageName}", e)
        }
    }

    fun uninstallApp(app: AppInfo) {
        try {
            context.startActivity(Intent(Intent.ACTION_DELETE).apply {
                data = Uri.parse("package:${app.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
        } catch (e: Exception) {
            Log.e(TAG, "Failed to uninstall ${app.packageName}", e)
        }
    }
}
