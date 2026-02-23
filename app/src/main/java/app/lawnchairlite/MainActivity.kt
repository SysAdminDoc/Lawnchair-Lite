package app.lawnchairlite

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import app.lawnchairlite.ui.HomeScreen
import app.lawnchairlite.ui.LauncherTheme

/**
 * Lawnchair Lite v2.1.0
 *
 * Stability improvements:
 * - Debounced package receiver: bulk install/uninstall events coalesced
 * - Package events differentiate between install, remove, and update
 * - onNewIntent properly guarded
 * - configChanges in manifest prevents recreation on common config changes
 *   (keyboard, orientation, screenSize, screenLayout, smallestScreenSize)
 */
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private var pkgReceiver: BroadcastReceiver? = null
    private var vmRef: LauncherViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                vmRef?.let {
                    if (it.editMode.value) it.exitEditMode()
                    else if (it.hasOpenOverlay()) it.closeAllOverlays()
                }
            }
        })

        setContent {
            val vm: LauncherViewModel = viewModel()
            LaunchedEffect(vm) { vmRef = vm }
            val settings by vm.settings.collectAsState()
            LauncherTheme(themeMode = settings.themeMode) { HomeScreen(vm = vm) }
            DisposableEffect(Unit) { registerPkgReceiver(vm); onDispose { unregisterPkgReceiver() } }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        try { vmRef?.closeAllOverlays() } catch (e: Exception) {
            Log.w(TAG, "onNewIntent overlay close failed", e)
        }
    }

    /**
     * Package change receiver with event differentiation:
     * - PACKAGE_ADDED (not replacing): triggers onAppInstalled for auto-placement
     * - PACKAGE_REMOVED, CHANGED, REPLACED: triggers debounced reload
     *
     * Debouncing prevents N consecutive reloads when the system processes
     * multiple package events in rapid succession (e.g. system updates).
     */
    private fun registerPkgReceiver(vm: LauncherViewModel) {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        pkgReceiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                try {
                    val pkg = i?.data?.schemeSpecificPart ?: return
                    when (i.action) {
                        Intent.ACTION_PACKAGE_ADDED -> {
                            val replacing = i.getBooleanExtra(Intent.EXTRA_REPLACING, false)
                            if (!replacing) vm.onAppInstalled(pkg) else vm.debouncedReload()
                        }
                        else -> vm.debouncedReload()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Package receiver error", e)
                    // Fallback: just reload
                    try { vm.debouncedReload() } catch (_: Exception) {}
                }
            }
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.registerReceiver(this, pkgReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
            } else {
                registerReceiver(pkgReceiver, filter)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register package receiver", e)
        }
    }

    private fun unregisterPkgReceiver() {
        pkgReceiver?.let {
            try { unregisterReceiver(it) } catch (_: Exception) {}
            pkgReceiver = null
        }
    }
}
