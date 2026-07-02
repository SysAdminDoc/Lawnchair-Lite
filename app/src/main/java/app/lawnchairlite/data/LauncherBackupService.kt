package app.lawnchairlite.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

interface LauncherBackupGateway {
    val settings: Flow<LauncherSettings>
    suspend fun exportBackup(options: BackupExportOptions): String
    fun previewBackup(json: String): BackupImportPreview
    suspend fun importBackup(json: String): Boolean
}

class LauncherPrefsBackupGateway(private val prefs: LauncherPrefs) : LauncherBackupGateway {
    override val settings: Flow<LauncherSettings> = prefs.settings

    override suspend fun exportBackup(options: BackupExportOptions): String =
        prefs.exportBackup(options)

    override fun previewBackup(json: String): BackupImportPreview =
        prefs.previewBackup(json)

    override suspend fun importBackup(json: String): Boolean =
        prefs.importBackup(json)
}

data class BackupRestoreResult(
    val imported: Boolean,
    val restoredSettings: LauncherSettings? = null,
)

class LauncherBackupService(private val gateway: LauncherBackupGateway) {
    suspend fun export(options: BackupExportOptions = BackupExportOptions()): String =
        gateway.exportBackup(options)

    fun preview(json: String): BackupImportPreview =
        gateway.previewBackup(json)

    suspend fun importAndLoadRestoredSettings(json: String): BackupRestoreResult {
        if (!gateway.importBackup(json)) return BackupRestoreResult(imported = false)
        return BackupRestoreResult(
            imported = true,
            restoredSettings = runCatching { gateway.settings.first() }.getOrNull(),
        )
    }
}
