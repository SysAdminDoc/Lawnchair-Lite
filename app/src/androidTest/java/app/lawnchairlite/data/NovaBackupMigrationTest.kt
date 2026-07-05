package app.lawnchairlite.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class NovaBackupMigrationTest {

    @Test
    fun convertsNovaBackupZipToLauncherJson() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val backup = createNovaBackup(context)

        val migrated = NovaBackupMigrationConverter(context).convertIfSupported(backup)

        assertNotNull(migrated)
        val json = JSONObject(migrated!!.json)
        assertEquals("Nova Launcher", migrated.source)
        assertEquals("Nova Launcher", json.getString("migration_source"))
        assertEquals(4, json.getInt("grid_cols"))
        assertEquals(5, json.getInt("grid_rows"))
        assertEquals(5, json.getInt("dock_count"))

        val home = deserializeGrid(json.getString("home_grid"))
        assertEquals(GridCell.App("com.android.chrome/com.google.android.apps.chrome.Main"), home[0])
        val folder = home[1] as GridCell.Folder
        assertEquals("Tools", folder.name)
        assertEquals(listOf("com.android.settings/com.android.settings.Settings"), folder.appKeys)

        val dock = deserializeGrid(json.getString("dock_grid"))
        assertEquals(
            GridCell.App("com.google.android.dialer/com.google.android.dialer.extensions.GoogleDialtactsActivity"),
            dock[0],
        )

        val preview = BackupImportPreview.fromJson(migrated.json)
        assertTrue(preview.canImport)
        assertEquals("Nova Launcher", preview.migrationSource)
        assertTrue(preview.migrationUnsupported.any { it.contains("Widgets") })
        assertTrue(preview.migrationUnsupported.any { it.contains("Nova appearance") })
    }

    private fun createNovaBackup(context: Context): ByteArray {
        val dbFile = File.createTempFile("nova-test", ".db", context.cacheDir)
        val db = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        try {
            db.execSQL(
                """
                CREATE TABLE favorites (
                    _id INTEGER PRIMARY KEY,
                    title TEXT,
                    intent TEXT,
                    container INTEGER,
                    screen INTEGER,
                    cellX INTEGER,
                    cellY INTEGER,
                    itemType INTEGER,
                    rank INTEGER
                )
                """.trimIndent(),
            )
            insertFavorite(
                db,
                id = 1,
                title = "Chrome",
                intent = "#Intent;component=com.android.chrome/com.google.android.apps.chrome.Main;end",
                container = -100,
                screen = 0,
                cellX = 0,
                cellY = 0,
                itemType = 0,
            )
            insertFavorite(
                db,
                id = 2,
                title = "Tools",
                intent = "",
                container = -100,
                screen = 0,
                cellX = 1,
                cellY = 0,
                itemType = 2,
            )
            insertFavorite(
                db,
                id = 3,
                title = "Settings",
                intent = "#Intent;component=com.android.settings/com.android.settings.Settings;end",
                container = 2,
                screen = 0,
                cellX = 0,
                cellY = 0,
                itemType = 0,
                rank = 0,
            )
            insertFavorite(
                db,
                id = 4,
                title = "Phone",
                intent = "#Intent;component=com.google.android.dialer/com.google.android.dialer.extensions.GoogleDialtactsActivity;end",
                container = -101,
                screen = 0,
                cellX = 0,
                cellY = 0,
                itemType = 0,
            )
            insertFavorite(
                db,
                id = 5,
                title = "Clock",
                intent = "",
                container = -100,
                screen = 0,
                cellX = 2,
                cellY = 0,
                itemType = 4,
            )
        } finally {
            db.close()
        }

        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("nova.xml"))
            zip.write(
                """
                <?xml version="1.0" encoding="utf-8"?>
                <map>
                    <string name="desktop_grid">5x4</string>
                    <int name="dock_grid_cols" value="5" />
                </map>
                """.trimIndent().toByteArray(),
            )
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("nova.db"))
            zip.write(dbFile.readBytes())
            zip.closeEntry()
        }
        dbFile.delete()
        return out.toByteArray()
    }

    private fun insertFavorite(
        db: SQLiteDatabase,
        id: Long,
        title: String,
        intent: String,
        container: Long,
        screen: Int,
        cellX: Int,
        cellY: Int,
        itemType: Int,
        rank: Int = 0,
    ) {
        db.execSQL(
            """
            INSERT INTO favorites (_id, title, intent, container, screen, cellX, cellY, itemType, rank)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any>(id, title, intent, container, screen, cellX, cellY, itemType, rank),
        )
    }
}
