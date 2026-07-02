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
    fun invalidJsonIsRejected() {
        val preview = BackupImportPreview.fromJson("{")

        assertFalse(preview.canImport)
        assertEquals("Invalid backup JSON", preview.error)
    }
}
