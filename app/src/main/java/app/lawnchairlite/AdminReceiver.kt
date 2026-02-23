package app.lawnchairlite

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

/** Lawnchair Lite v2.1.0 - Device Admin for lockNow() */
class AdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {}
    override fun onDisabled(context: Context, intent: Intent) {}
}
