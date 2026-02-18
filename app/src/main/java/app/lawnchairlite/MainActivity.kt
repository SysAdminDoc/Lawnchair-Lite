package app.lawnchairlite

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
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
 * Lawnchair Lite v0.9.0
 */
class MainActivity : ComponentActivity() {

    private var pkgReceiver: BroadcastReceiver? = null
    private var vmRef: LauncherViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                vmRef?.let { if (it.hasOpenOverlay()) it.closeAllOverlays() }
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

    override fun onNewIntent(intent: Intent?) { super.onNewIntent(intent); vmRef?.closeAllOverlays() }

    private fun registerPkgReceiver(vm: LauncherViewModel) {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED); addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        pkgReceiver = object : BroadcastReceiver() { override fun onReceive(c: Context?, i: Intent?) { vm.loadApps() } }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) ContextCompat.registerReceiver(this, pkgReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
        else registerReceiver(pkgReceiver, filter)
    }

    private fun unregisterPkgReceiver() { pkgReceiver?.let { runCatching { unregisterReceiver(it) }; pkgReceiver = null } }
}
