package app.lawnchairlite.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import app.lawnchairlite.BackupScheduleReceiver
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ScheduledBackupResult(
    val success: Boolean,
    val path: String = "",
    val error: String = "",
)

object BackupScheduler {
    private const val TAG = "BackupScheduler"
    private const val REQUEST_CODE = 2727
    private const val MAX_BACKUP_FILES = 8
    const val INTERVAL_MS: Long = 7L * 24L * 60L * 60L * 1000L

    fun schedule(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        alarmManager.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + INTERVAL_MS,
            INTERVAL_MS,
            pendingIntent(context),
        )
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        alarmManager.cancel(pendingIntent(context))
    }

    suspend fun runBackupNow(context: Context): ScheduledBackupResult = runCatching {
        val prefs = LauncherPrefs(context.applicationContext)
        val json = prefs.exportBackup(BackupExportOptions())
        val dir = context.getExternalFilesDir("backups") ?: File(context.filesDir, "backups")
        if (!dir.exists() && !dir.mkdirs()) error("Unable to create ${dir.absolutePath}")
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val file = File(dir, "lawnchair-lite-auto-$timestamp.json")
        file.writeText(json, Charsets.UTF_8)
        pruneOldBackups(dir)
        prefs.markScheduledBackupSuccess(file.absolutePath)
        ScheduledBackupResult(success = true, path = file.absolutePath)
    }.getOrElse { error ->
        Log.e(TAG, "Scheduled backup failed", error)
        ScheduledBackupResult(success = false, error = error.message.orEmpty())
    }

    private fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, BackupScheduleReceiver::class.java).setAction(BackupScheduleReceiver.ACTION_RUN_SCHEDULED_BACKUP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun pruneOldBackups(dir: File) {
        dir.listFiles { file -> file.isFile && file.name.startsWith("lawnchair-lite-auto-") && file.extension == "json" }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(MAX_BACKUP_FILES)
            ?.forEach { file -> runCatching { file.delete() } }
    }
}
