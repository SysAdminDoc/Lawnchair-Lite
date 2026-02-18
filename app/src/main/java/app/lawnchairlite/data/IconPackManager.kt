package app.lawnchairlite.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.res.Resources
import android.graphics.drawable.Drawable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/**
 * Lawnchair Lite v0.9.0 - Icon Pack Support
 *
 * Discovers installed icon packs, parses their appfilter.xml,
 * and resolves icons by ComponentName. Compatible with the standard
 * ADW/Nova/Apex icon pack format used by virtually all icon packs.
 */

data class IconPackInfo(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
)

class IconPackManager(private val context: Context) {

    private val pm: PackageManager = context.packageManager
    private val mutex = Mutex()

    // Cache: icon pack package -> (ComponentName string -> drawable name)
    private var loadedPack: String? = null
    private var filterMap: Map<String, String> = emptyMap()
    private var packResources: Resources? = null
    private var packPackageName: String? = null
    private val iconCache = mutableMapOf<String, Drawable?>()

    // ── Discovery ────────────────────────────────────────────────────────

    /** Find all installed icon packs by querying standard launcher theme actions. */
    fun getInstalledPacks(): List<IconPackInfo> {
        val seen = mutableSetOf<String>()
        val packs = mutableListOf<IconPackInfo>()

        val actions = listOf(
            "org.adw.launcher.THEMES",
            "com.novalauncher.THEME",
            "com.teslacoilsw.launcher.THEME",
            "com.gau.go.launcherex.theme",
            "org.adw.launcher.icons.ACTION_PICK_ICON",
        )

        for (action in actions) {
            val intent = Intent(action)
            val resolvedList: List<ResolveInfo> = runCatching {
                pm.queryIntentActivities(intent, PackageManager.GET_META_DATA)
            }.getOrDefault(emptyList())

            for (ri in resolvedList) {
                val pkg = ri.activityInfo.packageName
                if (pkg in seen || pkg == context.packageName) continue
                seen.add(pkg)
                runCatching {
                    val appInfo = pm.getApplicationInfo(pkg, 0)
                    packs.add(IconPackInfo(
                        packageName = pkg,
                        label = pm.getApplicationLabel(appInfo).toString(),
                        icon = pm.getApplicationIcon(appInfo),
                    ))
                }
            }
        }

        return packs.sortedBy { it.label.lowercase() }
    }

    // ── Loading ──────────────────────────────────────────────────────────

    /** Load an icon pack's appfilter.xml and cache the component -> drawable mapping. */
    suspend fun loadPack(packageName: String): Boolean = mutex.withLock {
        if (packageName == loadedPack && filterMap.isNotEmpty()) return true
        return@withLock withContext(Dispatchers.IO) {
            runCatching {
                iconCache.clear()
                val res = pm.getResourcesForApplication(packageName)
                val map = mutableMapOf<String, String>()

                // Try res/xml/appfilter.xml first, then assets/appfilter.xml
                val parsed = tryParseXmlResource(packageName, res, map)
                    || tryParseAssets(packageName, res, map)

                if (parsed && map.isNotEmpty()) {
                    filterMap = map
                    packResources = res
                    packPackageName = packageName
                    loadedPack = packageName
                    true
                } else {
                    false
                }
            }.getOrDefault(false)
        }
    }

    /** Clear the loaded icon pack. */
    suspend fun clearPack() = mutex.withLock {
        loadedPack = null
        filterMap = emptyMap()
        packResources = null
        packPackageName = null
        iconCache.clear()
    }

    // ── Resolution ───────────────────────────────────────────────────────

    /** Resolve an icon for the given component from the loaded pack. Returns null if no mapping. */
    fun resolveIcon(component: ComponentName): Drawable? {
        val res = packResources ?: return null
        val pkg = packPackageName ?: return null

        // Build the lookup key: "package/activity"
        val key = "${component.packageName}/${component.className}"
        if (key in iconCache) return iconCache[key]

        val drawableName = filterMap[key] ?: return null
        val icon = loadDrawable(res, pkg, drawableName)
        iconCache[key] = icon
        return icon
    }

    /** Resolve icon by package/activity key string. */
    fun resolveIcon(appKey: String): Drawable? {
        val parts = appKey.split("/", limit = 2)
        if (parts.size != 2) return null
        return resolveIcon(ComponentName(parts[0], parts[1]))
    }

    /** Number of mapped icons in the loaded pack. */
    fun mappedCount(): Int = filterMap.size

    /** Check if a pack is currently loaded. */
    fun isLoaded(): Boolean = loadedPack != null && filterMap.isNotEmpty()

    // ── XML Parsing ──────────────────────────────────────────────────────

    private fun tryParseXmlResource(packageName: String, res: Resources, map: MutableMap<String, String>): Boolean {
        return runCatching {
            val id = res.getIdentifier("appfilter", "xml", packageName)
            if (id == 0) return false
            val parser = res.getXml(id)
            parseAppFilter(parser, map)
            map.isNotEmpty()
        }.getOrDefault(false)
    }

    private fun tryParseAssets(packageName: String, res: Resources, map: MutableMap<String, String>): Boolean {
        return runCatching {
            val assets = res.assets
            val stream = assets.open("appfilter.xml")
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(stream, "UTF-8")
            parseAppFilter(parser, map)
            stream.close()
            map.isNotEmpty()
        }.getOrDefault(false)
    }

    private fun parseAppFilter(parser: XmlPullParser, map: MutableMap<String, String>) {
        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && parser.name == "item") {
                val componentStr = parser.getAttributeValue(null, "component")
                val drawableName = parser.getAttributeValue(null, "drawable")
                if (componentStr != null && drawableName != null) {
                    extractComponentKey(componentStr)?.let { key ->
                        map[key] = drawableName
                    }
                }
            }
            eventType = parser.next()
        }
    }

    /**
     * Extract "package/activity" from ComponentInfo string.
     * Format: "ComponentInfo{com.example.app/com.example.app.MainActivity}"
     */
    private fun extractComponentKey(raw: String): String? {
        val start = raw.indexOf('{')
        val end = raw.indexOf('}')
        if (start < 0 || end < 0 || end <= start + 1) return null
        val inner = raw.substring(start + 1, end)
        return if ('/' in inner) inner else null
    }

    private fun loadDrawable(res: Resources, packageName: String, name: String): Drawable? {
        return runCatching {
            val id = res.getIdentifier(name, "drawable", packageName)
            if (id != 0) res.getDrawable(id, null) else null
        }.getOrNull()
    }
}
