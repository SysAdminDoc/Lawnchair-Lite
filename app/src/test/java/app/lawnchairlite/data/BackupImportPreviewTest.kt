package app.lawnchairlite.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupImportPreviewTest {

    @Test
    fun legacyBackupWithoutSchemaIsImportableWithWarning() {
        val preview = BackupImportPreview.fromFields(
            mapOf(
                "theme" to "MIDNIGHT",
                "home_grid" to "app:com.example/.Main",
                "dock_grid" to "",
                "hidden_apps" to "com.private.app",
            ),
        )

        assertTrue(preview.canImport)
        assertEquals(0, preview.schemaVersion)
        assertEquals("Legacy backup without schema metadata", preview.warning)
        assertTrue(preview.sections.contains("Appearance"))
        assertTrue(preview.sections.contains("Layout & widgets"))
        assertEquals(listOf("Hidden apps"), preview.privateSections)
    }

    @Test
    fun futureSchemaIsRejectedBeforeImport() {
        val preview = BackupImportPreview.fromFields(
            mapOf(
                "schema" to 99,
                "theme" to "MIDNIGHT",
            ),
        )

        assertFalse(preview.canImport)
        assertEquals("Backup schema 99 is newer than supported schema 1", preview.error)
        assertTrue(preview.sections.contains("Appearance"))
    }

    @Test
    fun unknownFieldsAndInvalidEnumsAreReported() {
        val preview = BackupImportPreview.fromFields(
            mapOf(
                "schema" to 1,
                "theme" to "NOPE",
                "search_engine" to "GOOGLE",
                "mystery" to true,
                "omitted_private_sections" to listOf("search_history", "hidden_apps"),
            ),
        )

        assertTrue(preview.canImport)
        assertEquals(listOf("mystery"), preview.unknownFields)
        assertEquals(listOf("theme"), preview.skippedFields)
        assertEquals(listOf("search_history", "hidden_apps"), preview.omittedPrivateSections)
    }

    @Test
    fun drawerGroupsAreRecognizedAsDrawerSearchBackupSection() {
        val preview = BackupImportPreview.fromFields(
            mapOf(
                "schema" to 1,
                "drawer_groups" to "[]",
            ),
        )

        assertTrue(preview.canImport)
        assertEquals(listOf("Drawer & search"), preview.sections)
        assertEquals(emptyList<String>(), preview.unknownFields)
    }

    @Test
    fun iconOverridesAreRecognizedAsAppearanceBackupSection() {
        val preview = BackupImportPreview.fromFields(
            mapOf(
                "schema" to 1,
                "icon_overrides" to "{}",
            ),
        )

        assertTrue(preview.canImport)
        assertEquals(listOf("Appearance"), preview.sections)
        assertEquals(emptyList<String>(), preview.unknownFields)
    }

    @Test
    fun pageWallpaperDimsAreRecognizedAsAppearanceBackupSection() {
        val preview = BackupImportPreview.fromFields(
            mapOf(
                "schema" to 1,
                "page_wallpaper_dims" to "0=15|1=45",
            ),
        )

        assertTrue(preview.canImport)
        assertEquals(listOf("Appearance"), preview.sections)
        assertEquals(emptyList<String>(), preview.unknownFields)
    }

    @Test
    fun appGestureShortcutsAreRecognizedAsGestureBackupSection() {
        val preview = BackupImportPreview.fromFields(
            mapOf(
                "schema" to 1,
                "app_gesture_shortcuts" to "{}",
            ),
        )

        assertTrue(preview.canImport)
        assertEquals(listOf("Gestures"), preview.sections)
        assertEquals(emptyList<String>(), preview.unknownFields)
    }

    @Test
    fun shortcutShelfIsRecognizedAsLayoutBackupSection() {
        val preview = BackupImportPreview.fromFields(
            mapOf(
                "schema" to 1,
                "shortcut_shelf" to "S:com.example.mail:compose:Compose:com.example.mail/.Main",
            ),
        )

        assertTrue(preview.canImport)
        assertEquals(listOf("Layout & widgets"), preview.sections)
        assertEquals(emptyList<String>(), preview.unknownFields)
    }

    @Test
    fun customGestureRecorderFieldsAreRecognizedAsGestureBackupSection() {
        val preview = BackupImportPreview.fromFields(
            mapOf(
                "schema" to 1,
                "custom_gesture_pattern" to "02",
                "custom_gesture_action" to "SETTINGS",
                "gesture_app_custom" to "com.example/.Main",
            ),
        )

        assertTrue(preview.canImport)
        assertEquals(listOf("Gestures"), preview.sections)
        assertEquals(emptyList<String>(), preview.unknownFields)
    }

    @Test
    fun assistantAppIsRecognizedAsGestureBackupSection() {
        val preview = BackupImportPreview.fromFields(
            mapOf(
                "schema" to 1,
                "assistant_app" to "com.example.assistant/.Main",
            ),
        )

        assertTrue(preview.canImport)
        assertEquals(listOf("Gestures"), preview.sections)
        assertEquals(emptyList<String>(), preview.unknownFields)
    }

    @Test
    fun migrationMetadataIsRecognizedWithoutUnknownFields() {
        val preview = BackupImportPreview.fromFields(
            mapOf(
                "schema" to 1,
                "migration_source" to "Nova Launcher",
                "migration_unsupported" to listOf("Widgets require re-adding after migration"),
                "home_grid" to "A:com.example/.Main",
            ),
        )

        assertTrue(preview.canImport)
        assertEquals("Nova Launcher", preview.migrationSource)
        assertEquals(listOf("Widgets require re-adding after migration"), preview.migrationUnsupported)
        assertEquals("Migration skipped unsupported items", preview.warning)
        assertEquals(listOf("Layout & widgets"), preview.sections)
        assertEquals(emptyList<String>(), preview.unknownFields)
    }

    @Test
    fun invalidJsonIsRejected() {
        val preview = BackupImportPreview.fromJson("{")

        assertFalse(preview.canImport)
        assertEquals("Invalid backup JSON", preview.error)
    }
}
