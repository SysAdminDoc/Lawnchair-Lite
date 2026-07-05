package app.lawnchairlite.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Lawnchair Lite - Icon Pack Support
 *
 * Stability improvements:
 * - Byte-bounded icon bitmap cache with explicit recycling (prevents OOM on large icon packs)
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

class IconPackManager(
    private val context: Context,
    maxIconCacheBytes: Int = defaultIconCacheBytes(),
) {

    companion object {
        private const val TAG = "IconPackManager"
        private const val MIN_CACHE_BYTES = 4 * 1024 * 1024
        private const val MAX_CACHE_BYTES = 24 * 1024 * 1024
        private const val MAX_MISSING_KEYS = 2048
        private const val MAX_ICON_BITMAP_DP = 64

        private fun defaultIconCacheBytes(): Int {
            val heapBudget = Runtime.getRuntime().maxMemory() / 16L
            return heapBudget.coerceIn(MIN_CACHE_BYTES.toLong(), MAX_CACHE_BYTES.toLong()).toInt()
        }
    }

    private val pm: PackageManager = context.packageManager
    private val mutex = Mutex()
    private val maxIconBitmapPx = max(
        48,
        (context.resources.displayMetrics.density * MAX_ICON_BITMAP_DP).roundToInt(),
    )
    @Volatile private var loadedPack: String? = null
    @Volatile private var filterMap: Map<String, String> = emptyMap()
    @Volatile private var packResources: Resources? = null
    @Volatile private var packPackageName: String? = null
    private val iconCache = BoundedIconBitmapCache<CachedBitmapIcon>(maxIconCacheBytes)
    private val missingKeys = LinkedHashSet<String>(MAX_MISSING_KEYS)

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
                        pm.getApplicationLabel(appInfo).toString(),
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
                iconCache.clear()
                missingKeys.clear()
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
        loadedPack = null; filterMap = emptyMap(); packResources = null; packPackageName = null; iconCache.clear(); missingKeys.clear()
    }

    fun resolveIcon(component: ComponentName): Drawable? {
        // Capture volatile references to prevent races with loadPack/clearPack
        val res = packResources ?: return null
        val pkg = packPackageName ?: return null
        val map = filterMap
        val key = "${component.packageName}/${component.className}"
        iconCache.get(key)?.let { return it.toDrawable(context.resources) }
        if (key in missingKeys) return null
        val drawableName = map[key] ?: run { rememberMissingKey(key); return null }
        return try {
            val bitmap = loadBitmap(res, pkg, drawableName) ?: run {
                rememberMissingKey(key)
                return null
            }
            val cached = CachedBitmapIcon(bitmap)
            if (iconCache.put(key, cached)) cached.toDrawable(context.resources) else null
        } catch (e: Exception) {
            Log.w(TAG, "resolveIcon failed for $key", e)
            rememberMissingKey(key)
            null
        }
    }

    fun resolveIcon(appKey: String): Drawable? {
        val parts = appKey.split("/", limit = 2); if (parts.size != 2) return null
        return resolveIcon(ComponentName(parts[0], parts[1]))
    }

    fun mappedCount(): Int = filterMap.size
    fun isLoaded(): Boolean = loadedPack != null && filterMap.isNotEmpty()

    /** Load a few sample icons from an icon pack for preview (without fully loading it). */
    fun previewIcons(packageName: String, count: Int = 4): List<Drawable?> {
        return try {
            val res = pm.getResourcesForApplication(packageName)
            val map = mutableMapOf<String, String>()
            tryParseXmlResource(packageName, res, map) || tryParseAssets(packageName, res, map)
            map.values.take(count).mapNotNull { name -> loadDrawable(res, packageName, name) }
        } catch (e: Exception) {
            Log.w(TAG, "previewIcons failed for $packageName", e)
            emptyList()
        }
    }

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
        val bitmap = loadBitmap(res, packageName, name) ?: return null
        return BitmapDrawable(context.resources, bitmap)
    }

    private fun loadBitmap(res: Resources, packageName: String, name: String): Bitmap? {
        return try {
            val id = res.getIdentifier(name, "drawable", packageName)
            if (id != 0) drawableToBitmap(res.getDrawable(id, null).mutate()) else null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load drawable: $name from $packageName", e)
            null
        }
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        val intrinsicWidth = drawable.intrinsicWidth.takeIf { it > 0 } ?: maxIconBitmapPx
        val intrinsicHeight = drawable.intrinsicHeight.takeIf { it > 0 } ?: maxIconBitmapPx
        val largestSide = max(intrinsicWidth, intrinsicHeight).coerceAtLeast(1)
        val scale = minOf(1f, maxIconBitmapPx.toFloat() / largestSide.toFloat())
        val width = max(1, (intrinsicWidth * scale).roundToInt())
        val height = max(1, (intrinsicHeight * scale).roundToInt())
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val previousBounds = Rect(drawable.bounds)
        drawable.setBounds(0, 0, width, height)
        drawable.draw(canvas)
        drawable.bounds = previousBounds
        return bitmap
    }

    private fun rememberMissingKey(key: String) {
        missingKeys.remove(key)
        missingKeys.add(key)
        while (missingKeys.size > MAX_MISSING_KEYS) {
            val iterator = missingKeys.iterator()
            if (!iterator.hasNext()) return
            iterator.next()
            iterator.remove()
        }
    }

    private class CachedBitmapIcon(private val bitmap: Bitmap) : RecyclableIconEntry {
        override val bytes: Int = bitmap.allocationByteCount

        override fun recycle() {
            if (!bitmap.isRecycled) bitmap.recycle()
        }

        fun toDrawable(res: Resources): Drawable? {
            if (bitmap.isRecycled) return null
            val copy = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false)
            return BitmapDrawable(res, copy)
        }
    }
}
