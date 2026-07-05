package app.lawnchairlite.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject
import org.xml.sax.InputSource
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.StringReader
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

data class PreparedBackupImport(
    val json: String,
    val preview: BackupImportPreview,
)

class BackupImportPreparer(private val context: Context) {
    private val nova = NovaBackupMigrationConverter(context)

    fun prepare(payload: ByteArray): PreparedBackupImport {
        val json = runCatching { nova.convertIfSupported(payload) }.getOrNull()?.json
            ?: payload.toString(Charsets.UTF_8)
        return PreparedBackupImport(json = json, preview = BackupImportPreview.fromJson(json))
    }
}

data class MigratedBackup(
    val json: String,
    val source: String,
    val unsupported: List<String>,
)

class NovaBackupMigrationConverter(private val context: Context) {
    fun convertIfSupported(payload: ByteArray): MigratedBackup? {
        if (!payload.isZipPayload()) return null
        val extracted = extractNovaBackup(payload) ?: return null
        val dbFile = writeTempDb(extracted.databaseBytes)
        return try {
            val grid = parseNovaGrid(extracted.xml)
            val converted = convertDatabase(dbFile, grid)
            MigratedBackup(
                json = converted.toJson().toString(2),
                source = MIGRATION_SOURCE,
                unsupported = converted.unsupported,
            )
        } finally {
            runCatching { dbFile.delete() }
        }
    }

    private fun convertDatabase(dbFile: File, grid: NovaGrid): ConvertedNovaBackup {
        val db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        return db.use {
            val rows = readFavorites(it)
            val topLevel = rows.filter { row -> row.container == NOVA_CONTAINER_DESKTOP || row.container == NOVA_CONTAINER_HOTSEAT }
            val folderIds = topLevel.filter { row -> row.itemType == ITEM_TYPE_FOLDER }.map { row -> row.id }.toSet()
            val childrenByFolder = rows.filter { row -> row.container in folderIds }.groupBy { row -> row.container }
            val pageSize = grid.cols * grid.rows
            val maxScreen = topLevel.filter { it.container == NOVA_CONTAINER_DESKTOP }.maxOfOrNull { it.screen } ?: 0
            val pageCount = (maxScreen + 1).coerceIn(1, MAX_PAGES)
            val home = MutableList<GridCell?>(pageCount * pageSize) { null }
            val dock = MutableList<GridCell?>(grid.hotseat) { null }
            val unsupported = linkedMapOf<String, Int>()

            fun note(label: String) {
                unsupported[label] = (unsupported[label] ?: 0) + 1
            }

            fun placeHome(row: NovaFavorite, cell: GridCell?) {
                if (cell == null) return
                if (row.screen !in 0 until pageCount || row.cellX !in 0 until grid.cols || row.cellY !in 0 until grid.rows) {
                    note("Items outside the ${grid.cols}x${grid.rows} grid")
                    return
                }
                val index = row.screen * pageSize + row.cellY * grid.cols + row.cellX
                if (home[index] != null) {
                    note("Overlapping home cells")
                    return
                }
                home[index] = cell
            }

            fun placeDock(row: NovaFavorite, cell: GridCell?) {
                if (cell == null) return
                val index = row.cellX.takeIf { it in dock.indices } ?: row.screen.takeIf { it in dock.indices }
                if (index == null) {
                    note("Dock items outside the ${grid.hotseat}-slot dock")
                    return
                }
                if (dock[index] != null) {
                    note("Overlapping dock cells")
                    return
                }
                dock[index] = cell
            }

            topLevel.sortedWith(compareBy<NovaFavorite> { it.container }.thenBy { it.screen }.thenBy { it.cellY }.thenBy { it.cellX }).forEach { row ->
                val cell = row.toGridCell(childrenByFolder[row.id].orEmpty(), ::note)
                when (row.container) {
                    NOVA_CONTAINER_DESKTOP -> placeHome(row, cell)
                    NOVA_CONTAINER_HOTSEAT -> placeDock(row, cell)
                }
            }

            unsupported["Nova appearance, drawer, gesture, wallpaper, and widget binding settings"] = 1

            ConvertedNovaBackup(
                cols = grid.cols,
                rows = grid.rows,
                hotseat = grid.hotseat,
                home = home,
                dock = dock,
                unsupported = unsupported.map { (label, count) -> if (count > 1) "$label ($count)" else label },
            )
        }
    }

    private fun NovaFavorite.toGridCell(children: List<NovaFavorite>, note: (String) -> Unit): GridCell? = when (itemType) {
        ITEM_TYPE_APPLICATION, ITEM_TYPE_SHORTCUT -> appKeyFromIntent(intent)?.let { GridCell.App(it) } ?: run {
            note("Apps without launchable components")
            null
        }
        ITEM_TYPE_FOLDER -> {
            val appKeys = children.sortedWith(compareBy<NovaFavorite> { it.rank }.thenBy { it.cellY }.thenBy { it.cellX })
                .mapNotNull { child ->
                    when (child.itemType) {
                        ITEM_TYPE_APPLICATION, ITEM_TYPE_SHORTCUT -> appKeyFromIntent(child.intent) ?: run {
                            note("Folder apps without launchable components")
                            null
                        }
                        ITEM_TYPE_DEEP_SHORTCUT -> {
                            note("Deep shortcuts inside folders")
                            null
                        }
                        ITEM_TYPE_APPWIDGET -> {
                            note("Widgets inside folders")
                            null
                        }
                        else -> {
                            note("Unsupported folder items")
                            null
                        }
                    }
                }
                .distinct()
                .take(80)
            if (appKeys.isEmpty()) {
                note("Empty or unsupported folders")
                null
            } else {
                GridCell.Folder(title.ifBlank { "Folder" }.take(40), appKeys)
            }
        }
        ITEM_TYPE_APPWIDGET -> {
            note("Widgets require re-adding after migration")
            null
        }
        ITEM_TYPE_DEEP_SHORTCUT -> {
            note("Deep shortcuts require re-pinning after migration")
            null
        }
        else -> {
            note("Unsupported Nova item types")
            null
        }
    }

    private fun readFavorites(db: SQLiteDatabase): List<NovaFavorite> {
        db.rawQuery(
            """
            SELECT _id, title, intent, container, screen, cellX, cellY, itemType, rank
            FROM favorites
            WHERE container IN ($NOVA_CONTAINER_DESKTOP, $NOVA_CONTAINER_HOTSEAT)
               OR container IN (
                   SELECT _id FROM favorites
                   WHERE itemType = $ITEM_TYPE_FOLDER
                     AND container IN ($NOVA_CONTAINER_DESKTOP, $NOVA_CONTAINER_HOTSEAT)
               )
            """.trimIndent(),
            emptyArray(),
        ).use { cursor ->
            return buildList {
                while (cursor.moveToNext()) {
                    add(
                        NovaFavorite(
                            id = cursor.long("_id"),
                            title = cursor.string("title"),
                            intent = cursor.string("intent"),
                            container = cursor.long("container"),
                            screen = cursor.int("screen"),
                            cellX = cursor.int("cellX"),
                            cellY = cursor.int("cellY"),
                            itemType = cursor.int("itemType"),
                            rank = cursor.int("rank"),
                        ),
                    )
                }
            }
        }
    }

    private fun appKeyFromIntent(raw: String): String? {
        if (raw.isBlank()) return null
        val cleaned = raw.replace(Regex("extendedLaunchFlags=0x[0-9a-fA-F]+;"), "")
        val parsed = runCatching { Intent.parseUri(cleaned, 0) }.getOrNull()
            ?: runCatching { Intent.parseUri(cleaned, Intent.URI_INTENT_SCHEME) }.getOrNull()
        val component = parsed?.component ?: parseComponent(cleaned)
        return component?.flattenToString()
    }

    private fun parseComponent(raw: String): ComponentName? {
        val match = Regex("(?:component|cmp)=([^;]+)").find(raw) ?: return null
        return ComponentName.unflattenFromString(match.groupValues[1])
    }

    private fun extractNovaBackup(payload: ByteArray): ExtractedNovaBackup? {
        var db: ByteArray? = null
        var xml: String? = null
        ZipInputStream(ByteArrayInputStream(payload)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name.substringAfterLast('/').lowercase()
                when (name) {
                    "nova.db" -> db = zip.readEntryBytes(MAX_DB_BYTES)
                    "nova.xml" -> xml = zip.readEntryBytes(MAX_XML_BYTES).toString(Charsets.UTF_8)
                }
                zip.closeEntry()
            }
        }
        val databaseBytes = db ?: return null
        val xmlText = xml ?: return null
        return ExtractedNovaBackup(databaseBytes, xmlText)
    }

    private fun writeTempDb(bytes: ByteArray): File {
        val dir = File(context.cacheDir, "backup-migration").apply { mkdirs() }
        return File.createTempFile("nova-", ".db", dir).apply { writeBytes(bytes) }
    }

    private fun parseNovaGrid(xml: String): NovaGrid {
        val values = parseSharedPrefsXml(xml)
        val desktop = values["desktop_grid"].orEmpty()
        val match = Regex("""(\d+)x(\d+)""").find(desktop)
        val rows = match?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: values["desktop_grid_rows"]?.toIntOrNull()
            ?: 5
        val cols = match?.groupValues?.getOrNull(2)?.toIntOrNull()
            ?: values["desktop_grid_cols"]?.toIntOrNull()
            ?: 4
        val hotseat = values["dock_grid_cols"]?.toIntOrNull() ?: 5
        return NovaGrid(
            cols = cols.coerceIn(3, 8),
            rows = rows.coerceIn(3, 10),
            hotseat = hotseat.coerceIn(3, 7),
        )
    }

    private fun parseSharedPrefsXml(xml: String): Map<String, String> = runCatching {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isIgnoringComments = true
            isCoalescing = true
            isExpandEntityReferences = false
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        }
        val doc = factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
        val root = doc.documentElement
        buildMap {
            for (i in 0 until root.childNodes.length) {
                val node = root.childNodes.item(i)
                val attrs = node.attributes ?: continue
                val name = attrs.getNamedItem("name")?.nodeValue ?: continue
                val value = attrs.getNamedItem("value")?.nodeValue ?: node.textContent.orEmpty()
                put(name, value.trim())
            }
        }
    }.getOrDefault(emptyMap())

    private fun Cursor.long(name: String, default: Long = 0L): Long {
        val index = getColumnIndex(name)
        return if (index >= 0 && !isNull(index)) getLong(index) else default
    }

    private fun Cursor.int(name: String, default: Int = 0): Int {
        val index = getColumnIndex(name)
        return if (index >= 0 && !isNull(index)) getInt(index) else default
    }

    private fun Cursor.string(name: String): String {
        val index = getColumnIndex(name)
        return if (index >= 0 && !isNull(index)) getString(index).orEmpty() else ""
    }

    private fun ByteArray.isZipPayload(): Boolean =
        size >= 4 && this[0] == 0x50.toByte() && this[1] == 0x4B.toByte()

    private fun ZipInputStream.readEntryBytes(maxBytes: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val read = read(buffer)
            if (read <= 0) break
            total += read
            if (total > maxBytes) error("Nova backup entry exceeds $maxBytes bytes")
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    private data class ExtractedNovaBackup(
        val databaseBytes: ByteArray,
        val xml: String,
    )

    private data class NovaGrid(
        val cols: Int,
        val rows: Int,
        val hotseat: Int,
    )

    private data class NovaFavorite(
        val id: Long,
        val title: String,
        val intent: String,
        val container: Long,
        val screen: Int,
        val cellX: Int,
        val cellY: Int,
        val itemType: Int,
        val rank: Int,
    )

    private data class ConvertedNovaBackup(
        val cols: Int,
        val rows: Int,
        val hotseat: Int,
        val home: List<GridCell?>,
        val dock: List<GridCell?>,
        val unsupported: List<String>,
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            put("version", "Nova migration")
            put("schema", BackupImportPreview.CURRENT_SCHEMA)
            put("exported_at", System.currentTimeMillis())
            put("migration_source", MIGRATION_SOURCE)
            put("migration_unsupported", JSONArray(unsupported))
            put("grid_cols", cols)
            put("grid_rows", rows)
            put("dock_count", hotseat)
            put("home_grid", serializeGrid(home))
            put("dock_grid", serializeGrid(dock))
        }
    }

    private companion object {
        const val MIGRATION_SOURCE = "Nova Launcher"
        const val NOVA_CONTAINER_DESKTOP = -100L
        const val NOVA_CONTAINER_HOTSEAT = -101L
        const val ITEM_TYPE_APPLICATION = 0
        const val ITEM_TYPE_SHORTCUT = 1
        const val ITEM_TYPE_FOLDER = 2
        const val ITEM_TYPE_APPWIDGET = 4
        const val ITEM_TYPE_DEEP_SHORTCUT = 6
        const val MAX_PAGES = 20
        const val MAX_DB_BYTES = 16 * 1024 * 1024
        const val MAX_XML_BYTES = 1024 * 1024
    }
}
