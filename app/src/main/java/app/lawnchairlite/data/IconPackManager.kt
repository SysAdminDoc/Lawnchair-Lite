package app.lawnchairlite.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/**
 * Lawnchair Lite v2.1.0 - Icon Pack Support
 *
 * Stability improvements:
 * - LruCache with size limit (prevents OOM on large icon packs)
 * - All resource loading wrapped in try-catch (Resources.NotFoundException, etc.)
 * - Icon pack discovery tolerant of OEM PM quirks
 * - XML parsing failures don't crash, just return false
 * - Thread-safe via Mutex on all mutable state
 */

data class IconPackInfo(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
)

class IconPackManager(private val context: Context) {

    companion object {
        private const val TAG = "IconPackManager"
        private const val MAX_CACHE_SIZE = 500
    }

    private val pm: PackageManager = context.packageManager
    private val mutex = Mutex()
    private var loadedPack: String? = null
    private var filterMap: Map<String, String> = emptyMap()
    private var packResources: Resources? = null
    private var packPackageName: String? = null
    private val iconCache = LruCache<String, Drawable?>(MAX_CACHE_SIZE)
    // Track keys we've already attempted to resolve (including misses).
    // Without this, LruCache can't distinguish "never looked up" from "looked up, got null".
    private val attemptedKeys = HashSet<String>(MAX_CACHE_SIZE)

    fun getInstalledPacks(): List<IconPackInfo> {
        val seen = mutableSetOf<String>()
        val packs = mutableListOf<IconPackInfo>()
        val actions = listOf(
            "org.adw.launcher.THEMES", "com.novalauncher.THEME",
            "com.teslacoilsw.launcher.THEME", "com.gau.go.launcherex.theme",
            "org.adw.launcher.icons.ACTION_PICK_ICON",
        )
        for (action in actions) {
            val resolvedList: List<ResolveInfo> = try {
                pm.queryIntentActivities(Intent(action), PackageManager.GET_META_DATA)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to query icon packs for action: $action", e)
                emptyList()
            }
            for (ri in resolvedList) {
                val pkg = ri.activityInfo?.packageName ?: continue
                if (pkg in seen || pkg == context.packageName) continue
                seen.add(pkg)
                try {
                    val appInfo = pm.getApplicationInfo(pkg, 0)
                    packs.add(IconPackInfo(
                        pkg,
                        pm.getApplicationLabel(appInfo)?.toString() ?: pkg,
                        try { pm.getApplicationIcon(appInfo) } catch (_: Exception) { null }
                    ))
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to get icon pack info: $pkg", e)
                }
            }
        }
        return packs.sortedBy { it.label.lowercase() }
    }

    suspend fun loadPack(packageName: String): Boolean = mutex.withLock {
        if (packageName == loadedPack && filterMap.isNotEmpty()) return true
        return@withLock withContext(Dispatchers.IO) {
            try {
                iconCache.evictAll()
                attemptedKeys.clear()
                val res = pm.getResourcesForApplication(packageName)
                val map = mutableMapOf<String, String>()
                val parsed = tryParseXmlResource(packageName, res, map) || tryParseAssets(packageName, res, map)
                if (parsed && map.isNotEmpty()) {
                    filterMap = map; packResources = res; packPackageName = packageName; loadedPack = packageName
                    Log.d(TAG, "Loaded icon pack: $packageName (${map.size} mappings)")
                    true
                } else {
                    Log.w(TAG, "Icon pack had no valid mappings: $packageName")
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load icon pack: $packageName", e)
                false
            }
        }
    }

    suspend fun clearPack() = mutex.withLock {
        loadedPack = null; filterMap = emptyMap(); packResources = null; packPackageName = null; iconCache.evictAll(); attemptedKeys.clear()
    }

    fun resolveIcon(component: ComponentName): Drawable? {
        val res = packResources ?: return null; val pkg = packPackageName ?: return null
        val key = "${component.packageName}/${component.className}"
        // Fast path: already resolved (hit or miss)
        if (key in attemptedKeys) return iconCache.get(key)
        val drawableName = filterMap[key] ?: run { attemptedKeys.add(key); return null }
        val icon = loadDrawable(res, pkg, drawableName)
        iconCache.put(key, icon)
        attemptedKeys.add(key)
        return icon
    }

    fun resolveIcon(appKey: String): Drawable? {
        val parts = appKey.split("/", limit = 2); if (parts.size != 2) return null
        return resolveIcon(ComponentName(parts[0], parts[1]))
    }

    fun mappedCount(): Int = filterMap.size
    fun isLoaded(): Boolean = loadedPack != null && filterMap.isNotEmpty()

    private fun tryParseXmlResource(packageName: String, res: Resources, map: MutableMap<String, String>): Boolean {
        return try {
            val id = res.getIdentifier("appfilter", "xml", packageName)
            if (id == 0) return false
            parseAppFilter(res.getXml(id), map); map.isNotEmpty()
        } catch (e: Exception) {
            Log.w(TAG, "XML resource parse failed for $packageName", e)
            false
        }
    }

    private fun tryParseAssets(packageName: String, res: Resources, map: MutableMap<String, String>): Boolean {
        return try {
            val stream = res.assets.open("appfilter.xml")
            val factory = XmlPullParserFactory.newInstance(); val parser = factory.newPullParser()
            parser.setInput(stream, "UTF-8"); parseAppFilter(parser, map); stream.close(); map.isNotEmpty()
        } catch (e: Exception) {
            Log.w(TAG, "Asset parse failed for $packageName", e)
            false
        }
    }

    private fun parseAppFilter(parser: XmlPullParser, map: MutableMap<String, String>) {
        try {
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.name == "item") {
                    val componentStr = parser.getAttributeValue(null, "component")
                    val drawableName = parser.getAttributeValue(null, "drawable")
                    if (componentStr != null && drawableName != null) {
                        extractComponentKey(componentStr)?.let { key -> map[key] = drawableName }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error during appfilter parse", e)
        }
    }

    private fun extractComponentKey(raw: String): String? {
        val start = raw.indexOf('{'); val end = raw.indexOf('}')
        if (start < 0 || end < 0 || end <= start + 1) return null
        val inner = raw.substring(start + 1, end); return if ('/' in inner) inner else null
    }

    private fun loadDrawable(res: Resources, packageName: String, name: String): Drawable? {
        return try {
            val id = res.getIdentifier(name, "drawable", packageName)
            if (id != 0) res.getDrawable(id, null) else null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load drawable: $name from $packageName", e)
            null
        }
    }
}
