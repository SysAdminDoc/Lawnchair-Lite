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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
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
        scope.launch { val json = runCatching { context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() }.getOrNull(); if (json == null) { android.widget.Toast.makeText(context, "Import failed — invalid file", android.widget.Toast.LENGTH_SHORT).show(); return@launch }; vm.importBackup(json) }
    }

    // Settings search
    var settingsSearch by remember { mutableStateOf("") }
    val sq = settingsSearch.lowercase()
    // Keywords per section for search matching
    val themeKeywords = "theme wallpaper dim accent color midnight glass oled mocha aurora neon"
    val iconsKeywords = "icon shape size pack themed shadow grayscale label weight squircle circle square teardrop hexagon diamond"
    val gridKeywords = "grid columns rows padding page transition indicator badge folder cube stack fade depth carousel slide dots line"
    val drawerKeywords = "drawer sort columns opacity categories section headers animation suggestions search engine"
    val dockKeywords = "dock icons style search bar pill floating transparent hide"
    val gesturesKeywords = "gesture double tap swipe down swipe up triple pinch dock lock screen notification flashlight edit mode recent app launch"
    val featuresKeywords = "clock at a glance auto place notification badges status bar home lock parallax haptic feedback"
    val advancedKeywords = "kill background apps clear search history reset settings backup restore export import hidden apps about"
    fun sectionMatches(keywords: String): Boolean = sq.isBlank() || keywords.contains(sq) || sq.split(" ").all { w -> keywords.contains(w) }
    val showTheme = sectionMatches(themeKeywords)
    val showIcons = sectionMatches(iconsKeywords)
    val showGrid = sectionMatches(gridKeywords)
    val showDrawer = sectionMatches(drawerKeywords)
    val showDock = sectionMatches(dockKeywords)
    val showGestures = sectionMatches(gesturesKeywords)
    val showFeatures = sectionMatches(featuresKeywords)
    val showAdvanced = sectionMatches(advancedKeywords)
    // Auto-expand matching sections when searching
    val searching = sq.isNotBlank()

    // Section expanded state
    var themeExpanded by remember { mutableStateOf(true) }
    var iconsExpanded by remember { mutableStateOf(false) }
    var gridExpanded by remember { mutableStateOf(false) }
    var drawerExpanded by remember { mutableStateOf(false) }
    var dockExpanded by remember { mutableStateOf(false) }
    var gesturesExpanded by remember { mutableStateOf(false) }
    var featuresExpanded by remember { mutableStateOf(false) }
    var advancedExpanded by remember { mutableStateOf(false) }

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

            // Settings search bar
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextField(
                    value = settingsSearch, onValueChange = { settingsSearch = it },
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(22.dp)),
                    placeholder = { Text("Search settings\u2026", color = colors.textSecondary, fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = colors.textSecondary, modifier = Modifier.size(16.dp)) },
                    trailingIcon = if (settingsSearch.isNotBlank()) {{ IconButton(onClick = { settingsSearch = "" }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Close, "Clear", tint = colors.textSecondary, modifier = Modifier.size(14.dp)) } }} else null,
                    colors = TextFieldDefaults.colors(focusedTextColor = colors.text, unfocusedTextColor = colors.text, cursorColor = colors.accent, focusedContainerColor = colors.card, unfocusedContainerColor = colors.card, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                    singleLine = true, textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                )
            }

            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding()) {

                // ── THEME & WALLPAPER ──
                if (showTheme) {
                SectionHeader("Theme & Wallpaper", searching || themeExpanded, colors, summary = if (!searching && !themeExpanded) settings.themeMode.label else null) { themeExpanded = !themeExpanded }
                AnimatedVisibility(searching || themeExpanded) {
                    Column {
                        Lbl("Theme", colors)
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ThemeMode.entries.forEach { mode ->
                                val tc = themeColors(mode); val sel = settings.themeMode == mode
                                Column(
                                    Modifier.width(64.dp).clip(RoundedCornerShape(12.dp))
                                        .background(if (sel) tc.accent.copy(alpha = 0.12f) else colors.card)
                                        .border(if (sel) 2.dp else 0.5.dp, if (sel) tc.accent else colors.border, RoundedCornerShape(12.dp))
                                        .clickable { vm.setTheme(mode) }.padding(vertical = 10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                        listOf(tc.background, tc.accent, tc.card, tc.text).forEach { Box(Modifier.size(10.dp).clip(CircleShape).background(it).border(0.5.dp, tc.border, CircleShape)) }
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(mode.label, color = if (sel) tc.accent else colors.textSecondary, fontSize = 10.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                                }
                            }
                        }

                        Lbl("Wallpaper", colors)
                        ActionBtn("Change Wallpaper", "System Picker", colors) { vm.openWallpaperPicker() }
                        Spacer(Modifier.height(10.dp))
                        Text("Wallpaper Dim: ${settings.wallpaperDim}%", color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(4.dp))
                        Slider(
                            value = settings.wallpaperDim.toFloat(),
                            onValueChange = { vm.setWallpaperDim(it.toInt()) },
                            valueRange = 0f..80f, steps = 15,
                            colors = SliderDefaults.colors(thumbColor = colors.accent, activeTrackColor = colors.accent, inactiveTrackColor = colors.card),
                        )

                        // Accent Color Override
                        Lbl("Accent Color", colors)
                        val presetColors = listOf("#F44336", "#E91E63", "#9C27B0", "#673AB7", "#3F51B5", "#2196F3", "#00BCD4", "#4CAF50", "#FF9800", "#FF5722", "#795548", "#607D8B")
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            // Default (theme native accent) chip
                            val themeAccent = themeColors(settings.themeMode).accent
                            val isDefault = settings.accentOverride.isBlank()
                            Box(Modifier.size(28.dp).clip(CircleShape).background(themeAccent)
                                .border(if (isDefault) 2.dp else 0.dp, if (isDefault) colors.text else Color.Transparent, CircleShape)
                                .clickable { vm.setAccentOverride("") }) {
                                if (isDefault) Box(Modifier.size(10.dp).clip(CircleShape).background(colors.text).align(Alignment.Center))
                            }
                            presetColors.forEach { hex ->
                                val parsed = try { Color(android.graphics.Color.parseColor(hex)) } catch (_: Exception) { colors.accent }
                                val isSel = settings.accentOverride.equals(hex, ignoreCase = true)
                                Box(Modifier.size(28.dp).clip(CircleShape).background(parsed)
                                    .border(if (isSel) 2.dp else 0.dp, if (isSel) colors.text else Color.Transparent, CircleShape)
                                    .clickable { vm.setAccentOverride(hex) })
                            }
                        }
                        Spacer(Modifier.height(10.dp))
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
                                Text("Reset", color = colors.error, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(colors.error.copy(alpha = 0.1f))
                                        .clickable { accentInput = ""; vm.setAccentOverride("") }.padding(horizontal = 10.dp, vertical = 6.dp))
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }

                }
                // ── ICONS & LABELS ──
                if (showIcons) {
                SectionHeader("Icons & Labels", searching || iconsExpanded, colors, summary = if (!searching && !iconsExpanded) "${settings.iconShape.label} · ${settings.iconSize.label}" else null) { iconsExpanded = !iconsExpanded }
                AnimatedVisibility(searching || iconsExpanded) {
                    Column {
                        Lbl("Icon Shape", colors)
                        Chips(IconShape.entries.map { it to it.label }, settings.iconShape, colors) { vm.setShape(it) }
                        Spacer(Modifier.height(10.dp))
                        Lbl("Icon Size", colors)
                        Chips(IconSize.entries.map { it to it.label }, settings.iconSize, colors) { vm.setIconSize(it) }

                        Lbl("Icon Pack", colors)
                        IconPackSection(settings, availablePacks, iconPackLoading, colors, vm)
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                            Spacer(Modifier.height(6.dp))
                            Tog("Themed Icons (Android 13+)", settings.themedIcons, colors) { vm.setThemedIcons(it) }
                        }

                        Lbl("Icon Labels", colors)
                        Chips(LabelStyle.entries.map { it to it.label }, settings.labelStyle, colors) { vm.setLabelStyle(it) }
                        Spacer(Modifier.height(10.dp))
                        Lbl("Label Weight", colors)
                        Chips(LabelWeight.entries.map { it to it.label }, settings.labelWeight, colors) { vm.setLabelWeight(it) }
                        Spacer(Modifier.height(10.dp))
                        Lbl("Label Size", colors)
                        Chips(LabelSize.entries.map { it to it.label }, settings.labelSize, colors) { vm.setLabelSize(it) }

                        Tog("Icon Shadow", settings.iconShadow, colors) { vm.setIconShadow(it) }
                        Tog("Grayscale Icons", settings.grayscaleIcons, colors) { vm.setGrayscaleIcons(it) }
                        Spacer(Modifier.height(8.dp))
                    }
                }

                }
                // ── GRID & LAYOUT ──
                if (showGrid) {
                SectionHeader("Grid & Layout", searching || gridExpanded, colors, summary = if (!searching && !gridExpanded) "${settings.gridColumns}x${settings.gridRows} · ${settings.pageTransition.label}" else null) { gridExpanded = !gridExpanded }
                AnimatedVisibility(searching || gridExpanded) {
                    Column {
                        Lbl("Grid Columns", colors)
                        Chips((3..8).map { it to it.toString() }, settings.gridColumns, colors) { vm.setGridCols(it) }
                        Spacer(Modifier.height(10.dp))
                        Lbl("Grid Rows", colors)
                        Chips((3..10).map { it to it.toString() }, settings.gridRows, colors) { vm.setGridRows(it) }
                        Spacer(Modifier.height(10.dp))
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

                        Lbl("Page Transition", colors)
                        Chips(PageTransition.entries.map { it to it.label }, settings.pageTransition, colors) { vm.setPageTransition(it) }
                        Spacer(Modifier.height(10.dp))
                        Lbl("Page Indicator", colors)
                        Chips(PageIndicatorStyle.entries.map { it to it.label }, settings.pageIndicatorStyle, colors) { vm.setPageIndicatorStyle(it) }
                        Spacer(Modifier.height(10.dp))
                        Lbl("Badge Style", colors)
                        Chips(BadgeStyle.entries.map { it to it.label }, settings.badgeStyle, colors) { vm.setBadgeStyle(it) }
                        Spacer(Modifier.height(10.dp))
                        Lbl("Folder Columns", colors)
                        Chips((3..5).map { it to it.toString() }, settings.folderColumns, colors) { vm.setFolderColumns(it) }
                        Spacer(Modifier.height(8.dp))
                    }
                }

                }
                // ── DRAWER ──
                if (showDrawer) {
                SectionHeader("Drawer", searching || drawerExpanded, colors, summary = if (!searching && !drawerExpanded) "${settings.drawerSort.label} · ${settings.searchEngine.label}" else null) { drawerExpanded = !drawerExpanded }
                AnimatedVisibility(searching || drawerExpanded) {
                    Column {
                        Lbl("Drawer Sort", colors)
                        Chips(DrawerSort.entries.map { it to it.label }, settings.drawerSort, colors) { vm.setDrawerSort(it) }
                        Spacer(Modifier.height(10.dp))
                        Lbl("Drawer Columns", colors)
                        Chips((0..6).map { it to if (it == 0) "Auto" else it.toString() }, settings.drawerColumns, colors) { vm.setDrawerColumns(it) }
                        Spacer(Modifier.height(10.dp))
                        Lbl("Drawer Background", colors)
                        Text("Opacity: ${settings.drawerOpacity}%", color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Slider(
                            value = settings.drawerOpacity.toFloat(),
                            onValueChange = { vm.setDrawerOpacity(it.toInt()) },
                            valueRange = 50f..100f, steps = 49,
                            colors = SliderDefaults.colors(thumbColor = colors.accent, activeTrackColor = colors.accent, inactiveTrackColor = colors.card),
                        )
                        Tog("Drawer Categories", settings.drawerCategories, colors) { vm.setDrawerCategories(it) }
                        Tog("Drawer Section Headers", settings.drawerSectionHeaders, colors) { vm.setDrawerSectionHeaders(it) }
                        Tog("Drawer Animation", settings.drawerAnimation, colors) { vm.setDrawerAnimation(it) }
                        Tog("App Suggestions", settings.showSuggestions, colors) { vm.setShowSuggestions(it) }

                        Lbl("Search Engine", colors)
                        Chips(SearchEngine.entries.map { it to it.label }, settings.searchEngine, colors) { vm.setSearchEngine(it) }
                        Spacer(Modifier.height(8.dp))
                    }
                }

                }
                // ── DOCK ──
                if (showDock) {
                SectionHeader("Dock", searching || dockExpanded, colors, summary = if (!searching && !dockExpanded) "${settings.dockCount} icons · ${settings.dockStyle.label}" else null) { dockExpanded = !dockExpanded }
                AnimatedVisibility(searching || dockExpanded) {
                    Column {
                        Lbl("Dock Icons", colors)
                        Chips((3..7).map { it to it.toString() }, settings.dockCount, colors) { vm.setDockCount(it) }
                        Spacer(Modifier.height(10.dp))
                        Lbl("Dock Style", colors)
                        Chips(DockStyle.entries.map { it to it.label }, settings.dockStyle, colors) { vm.setDockStyle(it) }
                        Spacer(Modifier.height(10.dp))
                        Lbl("Search Bar", colors)
                        Chips(SearchBarStyle.entries.map { it to it.label }, settings.searchBarStyle, colors) { vm.setSearchBarStyle(it) }
                        Tog("Dock Search Bar", settings.showDockSearch, colors) { vm.setShowDockSearch(it) }
                        Tog("Hide Dock", settings.hideDock, colors) { vm.setHideDock(it) }
                        Spacer(Modifier.height(8.dp))
                    }
                }

                }
                // ── GESTURES ──
                if (showGestures) {
                SectionHeader("Gestures", searching || gesturesExpanded, colors) { gesturesExpanded = !gesturesExpanded }
                AnimatedVisibility(searching || gesturesExpanded) {
                    Column {
                        GesturePicker("Double-Tap", settings.doubleTapAction, colors, vm = vm, gestureSource = "double_tap") { vm.setDoubleTapAction(it) }
                        GesturePicker("Swipe Down", settings.swipeDownAction, colors, vm = vm, gestureSource = "swipe_down") { vm.setSwipeDownAction(it) }
                        GesturePicker("Triple-Tap", settings.tripleTapAction, colors, vm = vm, gestureSource = "triple_tap") { vm.setTripleTapAction(it) }
                        GesturePicker("Pinch In", settings.pinchAction, colors, vm = vm, gestureSource = "pinch") { vm.setPinchAction(it) }
                        GesturePicker("Swipe Up", settings.swipeUpAction, colors, vm = vm, gestureSource = "swipe_up") { vm.setSwipeUpAction(it) }
                        GesturePicker("Dock Handle Tap", settings.dockTapAction, colors, vm = vm, gestureSource = "dock_tap") { vm.setDockTapAction(it) }

                        val adminEnabled = remember { mutableStateOf(vm.isDeviceAdminEnabled()) }
                        if (listOf(settings.doubleTapAction, settings.swipeDownAction, settings.swipeUpAction, settings.tripleTapAction, settings.pinchAction, settings.dockTapAction).any { it == GestureAction.LOCK_SCREEN }) {
                            Spacer(Modifier.height(6.dp))
                            if (!adminEnabled.value) {
                                ActionBtn("Enable Lock Screen", "Requires Device Admin", colors) { vm.requestDeviceAdmin(); adminEnabled.value = vm.isDeviceAdminEnabled() }
                            } else {
                                Text("Device Admin: Enabled", color = colors.accent, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }

                }
                // ── FEATURES ──
                if (showFeatures) {
                SectionHeader("Features", searching || featuresExpanded, colors) { featuresExpanded = !featuresExpanded }
                AnimatedVisibility(searching || featuresExpanded) {
                    Column {
                        Tog("At-a-Glance Clock", settings.showClock, colors) { vm.setShowClock(it) }
                        if (settings.showClock) {
                            Lbl("Clock Style", colors)
                            Chips(ClockStyle.entries.map { it to it.label }, settings.clockStyle, colors) { vm.setClockStyle(it) }
                        }
                        Tog("Auto-Place New Apps", settings.autoPlaceNew, colors) { vm.setAutoPlaceNew(it) }
                        Tog("Notification Badges", settings.showNotifBadges, colors) { vm.setShowNotifBadges(it) }
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
                        Tog("Hide Status Bar", settings.hideStatusBar, colors) { vm.setHideStatusBar(it) }
                        Tog("Lock Home Screen", settings.homeLocked, colors) { vm.setHomeLocked(it) }
                        Tog("Wallpaper Parallax", settings.wallpaperParallax, colors) { vm.setWallpaperParallax(it) }

                        Lbl("Haptic Feedback", colors)
                        Chips(HapticLevel.entries.map { it to it.label }, settings.hapticLevel, colors) { vm.setHapticLevel(it) }
                        Spacer(Modifier.height(8.dp))
                    }
                }

                }
                // ── ADVANCED ──
                if (showAdvanced) {
                SectionHeader("Advanced", searching || advancedExpanded, colors) { advancedExpanded = !advancedExpanded }
                AnimatedVisibility(searching || advancedExpanded) {
                    Column {
                        Lbl("Quick Actions", colors)
                        ActionBtn("Kill Background Apps", "Free memory", colors) { vm.killBackgroundApps() }
                        Spacer(Modifier.height(8.dp))
                        ActionBtn("Clear Search History", "Remove saved searches", colors) { vm.clearSearchHistory() }
                        Spacer(Modifier.height(8.dp))
                        var showResetConfirm by remember { mutableStateOf(false) }
                        ActionBtn("Reset All Settings", "Restore defaults", colors) { showResetConfirm = true }
                        if (showResetConfirm) {
                            androidx.compose.ui.window.Dialog(onDismissRequest = { showResetConfirm = false }) {
                                Column(Modifier.clip(RoundedCornerShape(20.dp)).background(colors.surface)
                                    .border(0.5.dp, colors.border, RoundedCornerShape(20.dp)).padding(24.dp)) {
                                    Text("Reset All Settings", color = colors.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                                    Spacer(Modifier.height(10.dp))
                                    Text("This will restore all settings to defaults. Your home screen layout will be preserved.", color = colors.textSecondary, fontSize = 14.sp)
                                    Spacer(Modifier.height(18.dp))
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                        TextButton(onClick = { showResetConfirm = false }) { Text("Cancel", color = colors.textSecondary) }
                                        Spacer(Modifier.width(8.dp))
                                        Button(onClick = { showResetConfirm = false; vm.resetAllSettings() },
                                            colors = ButtonDefaults.buttonColors(containerColor = colors.error),
                                            shape = RoundedCornerShape(12.dp)) { Text("Reset", color = Color.White) }
                                    }
                                }
                            }
                        }

                        Lbl("Backup & Restore", colors)
                        ActionBtn("Export Layout", "Save to file", colors) { exportLauncher.launch("lawnchair-lite-backup.json") }
                        Spacer(Modifier.height(8.dp))
                        ActionBtn("Restore Layout", "Load from file", colors) { importLauncher.launch(arrayOf("application/json", "*/*")) }

                        // Hidden apps
                        Lbl("Hidden Apps", colors)
                        val hiddenInfos = remember(hiddenApps, allAppsRaw) { allAppsRaw.filter { it.key in hiddenApps } }
                        if (hiddenInfos.isEmpty()) Text("No hidden apps", color = colors.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp))
                        else {
                            if (hiddenInfos.size > 1) {
                                Text("Unhide All (${hiddenInfos.size})", color = colors.accent, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(colors.accent.copy(alpha = 0.12f))
                                        .clickable { hiddenInfos.forEach { vm.unhideApp(it.key) } }.padding(horizontal = 12.dp, vertical = 6.dp))
                                Spacer(Modifier.height(8.dp))
                            }
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

                // No results
                if (searching && !showTheme && !showIcons && !showGrid && !showDrawer && !showDock && !showGestures && !showFeatures && !showAdvanced) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), Alignment.Center) {
                        Text("No settings match \"$settingsSearch\"", color = colors.textSecondary, fontSize = 13.sp)
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

// ── Section Header ───────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String, expanded: Boolean, c: LauncherColors, summary: String? = null, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .padding(top = 16.dp, bottom = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(c.card)
            .border(0.5.dp, if (expanded) c.accent.copy(alpha = 0.3f) else c.border, RoundedCornerShape(12.dp))
            .clickable { onToggle() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = if (expanded) c.accent else c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            if (summary != null) {
                Text(summary, color = c.textSecondary, fontSize = 11.sp, maxLines = 1)
            }
        }
        Icon(
            if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = if (expanded) "Collapse" else "Expand",
            tint = if (expanded) c.accent else c.textSecondary,
            modifier = Modifier.size(20.dp),
        )
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
            Text("Reset", color = c.error, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(c.error.copy(alpha = 0.1f))
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
                    // Preview icons (loaded async to avoid blocking compose thread)
                    var previewIcons by remember { mutableStateOf<List<android.graphics.drawable.Drawable?>>(emptyList()) }
                    LaunchedEffect(pack.packageName) { vm.getIconPackPreviewAsync(pack.packageName) { previewIcons = it } }
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
                            // Icon preview row
                            if (previewIcons.isNotEmpty()) {
                                Spacer(Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    previewIcons.forEach { drawable ->
                                        if (drawable != null) {
                                            Image(rememberDrawablePainter(drawable), null, Modifier.size(24.dp).clip(RoundedCornerShape(6.dp)))
                                        }
                                    }
                                }
                            }
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

@Composable private fun GesturePicker(label: String, current: GestureAction, c: LauncherColors, vm: LauncherViewModel? = null, gestureSource: String = "", onChange: (GestureAction) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var showAppPicker by remember { mutableStateOf(false) }
    val currentAppKey = if (vm != null && gestureSource.isNotBlank()) vm.getGestureApp(gestureSource) else ""
    val currentAppLabel = if (current == GestureAction.LAUNCH_APP && currentAppKey.isNotBlank()) {
        vm?.resolveApp(currentAppKey)?.label ?: "App"
    } else current.label

    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(c.card).border(0.5.dp, c.border, RoundedCornerShape(10.dp)).clickable { expanded = !expanded }.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.Medium); Spacer(Modifier.weight(1f))
            if (current == GestureAction.LAUNCH_APP && currentAppKey.isNotBlank()) {
                val gestureAppIcon = vm?.resolveApp(currentAppKey)?.icon
                if (gestureAppIcon != null) { Image(rememberDrawablePainter(gestureAppIcon), null, Modifier.size(18.dp).clip(RoundedCornerShape(4.dp))); Spacer(Modifier.width(6.dp)) }
            }
            Text(if (current == GestureAction.LAUNCH_APP) "Launch: $currentAppLabel" else current.label, color = c.accent, fontSize = 13.sp)
        }
        AnimatedVisibility(expanded) {
            Column(Modifier.padding(start = 8.dp, top = 4.dp)) {
                GestureAction.entries.forEach { action -> val sel = action == current
                    Text(action.label, color = if (sel) c.accent else c.text, fontSize = 13.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(if (sel) c.accent.copy(alpha = 0.1f) else Color.Transparent)
                            .clickable {
                                onChange(action)
                                if (action == GestureAction.LAUNCH_APP) showAppPicker = true
                                else { expanded = false; showAppPicker = false }
                            }.padding(horizontal = 12.dp, vertical = 10.dp))
                }
            }
        }
        // App picker for LAUNCH_APP
        if (showAppPicker && vm != null && gestureSource.isNotBlank()) {
            val allAppsForPicker by vm.allApps.collectAsState()
            Column(Modifier.padding(start = 16.dp, top = 4.dp).heightIn(max = 200.dp).verticalScroll(rememberScrollState())) {
                Text("Select app:", color = c.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
                allAppsForPicker.forEach { pickApp ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                            .clickable { vm.setGestureApp(gestureSource, pickApp.key); showAppPicker = false; expanded = false }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (pickApp.icon != null) {
                            Image(com.google.accompanist.drawablepainter.rememberDrawablePainter(pickApp.icon), null, Modifier.size(24.dp).clip(RoundedCornerShape(6.dp)))
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(pickApp.label, color = c.text, fontSize = 12.sp, maxLines = 1)
                    }
                }
            }
        }
    }
}
