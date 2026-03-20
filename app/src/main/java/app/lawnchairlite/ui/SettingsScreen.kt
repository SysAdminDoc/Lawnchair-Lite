package app.lawnchairlite.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lawnchairlite.LauncherViewModel
import app.lawnchairlite.data.*
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import kotlinx.coroutines.launch

/**
 * Lawnchair Lite v2.8.0 - Settings
 */
@Composable
fun SettingsPanel(
    visible: Boolean, settings: LauncherSettings,
    vm: LauncherViewModel, onClose: () -> Unit,
) {
    val colors = LocalLauncherColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val hiddenApps by vm.hiddenApps.collectAsState()
    val allAppsRaw by vm.allApps.collectAsState()
    val availablePacks by vm.availablePacks.collectAsState()
    val iconPackLoading by vm.iconPackLoading.collectAsState()

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch { val json = vm.exportBackup(); runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) } } }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch { val json = runCatching { context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() }.getOrNull() ?: return@launch; vm.importBackup(json) }
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(350, easing = FastOutSlowInEasing)) + fadeIn(tween(200)),
        exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(300)) + fadeOut(tween(150)),
    ) {
        Column(Modifier.fillMaxSize().background(colors.background.copy(alpha = 0.97f)).statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = colors.textSecondary, modifier = Modifier.size(22.dp)) }
                Spacer(Modifier.weight(1f)); Text("Settings", color = colors.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f)); Spacer(Modifier.size(48.dp))
            }
            Divider(color = colors.border, thickness = 0.5.dp)

            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding()) {

                // Theme
                Lbl("Theme", colors)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ThemeMode.entries.forEach { mode ->
                        val tc = themeColors(mode); val sel = settings.themeMode == mode
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                                .background(if (sel) tc.accent.copy(alpha = 0.12f) else colors.card)
                                .border(if (sel) 2.dp else 0.5.dp, if (sel) tc.accent else colors.border, RoundedCornerShape(12.dp))
                                .clickable { vm.setTheme(mode) }.padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                listOf(tc.accent, tc.card, tc.text).forEach { Box(Modifier.size(10.dp).clip(CircleShape).background(it)) }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(mode.label, color = if (sel) tc.accent else colors.textSecondary, fontSize = 10.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }

                // Wallpaper
                Lbl("Wallpaper", colors)
                ActionBtn("Change Wallpaper", "System Picker", colors) { vm.openWallpaperPicker() }
                Spacer(Modifier.height(10.dp))
                Text("Wallpaper Dim: ${settings.wallpaperDim}%", color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                Slider(
                    value = settings.wallpaperDim.toFloat(),
                    onValueChange = { vm.setWallpaperDim(it.toInt()) },
                    valueRange = 0f..80f,
                    steps = 15,
                    colors = SliderDefaults.colors(
                        thumbColor = colors.accent,
                        activeTrackColor = colors.accent,
                        inactiveTrackColor = colors.card,
                    ),
                )

                // Icon
                Lbl("Icon Shape", colors)
                Chips(IconShape.entries.map { it to it.label }, settings.iconShape, colors) { vm.setShape(it) }
                Spacer(Modifier.height(10.dp))
                Lbl("Icon Size", colors)
                Chips(IconSize.entries.map { it to it.label }, settings.iconSize, colors) { vm.setIconSize(it) }

                // Icon Pack
                Lbl("Icon Pack", colors)
                IconPackSection(settings, availablePacks, iconPackLoading, colors, vm)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    Spacer(Modifier.height(6.dp))
                    Tog("Themed Icons (Android 13+)", settings.themedIcons, colors) { vm.setThemedIcons(it) }
                }

                // Labels
                Lbl("Icon Labels", colors)
                Chips(LabelStyle.entries.map { it to it.label }, settings.labelStyle, colors) { vm.setLabelStyle(it) }

                // Drawer Sort
                Lbl("Drawer Sort", colors)
                Chips(DrawerSort.entries.map { it to it.label }, settings.drawerSort, colors) { vm.setDrawerSort(it) }

                // Grid
                Lbl("Grid Columns", colors)
                Chips((3..8).map { it to it.toString() }, settings.gridColumns, colors) { vm.setGridCols(it) }
                Spacer(Modifier.height(10.dp))
                Lbl("Grid Rows", colors)
                Chips((3..10).map { it to it.toString() }, settings.gridRows, colors) { vm.setGridRows(it) }
                Spacer(Modifier.height(10.dp))
                Lbl("Dock Icons", colors)
                Chips((3..7).map { it to it.toString() }, settings.dockCount, colors) { vm.setDockCount(it) }

                // Grid Padding
                Lbl("Grid Padding", colors)
                Text("Horizontal: ${settings.gridPaddingH}dp", color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Slider(
                    value = settings.gridPaddingH.toFloat(),
                    onValueChange = { vm.setGridPaddingH(it.toInt()) },
                    valueRange = 0f..24f, steps = 23,
                    colors = SliderDefaults.colors(thumbColor = colors.accent, activeTrackColor = colors.accent, inactiveTrackColor = colors.card),
                )
                Text("Vertical: ${settings.gridPaddingV}dp", color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Slider(
                    value = settings.gridPaddingV.toFloat(),
                    onValueChange = { vm.setGridPaddingV(it.toInt()) },
                    valueRange = 0f..24f, steps = 23,
                    colors = SliderDefaults.colors(thumbColor = colors.accent, activeTrackColor = colors.accent, inactiveTrackColor = colors.card),
                )

                // Drawer Columns
                Lbl("Drawer Columns", colors)
                Chips((0..6).map { it to if (it == 0) "Auto" else it.toString() }, settings.drawerColumns, colors) { vm.setDrawerColumns(it) }

                // Page Transition
                Lbl("Page Transition", colors)
                Chips(PageTransition.entries.map { it to it.label }, settings.pageTransition, colors) { vm.setPageTransition(it) }

                // Badge Style
                Lbl("Badge Style", colors)
                Chips(BadgeStyle.entries.map { it to it.label }, settings.badgeStyle, colors) { vm.setBadgeStyle(it) }

                // Accent Color Override
                Lbl("Accent Color", colors)
                var accentInput by remember { mutableStateOf(settings.accentOverride) }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (settings.accentOverride.isNotBlank()) {
                        val parsed = try { Color(android.graphics.Color.parseColor(settings.accentOverride)) } catch (_: Exception) { colors.accent }
                        Box(Modifier.size(28.dp).clip(CircleShape).background(parsed).border(1.dp, colors.border, CircleShape))
                        Spacer(Modifier.width(10.dp))
                    }
                    TextField(
                        value = accentInput, onValueChange = { accentInput = it.take(7) },
                        modifier = Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(10.dp)),
                        placeholder = { Text("#FF5722", color = colors.textSecondary, fontSize = 13.sp) },
                        colors = TextFieldDefaults.colors(focusedTextColor = colors.text, unfocusedTextColor = colors.text, cursorColor = colors.accent, focusedContainerColor = colors.card, unfocusedContainerColor = colors.card, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                        singleLine = true, textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Apply", color = colors.accent, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(colors.accent.copy(alpha = 0.12f))
                            .clickable { vm.setAccentOverride(accentInput.trim()) }.padding(horizontal = 10.dp, vertical = 6.dp))
                    if (settings.accentOverride.isNotBlank()) {
                        Spacer(Modifier.width(6.dp))
                        Text("Reset", color = Color(0xFFEF5350), fontSize = 12.sp, fontWeight = FontWeight.Medium,
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFFEF5350).copy(alpha = 0.1f))
                                .clickable { accentInput = ""; vm.setAccentOverride("") }.padding(horizontal = 10.dp, vertical = 6.dp))
                    }
                }

                // Features
                Lbl("Features", colors)
                Tog("At-a-Glance Clock", settings.showClock, colors) { vm.setShowClock(it) }
                Tog("Dock Search Bar", settings.showDockSearch, colors) { vm.setShowDockSearch(it) }
                Tog("Auto-Place New Apps", settings.autoPlaceNew, colors) { vm.setAutoPlaceNew(it) }
                Tog("Notification Badges", settings.showNotifBadges, colors) { vm.setShowNotifBadges(it) }
                Tog("Hide Status Bar", settings.hideStatusBar, colors) { vm.setHideStatusBar(it) }
                Tog("Icon Shadow", settings.iconShadow, colors) { vm.setIconShadow(it) }
                Tog("Lock Home Screen", settings.homeLocked, colors) { vm.setHomeLocked(it) }
                Tog("Drawer Categories", settings.drawerCategories, colors) { vm.setDrawerCategories(it) }
                Tog("Drawer Section Headers", settings.drawerSectionHeaders, colors) { vm.setDrawerSectionHeaders(it) }
                Tog("Wallpaper Parallax", settings.wallpaperParallax, colors) { vm.setWallpaperParallax(it) }
                Tog("Drawer Animation", settings.drawerAnimation, colors) { vm.setDrawerAnimation(it) }

                // Dock Style
                Lbl("Dock Style", colors)
                Chips(DockStyle.entries.map { it to it.label }, settings.dockStyle, colors) { vm.setDockStyle(it) }

                // Search Bar Style
                Lbl("Search Bar", colors)
                Chips(SearchBarStyle.entries.map { it to it.label }, settings.searchBarStyle, colors) { vm.setSearchBarStyle(it) }

                // Haptic Feedback
                Lbl("Haptic Feedback", colors)
                Chips(HapticLevel.entries.map { it to it.label }, settings.hapticLevel, colors) { vm.setHapticLevel(it) }

                // Label Size
                Lbl("Label Size", colors)
                Chips(LabelSize.entries.map { it to it.label }, settings.labelSize, colors) { vm.setLabelSize(it) }

                // Folder Columns
                Lbl("Folder Columns", colors)
                Chips((3..5).map { it to it.toString() }, settings.folderColumns, colors) { vm.setFolderColumns(it) }

                // Drawer Opacity
                Lbl("Drawer Background", colors)
                Text("Opacity: ${settings.drawerOpacity}%", color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Slider(
                    value = settings.drawerOpacity.toFloat(),
                    onValueChange = { vm.setDrawerOpacity(it.toInt()) },
                    valueRange = 50f..100f, steps = 49,
                    colors = SliderDefaults.colors(thumbColor = colors.accent, activeTrackColor = colors.accent, inactiveTrackColor = colors.card),
                )
                if (settings.showNotifBadges) {
                    val notifConnected = remember { mutableStateOf(vm.isNotificationAccessGranted()) }
                    if (!notifConnected.value) {
                        Spacer(Modifier.height(4.dp))
                        ActionBtn("Grant Notification Access", "Required for badges", colors) {
                            vm.openNotificationAccess()
                            notifConnected.value = vm.isNotificationAccessGranted()
                        }
                    } else {
                        Spacer(Modifier.height(4.dp))
                        Text("Notification Access: Granted", color = colors.accent, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                    Spacer(Modifier.height(4.dp))
                }

                // Gestures
                Lbl("Gestures", colors)
                GesturePicker("Double-Tap", settings.doubleTapAction, colors) { vm.setDoubleTapAction(it) }
                GesturePicker("Swipe Down", settings.swipeDownAction, colors) { vm.setSwipeDownAction(it) }
                GesturePicker("Triple-Tap", settings.tripleTapAction, colors) { vm.setTripleTapAction(it) }
                GesturePicker("Pinch In", settings.pinchAction, colors) { vm.setPinchAction(it) }
                GesturePicker("Dock Handle Tap", settings.dockTapAction, colors) { vm.setDockTapAction(it) }

                val adminEnabled = remember { mutableStateOf(vm.isDeviceAdminEnabled()) }
                if (listOf(settings.doubleTapAction, settings.swipeDownAction, settings.tripleTapAction, settings.pinchAction, settings.dockTapAction).any { it == GestureAction.LOCK_SCREEN }) {
                    Spacer(Modifier.height(6.dp))
                    if (!adminEnabled.value) {
                        ActionBtn("Enable Lock Screen", "Requires Device Admin", colors) { vm.requestDeviceAdmin(); adminEnabled.value = vm.isDeviceAdminEnabled() }
                    } else {
                        Text("Device Admin: Enabled", color = colors.accent, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }

                // Quick actions
                Lbl("Quick Actions", colors)
                ActionBtn("Kill Background Apps", "Free memory", colors) { vm.killBackgroundApps() }

                // Backup
                Lbl("Backup & Restore", colors)
                ActionBtn("Export Layout", "Save to file", colors) { exportLauncher.launch("lawnchair-lite-backup.json") }
                Spacer(Modifier.height(8.dp))
                ActionBtn("Restore Layout", "Load from file", colors) { importLauncher.launch(arrayOf("application/json", "*/*")) }

                // Hidden apps
                Lbl("Hidden Apps", colors)
                val hiddenInfos = remember(hiddenApps, allAppsRaw) { allAppsRaw.filter { it.key in hiddenApps } }
                if (hiddenInfos.isEmpty()) Text("No hidden apps", color = colors.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp))
                else {
                    hiddenInfos.forEach { app ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(colors.card).clickable { vm.unhideApp(app.key) }.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (app.icon != null) {
                                Box(Modifier.size(36.dp).clip(iconClip(settings.iconShape)).background(colors.surface), Alignment.Center) {
                                    Image(rememberDrawablePainter(app.icon), null, Modifier.fillMaxSize().padding(3.dp))
                                }; Spacer(Modifier.width(10.dp))
                            }
                            Column(Modifier.weight(1f)) { Text(app.label, color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.Medium); Text(app.packageName, color = colors.textSecondary, fontSize = 10.sp) }
                            Icon(Icons.Default.Visibility, "Unhide", tint = colors.accent, modifier = Modifier.size(18.dp))
                        }; Spacer(Modifier.height(6.dp))
                    }
                    Text("Tap to unhide", color = colors.textSecondary, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp))
                }

                Lbl("About", colors)
                Text("Lawnchair Lite v${app.lawnchairlite.BuildConfig.VERSION_NAME}", color = colors.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text("Lightweight launcher inspired by Lawnchair/Pixel Launcher.", color = colors.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp, bottom = 32.dp))
            }
        }
    }
}

// ── Icon Pack Picker ─────────────────────────────────────────────────

@Composable
private fun IconPackSection(
    settings: LauncherSettings, packs: List<IconPackInfo>, loading: Boolean,
    c: LauncherColors, vm: LauncherViewModel,
) {
    var expanded by remember { mutableStateOf(false) }
    val activePack = settings.iconPack
    val activeLabel = if (activePack.isBlank()) "System Default" else packs.find { it.packageName == activePack }?.label ?: activePack.substringAfterLast(".")

    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.card)
            .border(0.5.dp, if (activePack.isNotBlank()) c.accent.copy(alpha = 0.4f) else c.border, RoundedCornerShape(12.dp))
            .clickable {
                if (packs.isEmpty()) vm.refreshIconPacks()
                expanded = !expanded
            }.padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = c.accent)
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Text("Active Pack", color = c.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(activeLabel, color = if (activePack.isNotBlank()) c.accent else c.textSecondary, fontSize = 12.sp)
        }
        if (activePack.isNotBlank()) {
            Text("Reset", color = Color(0xFFEF5350), fontSize = 12.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFFEF5350).copy(alpha = 0.1f))
                    .clickable { vm.clearIconPack(); expanded = false }.padding(horizontal = 10.dp, vertical = 4.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(if (expanded) "Close" else "Browse", color = c.accent, fontSize = 12.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(c.accent.copy(alpha = 0.1f))
                .clickable {
                    if (packs.isEmpty()) vm.refreshIconPacks()
                    expanded = !expanded
                }.padding(horizontal = 10.dp, vertical = 4.dp))
    }

    AnimatedVisibility(expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        Column(Modifier.padding(top = 8.dp)) {
            if (packs.isEmpty()) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.card)
                        .border(0.5.dp, c.border, RoundedCornerShape(12.dp)).padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("No icon packs found", color = c.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(4.dp))
                    Text("Install icon packs from Play Store", color = c.textSecondary, fontSize = 11.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("Refresh", color = c.accent, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(c.accent.copy(alpha = 0.12f))
                            .clickable { vm.refreshIconPacks() }.padding(horizontal = 14.dp, vertical = 6.dp))
                }
            } else {
                packs.forEach { pack ->
                    val isActive = pack.packageName == activePack
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .background(if (isActive) c.accent.copy(alpha = 0.1f) else c.card)
                            .border(0.5.dp, if (isActive) c.accent.copy(alpha = 0.4f) else c.border, RoundedCornerShape(10.dp))
                            .clickable { vm.setIconPack(pack.packageName); expanded = false }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (pack.icon != null) {
                            Box(Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(c.surface), Alignment.Center) {
                                Image(rememberDrawablePainter(pack.icon), pack.label, Modifier.fillMaxSize().padding(3.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(pack.label, color = if (isActive) c.accent else c.text, fontSize = 13.sp, fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium)
                            Text(pack.packageName, color = c.textSecondary, fontSize = 10.sp, maxLines = 1)
                        }
                        if (isActive) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(c.accent))
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
    }
}

// ── Helpers ──────────────────────────────────────────────────────────

@Composable private fun Lbl(t: String, c: LauncherColors) { Text(t.uppercase(), color = c.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.padding(top = 22.dp, bottom = 10.dp)) }

@Composable private fun <T> Chips(opts: List<Pair<T, String>>, sel: T, c: LauncherColors, onSel: (T) -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        opts.forEach { (v, l) -> val s = v == sel
            Box(Modifier.clip(RoundedCornerShape(10.dp)).background(if (s) c.accent.copy(alpha = 0.15f) else c.card)
                .border(0.5.dp, if (s) c.accent.copy(alpha = 0.4f) else c.border, RoundedCornerShape(10.dp))
                .clickable { onSel(v) }.padding(horizontal = 16.dp, vertical = 8.dp))
            { Text(l, color = if (s) c.accent else c.textSecondary, fontSize = 13.sp, fontWeight = if (s) FontWeight.Bold else FontWeight.Normal) }
        }
    }
}

@Composable private fun Tog(label: String, value: Boolean, c: LauncherColors, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!value) }.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Switch(checked = value, onCheckedChange = { onChange(it) }, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = c.accent, uncheckedThumbColor = c.textSecondary, uncheckedTrackColor = c.card, uncheckedBorderColor = c.border))
    }
    Divider(color = c.border.copy(alpha = 0.3f), thickness = 0.5.dp)
}

@Composable private fun ActionBtn(label: String, sub: String, c: LauncherColors, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.card).border(0.5.dp, c.border, RoundedCornerShape(12.dp)).clickable { onClick() }.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.Medium); Spacer(Modifier.weight(1f)); Text(sub, color = c.accent, fontSize = 12.sp)
    }
}

@Composable private fun GesturePicker(label: String, current: GestureAction, c: LauncherColors, onChange: (GestureAction) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(c.card).border(0.5.dp, c.border, RoundedCornerShape(10.dp)).clickable { expanded = !expanded }.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.Medium); Spacer(Modifier.weight(1f)); Text(current.label, color = c.accent, fontSize = 13.sp)
        }
        AnimatedVisibility(expanded) {
            Column(Modifier.padding(start = 8.dp, top = 4.dp)) {
                GestureAction.entries.forEach { action -> val sel = action == current
                    Text(action.label, color = if (sel) c.accent else c.text, fontSize = 13.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(if (sel) c.accent.copy(alpha = 0.1f) else Color.Transparent)
                            .clickable { onChange(action); expanded = false }.padding(horizontal = 12.dp, vertical = 10.dp))
                }
            }
        }
    }
}
