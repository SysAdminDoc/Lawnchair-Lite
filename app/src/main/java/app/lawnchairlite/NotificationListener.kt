package app.lawnchairlite

import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Lawnchair Lite v2.12.0 - Notification Listener
 *
 * Tracks active notification counts per package for badge dots.
 * User must grant notification access in system settings.
 * The service communicates via companion StateFlow (no binding needed).
 *
 * Stability: rebuildCounts is debounced (150ms) to prevent excessive
 * StateFlow emissions during rapid notification bursts (e.g. messaging apps).
 */
class NotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "NotifListener"
        private const val REBUILD_DEBOUNCE_MS = 150L
        private val _counts = MutableStateFlow<Map<String, Int>>(emptyMap())
        val counts: StateFlow<Map<String, Int>> = _counts.asStateFlow()
        private val _connected = MutableStateFlow(false)
        val connected: StateFlow<Boolean> = _connected.asStateFlow()
    }

    private val handler = Handler(Looper.getMainLooper())
    private val rebuildRunnable = Runnable { rebuildCounts() }

    override fun onListenerConnected() {
        super.onListenerConnected()
        _connected.value = true
        rebuildCounts() // immediate on connect to populate initial state
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        _connected.value = false
        handler.removeCallbacks(rebuildRunnable)
        _counts.value = emptyMap()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        debouncedRebuild()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        debouncedRebuild()
    }

    private fun debouncedRebuild() {
        handler.removeCallbacks(rebuildRunnable)
        handler.postDelayed(rebuildRunnable, REBUILD_DEBOUNCE_MS)
    }

    private fun rebuildCounts() {
        try {
            val active = activeNotifications ?: return
            val map = mutableMapOf<String, Int>()
            for (sbn in active) {
                if (sbn.isOngoing) continue
                val pkg = sbn.packageName ?: continue
                map[pkg] = (map[pkg] ?: 0) + 1
            }
            _counts.value = map
        } catch (e: Exception) {
            Log.w(TAG, "rebuildCounts failed", e)
        }
    }
}
