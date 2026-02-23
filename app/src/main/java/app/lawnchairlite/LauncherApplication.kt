package app.lawnchairlite

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * Lawnchair Lite v2.1.0 - Application
 *
 * Stability: Global crash handler with notification-based reporting.
 * Modeled after Lawnchair's LawnchairApp crash infrastructure.
 */
class LauncherApplication : Application() {

    companion object {
        private const val TAG = "LawnchairLite"
        private const val CRASH_CHANNEL = "crash_reports"
        private const val CRASH_NOTIF_ID = 9001
    }

    private var defaultHandler: Thread.UncaughtExceptionHandler? = null

    override fun onCreate() {
        super.onCreate()
        setupCrashHandler()
    }

    /**
     * Installs a global UncaughtExceptionHandler that:
     * 1. Captures the full stack trace
     * 2. Formats a structured bug report (version, device info, trace)
     * 3. Posts a notification so the user can copy/share it
     * 4. Delegates to the default handler (Android restarts the launcher)
     *
     * Because this is a HOME intent handler, Android auto-restarts us after crash.
     * All workspace state lives in DataStore, so restart recovery is transparent.
     */
    private fun setupCrashHandler() {
        defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val report = buildCrashReport(thread, throwable)
                Log.e(TAG, report)
                postCrashNotification(report)
            } catch (_: Exception) {
                // If crash reporting itself fails, don't recurse
            }
            // Delegate to default handler (triggers Android's launcher restart)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun buildCrashReport(thread: Thread, throwable: Throwable): String {
        val sb = StringBuilder()
        sb.appendLine("=== Lawnchair Lite Crash Report ===")
        sb.appendLine("Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        sb.appendLine("Build: ${if (BuildConfig.DEBUG) "DEBUG" else "RELEASE"}")
        sb.appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        sb.appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        sb.appendLine("Fingerprint: ${Build.FINGERPRINT}")
        sb.appendLine("Thread: ${thread.name}")
        sb.appendLine("Time: ${System.currentTimeMillis()}")
        sb.appendLine("---")
        sb.appendLine(Log.getStackTraceString(throwable))
        return sb.toString()
    }

    private fun postCrashNotification(report: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CRASH_CHANNEL, "Crash Reports", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Notifications when the launcher crashes"
                }
            )
        }

        // Copy-to-clipboard intent
        val copyIntent = Intent(this, CrashCopyReceiver::class.java).apply {
            putExtra("report", report)
        }
        val copyPending = PendingIntent.getBroadcast(
            this, 0, copyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(this, CRASH_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Lawnchair Lite crashed")
            .setContentText("Tap 'Copy Report' to copy the crash log")
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                report.take(500) + if (report.length > 500) "\n..." else ""
            ))
            .addAction(android.R.drawable.ic_menu_save, "Copy Report", copyPending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        runCatching { nm.notify(CRASH_NOTIF_ID, notif) }
    }
}
