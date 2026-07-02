package app.lawnchairlite.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherBackupServiceTest {
    @Test
    fun delegatesExportAndPreview() = runBlocking {
        val gateway = FakeBackupGateway()
        val service = LauncherBackupService(gateway)
        val options = BackupExportOptions(includeSearchHistory = true)
        val previewJson = """{"schema":1,"theme":"MIDNIGHT"}"""

        assertEquals("{}", service.export(options))
        assertEquals(listOf("Appearance"), service.preview(previewJson).sections)
        assertEquals(options, gateway.lastExportOptions)
        assertEquals(previewJson, gateway.lastPreviewJson)
    }

    @Test
    fun successfulImportReturnsRestoredSettings() = runBlocking {
        val gateway = FakeBackupGateway(importResult = true)
        val restored = LauncherSettings(iconPack = "example.pack")
        gateway.settingsFlow.value = restored

        val result = LauncherBackupService(gateway).importAndLoadRestoredSettings("{}")

        assertTrue(result.imported)
        assertEquals(restored, result.restoredSettings)
        assertEquals("{}", gateway.lastImportJson)
    }

    @Test
    fun failedImportDoesNotReadRestoredSettings() = runBlocking {
        val gateway = FakeBackupGateway(importResult = false)

        val result = LauncherBackupService(gateway).importAndLoadRestoredSettings("{}")

        assertFalse(result.imported)
        assertEquals(null, result.restoredSettings)
    }

    private class FakeBackupGateway(
        private val importResult: Boolean = true,
    ) : LauncherBackupGateway {
        val settingsFlow = MutableStateFlow(LauncherSettings())
        var lastExportOptions: BackupExportOptions? = null
        var lastPreviewJson: String? = null
        var lastImportJson: String? = null

        override val settings: Flow<LauncherSettings> = settingsFlow

        override suspend fun exportBackup(options: BackupExportOptions): String {
            lastExportOptions = options
            return "{}"
        }

        override fun previewBackup(json: String): BackupImportPreview {
            lastPreviewJson = json
            return BackupImportPreview.fromFields(mapOf("schema" to 1, "theme" to "MIDNIGHT"))
        }

        override suspend fun importBackup(json: String): Boolean {
            lastImportJson = json
            return importResult
        }
    }
}
