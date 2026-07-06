package app.lawnchairlite

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.lawnchairlite.data.BackupScheduler
import app.lawnchairlite.data.LauncherPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BackupScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val prefs = LauncherPrefs(context.applicationContext)
                val state = prefs.backupScheduleState.first()
                when (intent.action) {
                    Intent.ACTION_BOOT_COMPLETED,
                    Intent.ACTION_MY_PACKAGE_REPLACED -> if (state.enabled) BackupScheduler.schedule(context)
                    ACTION_RUN_SCHEDULED_BACKUP -> if (state.enabled) {
                        BackupScheduler.runBackupNow(context)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_RUN_SCHEDULED_BACKUP = "app.lawnchairlite.action.RUN_SCHEDULED_BACKUP"
    }
}
