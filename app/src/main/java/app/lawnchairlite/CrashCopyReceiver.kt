package app.lawnchairlite

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast

/**
 * Lawnchair Lite - Crash Report Clipboard Receiver
 *
 * Handles the "Copy Report" action from crash notifications.
 */
class CrashCopyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val report = intent?.getStringExtra("report") ?: return
        val ctx = context ?: return
        runCatching {
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText(ctx.getString(R.string.crash_report), report))
            Toast.makeText(ctx, ctx.getString(R.string.crash_report_copied), Toast.LENGTH_SHORT).show()
        }
    }
}
