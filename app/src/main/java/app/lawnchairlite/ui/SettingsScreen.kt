package app.lawnchairlite.ui

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lawnchairlite.R
import app.lawnchairlite.LauncherViewModel
import app.lawnchairlite.data.*
import kotlinx.coroutines.launch

@Composable
fun SettingsPanel(
    visible: Boolean, settings: LauncherSettings,
    vm: LauncherViewModel, onClose: () -> Unit,
) {
    val colors = LocalLauncherColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val backupExportedMessage = stringResource(R.string.backup_exported)
    val themeExportedMessage = stringResource(R.string.theme_exported)
    val exportFailedMessage = stringResource(R.string.export_failed)
    val invalidImportMessage = stringResource(R.string.import_failed_invalid_file)
    val restoreFailedMessage = stringResource(R.string.restore_failed)
    val hiddenApps by vm.hiddenApps.collectAsState()
    val allAppsRaw by vm.allApps.collectAsState()
    val homeGrid by vm.homeGrid.collectAsState()
    val availablePacks by vm.availablePacks.collectAsState()
    val iconPackLoading by vm.iconPackLoading.collectAsState()
    var includeBackupSearchHistory by remember { mutableStateOf(false) }
    var includeBackupUsage by remember { mutableStateOf(false) }
    var includeBackupHiddenApps by remember { mutableStateOf(false) }
    var pendingBackupOptions by remember { mutableStateOf(BackupExportOptions()) }
    var pendingRestoreJson by remember { mutableStateOf<String?>(null) }
    var restorePreview by remember { mutableStateOf<BackupImportPreview?>(null) }
    var diagnosticReports by remember { mutableStateOf(vm.diagnosticReports()) }
    var permissionRefresh by remember { mutableIntStateOf(0) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permissionRefresh++ }
    val contactPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permissionRefresh++ }
    val calendarPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permissionRefresh++ }
    val locationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permissionRefresh++ }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val json = vm.exportBackup(pendingBackupOptions)
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
            }.onSuccess {
                android.widget.Toast.makeText(context, backupExportedMessage, android.widget.Toast.LENGTH_SHORT).show()
            }.onFailure {
                android.widget.Toast.makeText(context, exportFailedMessage, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
    val exportThemeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val json = vm.exportTheme()
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
            }.onSuccess {
                android.widget.Toast.makeText(context, themeExportedMessage, android.widget.Toast.LENGTH_SHORT).show()
            }.onFailure {
                android.widget.Toast.makeText(context, exportFailedMessage, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
    val importThemeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val payload = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            if (payload == null) {
                android.widget.Toast.makeText(context, invalidImportMessage, android.widget.Toast.LENGTH_SHORT).show()
                return@launch
            }
            vm.importTheme(payload.decodeToString())
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val payload = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            if (payload == null) {
                android.widget.Toast.makeText(context, invalidImportMessage, android.widget.Toast.LENGTH_SHORT).show()
                return@launch
            }
            val prepared = vm.prepareBackupImport(payload)
            val preview = prepared.preview
            if (!preview.canImport) {
                android.widget.Toast.makeText(context, preview.error ?: restoreFailedMessage, android.widget.Toast.LENGTH_SHORT).show()
                restorePreview = preview
                pendingRestoreJson = null
                return@launch
            }
            pendingRestoreJson = prepared.json
            restorePreview = preview
        }
    }

    // Settings search
    var settingsSearch by remember { mutableStateOf("") }
    val sq = settingsSearch.lowercase()
    // Keywords per section for search matching
    val themeKeywords = "theme wallpaper dim accent color dynamic material monet midnight glass oled mocha aurora neon"
    val iconsKeywords = "icon shape size pack themed shadow grayscale label weight squircle circle square teardrop hexagon diamond"
    val gridKeywords = "grid columns rows padding page transition indicator badge folder cube stack fade depth carousel slide dots line"
    val drawerKeywords = "drawer sort columns opacity categories category rules groups folders regex package prefix install source section headers animation suggestions search engine"
    val dockKeywords = "dock icons style search bar pill floating transparent hide labels label opacity"
    val gesturesKeywords = "gesture double tap swipe down swipe up triple pinch dock lock screen notification flashlight edit mode recent app launch custom draw recorder assistant replacement corner voice default"
    val featuresKeywords = "clock smartspace at a glance weather calendar event auto place notification badges status bar home lock parallax haptic feedback"
    val advancedKeywords = "kill background apps clear search history reset settings backup restore export import hidden apps diagnostics crash report support bundle permissions package visibility notification contacts calendar location widget about"
    fun sectionMatches(keywords: String): Boolean = sq.isBlank() || keywords.contains(sq) || sq.split(" ").all { w -> keywords.contains(w) }
    val showTheme = sectionMatches(themeKeywords)
    val showIcons = sectionMatches(iconsKeywords)
    val showGrid = sectionMatches(gridKeywords)
    val showDrawer = sectionMatches(drawerKeywords)
    val showDock = sectionMatches(dockKeywords)
    val showGestures = sectionMatches(gesturesKeywords)
    val showFeatures = sectionMatches(featuresKeywords)
    val showAdvanced = sectionMatches(advancedKeywords)
    val pageSize = (settings.gridColumns * settings.gridRows).coerceAtLeast(1)
    val pageCount = ((homeGrid.size + pageSize - 1) / pageSize).coerceAtLeast(1)
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
    var showCustomGestureRecorder by remember { mutableStateOf(false) }

    // Swipe-down-to-dismiss: track overscroll at top
    val scrollState = rememberScrollState()
    var dismissDrag by remember { mutableFloatStateOf(0f) }
    val dismissThreshold = with(LocalDensity.current) { 60.dp.toPx() }
    val dismissNestedScroll = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (dismissDrag > 0f && available.y < 0f) {
                    dismissDrag = (dismissDrag + available.y * 2.5f).coerceAtLeast(0f)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0f && scrollState.value == 0) {
                    dismissDrag += available.y * 2.5f
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (dismissDrag > 0f) {
                    val shouldClose = dismissDrag > dismissThreshold || available.y > 300f
                    dismissDrag = 0f
                    if (shouldClose) onClose()
                }
                return Velocity.Zero
            }
        }
    }
    // Reset dismiss drag when panel closes
    LaunchedEffect(visible) { if (!visible) dismissDrag = 0f }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(300, easing = FastOutSlowInEasing)) + fadeIn(tween(150)),
        exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(200)) + fadeOut(tween(100)),
    ) {
        Column(
            Modifier.fillMaxSize().background(colors.background.copy(alpha = 0.97f)).statusBarsPadding()
                .nestedScroll(dismissNestedScroll)
                .graphicsLayer { translationY = dismissDrag * 0.5f; alpha = 1f - (dismissDrag / dismissThreshold * 0.3f).coerceIn(0f, 0.3f) }
        ) {
            // Drag handle
            Box(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp), Alignment.Center) {
                Box(Modifier.width(48.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(colors.textSecondary.copy(alpha = 0.5f)))
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = colors.textSecondary, modifier = Modifier.size(22.dp)) }
                Spacer(Modifier.weight(1f)); Text(stringResource(R.string.settings), color = colors.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f)); Spacer(Modifier.size(48.dp))
            }
            HorizontalDivider(color = colors.border, thickness = 0.5.dp)

            // Settings search bar
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextField(
                    value = settingsSearch, onValueChange = { settingsSearch = it },
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(22.dp)),
                    placeholder = { Text(stringResource(R.string.settings_search_hint), color = colors.textSecondary, fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = colors.textSecondary, modifier = Modifier.size(16.dp)) },
                    trailingIcon = if (settingsSearch.isNotBlank()) {{ IconButton(onClick = { settingsSearch = "" }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Close, stringResource(R.string.clear), tint = colors.textSecondary, modifier = Modifier.size(14.dp)) } }} else null,
                    colors = TextFieldDefaults.colors(focusedTextColor = colors.text, unfocusedTextColor = colors.text, cursorColor = colors.accent, focusedContainerColor = colors.card, unfocusedContainerColor = colors.card, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                    singleLine = true, textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                )
            }

            Column(Modifier.fillMaxSize().verticalScroll(scrollState).padding(horizontal = 20.dp).navigationBarsPadding()) {

                // ── THEME & WALLPAPER ──
                if (showTheme) {
                SectionHeader(stringResource(R.string.theme_wallpaper), searching || themeExpanded, colors, summary = if (!searching && !themeExpanded) settings.themeMode.localizedLabel() else null) { themeExpanded = !themeExpanded }
                AnimatedVisibility(searching || themeExpanded) {
                    Column {
                        Lbl(stringResource(R.string.theme), colors)
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
                                    Text(mode.localizedLabel(), color = if (sel) tc.accent else colors.textSecondary, fontSize = 10.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                                }
                            }
                        }

                        Lbl(stringResource(R.string.wallpaper), colors)
                        ActionBtn(stringResource(R.string.change_wallpaper), stringResource(R.string.system_picker), colors) { vm.openWallpaperPicker() }
                        Spacer(Modifier.height(10.dp))
                        Text(stringResource(R.string.wallpaper_dim_format, settings.wallpaperDim), color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(4.dp))
                        Slider(
                            value = settings.wallpaperDim.toFloat(),
                            onValueChange = { vm.setWallpaperDim(it.toInt()) },
                            valueRange = 0f..80f, steps = 15,
                            colors = SliderDefaults.colors(thumbColor = colors.accent, activeTrackColor = colors.accent, inactiveTrackColor = colors.card),
                        )
                        if (pageCount > 1) {
                            Spacer(Modifier.height(10.dp))
                            Lbl(stringResource(R.string.page_wallpaper_dim_overrides), colors)
                            (0 until pageCount).forEach { page ->
                                val hasOverride = settings.pageWallpaperDims.containsKey(page)
                                val pageDim = settings.pageWallpaperDims[page] ?: settings.wallpaperDim
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text(stringResource(R.string.page_wallpaper_dim_format, page + 1, pageDim), color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    Spacer(Modifier.weight(1f))
                                    Text(
                                        stringResource(if (hasOverride) R.string.reset else R.string.page_dim_uses_default),
                                        color = if (hasOverride) colors.error else colors.textSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background((if (hasOverride) colors.error else colors.card).copy(alpha = if (hasOverride) 0.1f else 0.65f))
                                            .then(if (hasOverride) Modifier.clickable { vm.resetPageWallpaperDim(page) } else Modifier)
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                    )
                                }
                                Slider(
                                    value = pageDim.toFloat(),
                                    onValueChange = { vm.setPageWallpaperDim(page, it.toInt()) },
                                    valueRange = 0f..80f,
                                    steps = 15,
                                    colors = SliderDefaults.colors(thumbColor = colors.accent, activeTrackColor = colors.accent, inactiveTrackColor = colors.card),
                                )
                            }
                        }

                        // Accent Color Override
                        Lbl(stringResource(R.string.accent_color), colors)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            Tog(stringResource(R.string.material_you_dynamic_color), settings.dynamicColor, colors) { vm.setDynamicColor(it) }
                        } else {
                            Text(stringResource(R.string.material_you_requires_android_12), color = colors.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
                        }
                        val presetColors = listOf("#F44336", "#E91E63", "#9C27B0", "#673AB7", "#3F51B5", "#2196F3", "#00BCD4", "#4CAF50", "#FF9800", "#FF5722", "#795548", "#607D8B")
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            // Default (theme native accent) chip
                            val themeAccent = if (settings.dynamicColor) colors.accent else themeColors(settings.themeMode).accent
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
                            Text(stringResource(R.string.apply), color = colors.accent, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(colors.accent.copy(alpha = 0.12f))
                                    .clickable { vm.setAccentOverride(accentInput.trim()) }.padding(horizontal = 10.dp, vertical = 6.dp))
                            if (settings.accentOverride.isNotBlank()) {
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.reset), color = colors.error, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(colors.error.copy(alpha = 0.1f))
                                        .clickable { accentInput = ""; vm.setAccentOverride("") }.padding(horizontal = 10.dp, vertical = 6.dp))
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        ActionBtn(stringResource(R.string.export_theme), stringResource(R.string.lawnchair_theme_file), colors) {
                            exportThemeLauncher.launch("lawnchair-lite-theme.lawnchair-theme")
                        }
                        Spacer(Modifier.height(8.dp))
                        ActionBtn(stringResource(R.string.import_theme), stringResource(R.string.lawnchair_theme_file), colors) {
                            importThemeLauncher.launch(arrayOf("application/json", "*/*"))
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }

                }
                // ── ICONS & LABELS ──
                if (showIcons) {
                SectionHeader(stringResource(R.string.icons_labels), searching || iconsExpanded, colors, summary = if (!searching && !iconsExpanded) stringResource(R.string.two_part_summary, settings.iconShape.localizedLabel(), settings.iconSize.localizedLabel()) else null) { iconsExpanded = !iconsExpanded }
                AnimatedVisibility(searching || iconsExpanded) {
                    Column {
                        Lbl(stringResource(R.string.icon_shape), colors)
                        Chips(IconShape.entries.map { it to it.localizedLabel() }, settings.iconShape, colors) { vm.setShape(it) }
                        Spacer(Modifier.height(10.dp))
                        Lbl(stringResource(R.string.icon_size), colors)
                        Chips(IconSize.entries.map { it to it.localizedLabel() }, settings.iconSize, colors) { vm.setIconSize(it) }

                        Lbl(stringResource(R.string.icon_pack), colors)
                        IconPackSection(settings, availablePacks, iconPackLoading, colors, vm)
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                            Spacer(Modifier.height(6.dp))
                            Tog(stringResource(R.string.themed_icons_android_13), settings.themedIcons, colors) { vm.setThemedIcons(it) }
                        }

                        Lbl(stringResource(R.string.icon_labels), colors)
                        Chips(LabelStyle.entries.map { it to it.localizedLabel() }, settings.labelStyle, colors) { vm.setLabelStyle(it) }
                        Spacer(Modifier.height(10.dp))
                        Lbl(stringResource(R.string.label_weight), colors)
                        Chips(LabelWeight.entries.map { it to it.localizedLabel() }, settings.labelWeight, colors) { vm.setLabelWeight(it) }
                        Spacer(Modifier.height(10.dp))
                        Lbl(stringResource(R.string.label_size), colors)
                        Chips(LabelSize.entries.map { it to it.localizedLabel() }, settings.labelSize, colors) { vm.setLabelSize(it) }

                        Tog(stringResource(R.string.icon_shadow), settings.iconShadow, colors) { vm.setIconShadow(it) }
                        Tog(stringResource(R.string.grayscale_icons), settings.grayscaleIcons, colors) { vm.setGrayscaleIcons(it) }
                        Spacer(Modifier.height(8.dp))
                    }
                }

                }
                // ── GRID & LAYOUT ──
                if (showGrid) {
                SectionHeader(stringResource(R.string.grid_layout), searching || gridExpanded, colors, summary = if (!searching && !gridExpanded) stringResource(R.string.grid_summary, settings.gridColumns, settings.gridRows, settings.pageTransition.localizedLabel()) else null) { gridExpanded = !gridExpanded }
                AnimatedVisibility(searching || gridExpanded) {
                    Column {
                        Lbl(stringResource(R.string.grid_columns), colors)
                        Chips((3..8).map { it to it.toString() }, settings.gridColumns, colors) { vm.setGridCols(it) }
                        Spacer(Modifier.height(10.dp))
                        Lbl(stringResource(R.string.grid_rows), colors)
                        Chips((3..10).map { it to it.toString() }, settings.gridRows, colors) { vm.setGridRows(it) }
                        Spacer(Modifier.height(10.dp))
                        Lbl(stringResource(R.string.grid_padding), colors)
                        Text(stringResource(R.string.grid_padding_horizontal, settings.gridPaddingH), color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Slider(
                            value = settings.gridPaddingH.toFloat(),
                            onValueChange = { vm.setGridPaddingH(it.toInt()) },
                            valueRange = 0f..24f, steps = 23,
                            colors = SliderDefaults.colors(thumbColor = colors.accent, activeTrackColor = colors.accent, inactiveTrackColor = colors.card),
                        )
                        Text(stringResource(R.string.grid_padding_vertical, settings.gridPaddingV), color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Slider(
                            value = settings.gridPaddingV.toFloat(),
                            onValueChange = { vm.setGridPaddingV(it.toInt()) },
                            valueRange = 0f..24f, steps = 23,
                            colors = SliderDefaults.colors(thumbColor = colors.accent, activeTrackColor = colors.accent, inactiveTrackColor = colors.card),
                        )

                        Lbl(stringResource(R.string.page_transition), colors)
                        Chips(PageTransition.entries.map { it to it.localizedLabel() }, settings.pageTransition, colors) { vm.setPageTransition(it) }
                        Spacer(Modifier.height(10.dp))
                        Lbl(stringResource(R.string.page_indicator), colors)
                        Chips(PageIndicatorStyle.entries.map { it to it.localizedLabel() }, settings.pageIndicatorStyle, colors) { vm.setPageIndicatorStyle(it) }
                        Spacer(Modifier.height(10.dp))
                        Lbl(stringResource(R.string.badge_style), colors)
                        Chips(BadgeStyle.entries.map { it to it.localizedLabel() }, settings.badgeStyle, colors) { vm.setBadgeStyle(it) }
                        Spacer(Modifier.height(10.dp))
                        Lbl(stringResource(R.string.folder_columns), colors)
                        Chips((3..5).map { it to it.toString() }, settings.folderColumns, colors) { vm.setFolderColumns(it) }
                        Spacer(Modifier.height(8.dp))
                    }
                }

                }
                // ── DRAWER ──
                if (showDrawer) {
                SectionHeader(stringResource(R.string.drawer), searching || drawerExpanded, colors, summary = if (!searching && !drawerExpanded) stringResource(R.string.two_part_summary, settings.drawerSort.localizedLabel(), settings.searchEngine.localizedLabel()) else null) { drawerExpanded = !drawerExpanded }
                AnimatedVisibility(searching || drawerExpanded) {
                    Column {
                        Lbl(stringResource(R.string.drawer_sort), colors)
                        Chips(DrawerSort.entries.map { it to it.localizedLabel() }, settings.drawerSort, colors) { vm.setDrawerSort(it) }
                        Spacer(Modifier.height(10.dp))
                        Lbl(stringResource(R.string.drawer_columns), colors)
                        Chips((0..6).map { it to if (it == 0) stringResource(R.string.auto) else it.toString() }, settings.drawerColumns, colors) { vm.setDrawerColumns(it) }
                        Spacer(Modifier.height(10.dp))
                        Lbl(stringResource(R.string.drawer_background), colors)
                        Text(stringResource(R.string.drawer_opacity, settings.drawerOpacity), color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Slider(
                            value = settings.drawerOpacity.toFloat(),
                            onValueChange = { vm.setDrawerOpacity(it.toInt()) },
                            valueRange = 50f..100f, steps = 49,
                            colors = SliderDefaults.colors(thumbColor = colors.accent, activeTrackColor = colors.accent, inactiveTrackColor = colors.card),
                        )
                        Tog(stringResource(R.string.drawer_categories), settings.drawerCategories, colors) { vm.setDrawerCategories(it) }
                        if (settings.drawerCategories || settings.categoryRules.isNotEmpty()) {
                            CategoryRulesSection(settings.categoryRules, colors, vm)
                        }
                        DrawerGroupsSection(settings.drawerGroups, allAppsRaw, colors, vm)
                        Tog(stringResource(R.string.drawer_section_headers), settings.drawerSectionHeaders, colors) { vm.setDrawerSectionHeaders(it) }
                        Tog(stringResource(R.string.drawer_animation), settings.drawerAnimation, colors) { vm.setDrawerAnimation(it) }
                        Tog(stringResource(R.string.app_suggestions), settings.showSuggestions, colors) { vm.setShowSuggestions(it) }

                        Lbl(stringResource(R.string.search_engine), colors)
                        Chips(SearchEngine.entries.map { it to it.localizedLabel() }, settings.searchEngine, colors) { vm.setSearchEngine(it) }
                        Spacer(Modifier.height(8.dp))
                    }
                }

                }
                // ── DOCK ──
                if (showDock) {
                val dockSummary = if (!searching && !dockExpanded) stringResource(if (settings.dockLabels) R.string.dock_summary_with_labels else R.string.dock_summary, settings.dockCount, settings.dockStyle.localizedLabel()) else null
                SectionHeader(stringResource(R.string.dock), searching || dockExpanded, colors, summary = dockSummary) { dockExpanded = !dockExpanded }
                AnimatedVisibility(searching || dockExpanded) {
                    Column {
                        Lbl(stringResource(R.string.dock_icons), colors)
                        Chips((3..7).map { it to it.toString() }, settings.dockCount, colors) { vm.setDockCount(it) }
                        Spacer(Modifier.height(10.dp))
                        Lbl(stringResource(R.string.dock_style), colors)
                        Chips(DockStyle.entries.map { it to it.localizedLabel() }, settings.dockStyle, colors) { vm.setDockStyle(it) }
                        Spacer(Modifier.height(10.dp))
                        Lbl(stringResource(R.string.search_bar), colors)
                        Chips(SearchBarStyle.entries.map { it to it.localizedLabel() }, settings.searchBarStyle, colors) { vm.setSearchBarStyle(it) }
                        Tog(stringResource(R.string.dock_search_bar), settings.showDockSearch, colors) { vm.setShowDockSearch(it) }
                        Tog(stringResource(R.string.hide_dock), settings.hideDock, colors) { vm.setHideDock(it) }
                        Tog(stringResource(R.string.dock_labels), settings.dockLabels, colors) { vm.setDockLabels(it) }
                        if (settings.dockLabels) {
                            Text(stringResource(R.string.dock_label_opacity, settings.dockLabelOpacity), color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Slider(
                                value = settings.dockLabelOpacity.toFloat(),
                                onValueChange = { vm.setDockLabelOpacity(it.toInt()) },
                                valueRange = 35f..100f,
                                colors = SliderDefaults.colors(thumbColor = colors.accent, activeTrackColor = colors.accent, inactiveTrackColor = colors.card),
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }

                }
                // ── GESTURES ──
                if (showGestures) {
                SectionHeader(stringResource(R.string.gestures), searching || gesturesExpanded, colors) { gesturesExpanded = !gesturesExpanded }
                AnimatedVisibility(searching || gesturesExpanded) {
                    Column {
                        GesturePicker(stringResource(R.string.double_tap), settings.doubleTapAction, colors, vm = vm, gestureSource = "double_tap") { vm.setDoubleTapAction(it) }
                        GesturePicker(stringResource(R.string.swipe_down), settings.swipeDownAction, colors, vm = vm, gestureSource = "swipe_down") { vm.setSwipeDownAction(it) }
                        GesturePicker(stringResource(R.string.triple_tap), settings.tripleTapAction, colors, vm = vm, gestureSource = "triple_tap") { vm.setTripleTapAction(it) }
                        GesturePicker(stringResource(R.string.pinch_in), settings.pinchAction, colors, vm = vm, gestureSource = "pinch") { vm.setPinchAction(it) }
                        GesturePicker(stringResource(R.string.swipe_up), settings.swipeUpAction, colors, vm = vm, gestureSource = "swipe_up") { vm.setSwipeUpAction(it) }
                        GesturePicker(stringResource(R.string.dock_handle_tap), settings.dockTapAction, colors, vm = vm, gestureSource = "dock_tap") { vm.setDockTapAction(it) }
                        AssistantReplacementPicker(settings.assistantApp, allAppsRaw, colors, vm)
                        GesturePicker(stringResource(R.string.custom_draw_gesture), settings.customGestureAction, colors, vm = vm, gestureSource = "custom_gesture") { vm.setCustomGestureAction(it) }
                        ActionBtn(
                            stringResource(R.string.record_custom_gesture),
                            stringResource(if (settings.customGesturePattern.isBlank()) R.string.gesture_not_recorded else R.string.gesture_recorded),
                            colors,
                        ) { showCustomGestureRecorder = true }
                        if (settings.customGesturePattern.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            ActionBtn(stringResource(R.string.clear_custom_gesture), stringResource(R.string.gesture_recorded), colors) { vm.clearCustomGesturePattern() }
                        }

                        val adminEnabled = remember { mutableStateOf(vm.isDeviceAdminEnabled()) }
                        if (listOf(settings.doubleTapAction, settings.swipeDownAction, settings.swipeUpAction, settings.tripleTapAction, settings.pinchAction, settings.dockTapAction, settings.customGestureAction).any { it == GestureAction.LOCK_SCREEN }) {
                            Spacer(Modifier.height(6.dp))
                            if (!adminEnabled.value) {
                                ActionBtn(stringResource(R.string.enable_lock_screen), stringResource(R.string.requires_device_admin), colors) { vm.requestDeviceAdmin(); adminEnabled.value = vm.isDeviceAdminEnabled() }
                            } else {
                                Text(stringResource(R.string.device_admin_enabled), color = colors.accent, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }

                }
                // ── FEATURES ──
                if (showCustomGestureRecorder) {
                    CustomGestureRecorderDialog(
                        currentPattern = settings.customGesturePattern,
                        colors = colors,
                        onSave = { pattern ->
                            vm.saveCustomGesturePattern(pattern)
                            showCustomGestureRecorder = false
                        },
                        onClear = {
                            vm.clearCustomGesturePattern()
                            showCustomGestureRecorder = false
                        },
                        onDismiss = { showCustomGestureRecorder = false },
                    )
                }
                if (showFeatures) {
                SectionHeader(stringResource(R.string.features), searching || featuresExpanded, colors) { featuresExpanded = !featuresExpanded }
                AnimatedVisibility(searching || featuresExpanded) {
                    Column {
                        Tog(stringResource(R.string.smartspace), settings.showClock, colors) { vm.setShowClock(it) }
                        if (settings.showClock) {
                            Lbl(stringResource(R.string.clock_style), colors)
                            Chips(ClockStyle.entries.map { it to it.localizedLabel() }, settings.clockStyle, colors) { vm.setClockStyle(it) }
                        }
                        Tog(stringResource(R.string.auto_place_new_apps), settings.autoPlaceNew, colors) { vm.setAutoPlaceNew(it) }
                        Tog(stringResource(R.string.notification_badges), settings.showNotifBadges, colors) { vm.setShowNotifBadges(it) }
                        if (settings.showNotifBadges) {
                            val notifConnected = remember { mutableStateOf(vm.isNotificationAccessGranted()) }
                            if (!notifConnected.value) {
                                Spacer(Modifier.height(4.dp))
                                ActionBtn(stringResource(R.string.grant_notification_access), stringResource(R.string.required_for_badges), colors) {
                                    vm.openNotificationAccess()
                                    notifConnected.value = vm.isNotificationAccessGranted()
                                }
                            } else {
                                Spacer(Modifier.height(4.dp))
                                Text(stringResource(R.string.notification_access_granted), color = colors.accent, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            }
                            Spacer(Modifier.height(4.dp))
                        }
                        Tog(stringResource(R.string.hide_status_bar), settings.hideStatusBar, colors) { vm.setHideStatusBar(it) }
                        Tog(stringResource(R.string.lock_home_screen), settings.homeLocked, colors) { vm.setHomeLocked(it) }
                        Tog(stringResource(R.string.wallpaper_parallax), settings.wallpaperParallax, colors) { vm.setWallpaperParallax(it) }

                        Lbl(stringResource(R.string.haptic_feedback), colors)
                        Chips(HapticLevel.entries.map { it to it.localizedLabel() }, settings.hapticLevel, colors) { vm.setHapticLevel(it) }
                        Spacer(Modifier.height(8.dp))
                    }
                }

                }
                // ── ADVANCED ──
                if (showAdvanced) {
                SectionHeader(stringResource(R.string.advanced), searching || advancedExpanded, colors) { advancedExpanded = !advancedExpanded }
                AnimatedVisibility(searching || advancedExpanded) {
                    Column {
                        Lbl(stringResource(R.string.quick_actions), colors)
                        ActionBtn(stringResource(R.string.kill_background_apps), stringResource(R.string.free_memory), colors) { vm.killBackgroundApps() }
                        Spacer(Modifier.height(8.dp))
                        ActionBtn(stringResource(R.string.clear_search_history), stringResource(R.string.remove_saved_searches), colors) { vm.clearSearchHistory() }
                        Spacer(Modifier.height(8.dp))
                        var showResetConfirm by remember { mutableStateOf(false) }
                        ActionBtn(stringResource(R.string.reset_all_settings), stringResource(R.string.restore_defaults), colors) { showResetConfirm = true }
                        if (showResetConfirm) {
                            androidx.compose.ui.window.Dialog(onDismissRequest = { showResetConfirm = false }) {
                                Column(Modifier.clip(RoundedCornerShape(20.dp)).background(colors.surface)
                                    .border(0.5.dp, colors.border, RoundedCornerShape(20.dp)).padding(24.dp)) {
                                    Text(stringResource(R.string.reset_all_settings), color = colors.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                                    Spacer(Modifier.height(10.dp))
                                    Text(stringResource(R.string.reset_all_settings_body), color = colors.textSecondary, fontSize = 14.sp)
                                    Spacer(Modifier.height(18.dp))
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                        TextButton(onClick = { showResetConfirm = false }) { Text(stringResource(R.string.cancel), color = colors.textSecondary) }
                                        Spacer(Modifier.width(8.dp))
                                        Button(onClick = { showResetConfirm = false; vm.resetAllSettings() },
                                            colors = ButtonDefaults.buttonColors(containerColor = colors.error),
                                            shape = RoundedCornerShape(12.dp)) { Text(stringResource(R.string.reset), color = Color.White) }
                                    }
                                }
                            }
                        }

                        Lbl(stringResource(R.string.backup_restore), colors)
                        Text(stringResource(R.string.private_data_backup_note), color = colors.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
                        Tog(stringResource(R.string.include_search_history), includeBackupSearchHistory, colors) { includeBackupSearchHistory = it }
                        Tog(stringResource(R.string.include_usage_recents), includeBackupUsage, colors) { includeBackupUsage = it }
                        Tog(stringResource(R.string.include_hidden_apps), includeBackupHiddenApps, colors) { includeBackupHiddenApps = it }
                        val privateSummary = stringResource(if (includeBackupSearchHistory || includeBackupUsage || includeBackupHiddenApps) R.string.with_selected_private_data else R.string.private_data_excluded)
                        Spacer(Modifier.height(8.dp))
                        ActionBtn(stringResource(R.string.export_layout), privateSummary, colors) {
                            pendingBackupOptions = BackupExportOptions(
                                includeSearchHistory = includeBackupSearchHistory,
                                includeAppUsage = includeBackupUsage,
                                includeHiddenApps = includeBackupHiddenApps,
                            )
                            exportLauncher.launch("lawnchair-lite-backup.json")
                        }
                        Spacer(Modifier.height(8.dp))
                        ActionBtn(stringResource(R.string.restore_layout), stringResource(R.string.omitted_private_data_kept), colors) { importLauncher.launch(arrayOf("application/json", "*/*")) }
                        val preview = restorePreview
                        val restoreJson = pendingRestoreJson
                        if (preview != null) {
                            RestorePreviewDialog(
                                preview = preview,
                                colors = colors,
                                onDismiss = { restorePreview = null; pendingRestoreJson = null },
                                onConfirm = if (restoreJson != null && preview.canImport) {
                                    {
                                        scope.launch {
                                            val ok = vm.importBackup(restoreJson)
                                            if (ok) {
                                                restorePreview = null
                                                pendingRestoreJson = null
                                            }
                                        }
                                    }
                                } else null,
                            )
                        }

                        Lbl(stringResource(R.string.diagnostics), colors)
                        ActionBtn(stringResource(R.string.copy_support_bundle), stringResource(R.string.crash_reports_count, diagnosticReports.size), colors) { vm.copyDiagnosticReport() }
                        Spacer(Modifier.height(8.dp))
                        ActionBtn(stringResource(R.string.share_support_bundle), stringResource(R.string.build_device_crash_history), colors) { vm.shareDiagnosticReport() }
                        Spacer(Modifier.height(8.dp))
                        if (diagnosticReports.isEmpty()) {
                            Text(stringResource(R.string.no_saved_crash_reports), color = colors.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp))
                        } else {
                            diagnosticReports.forEach { report ->
                                DiagnosticReportRow(
                                    report = report,
                                    colors = colors,
                                    onCopy = { vm.copyDiagnosticReport(report.fileName) },
                                    onShare = { vm.shareDiagnosticReport(report.fileName) },
                                    onDelete = {
                                        vm.deleteDiagnosticReport(report.fileName)
                                        diagnosticReports = vm.diagnosticReports()
                                    },
                                )
                                Spacer(Modifier.height(6.dp))
                            }
                        }

                        Lbl(stringResource(R.string.permissions), colors)
                        Text(stringResource(R.string.permissions_degraded_note), color = colors.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
                        key(permissionRefresh) {
                            val notificationsOk = vm.areCrashNotificationsEnabled()
                            val notificationAccessGranted = vm.isNotificationAccessGranted()
                            val contactGranted = vm.hasContactPermission()
                            val calendarGranted = vm.hasCalendarPermission()
                            val locationGranted = vm.hasLocationPermission()
                            PermissionStatusRow(
                                title = stringResource(R.string.all_apps_visibility),
                                status = stringResource(R.string.declared),
                                description = stringResource(R.string.all_apps_visibility_desc),
                                c = colors,
                            )
                            PermissionStatusRow(
                                title = stringResource(R.string.widget_binding),
                                status = stringResource(R.string.system_prompt),
                                description = stringResource(R.string.widget_binding_desc),
                                c = colors,
                            )
                            PermissionStatusRow(
                                title = stringResource(R.string.crash_notifications),
                                status = stringResource(if (notificationsOk) R.string.allowed else R.string.blocked),
                                description = stringResource(R.string.crash_notifications_desc),
                                c = colors,
                                actionLabel = if (notificationsOk) null else stringResource(R.string.allow),
                                onAction = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    else vm.openAppNotificationSettings()
                                },
                            )
                            PermissionStatusRow(
                                title = stringResource(R.string.notification_badges),
                                status = stringResource(if (notificationAccessGranted) R.string.granted else R.string.needs_access),
                                description = stringResource(R.string.notification_badges_desc),
                                c = colors,
                                actionLabel = if (notificationAccessGranted) null else stringResource(R.string.open),
                                onAction = { vm.openNotificationAccess() },
                            )
                            PermissionStatusRow(
                                title = stringResource(R.string.contacts_search),
                                status = stringResource(if (contactGranted) R.string.granted else R.string.optional),
                                description = stringResource(R.string.contacts_search_desc),
                                c = colors,
                                actionLabel = if (contactGranted) null else stringResource(R.string.grant),
                                onAction = { contactPermissionLauncher.launch(Manifest.permission.READ_CONTACTS) },
                            )
                            PermissionStatusRow(
                                title = stringResource(R.string.calendar_smartspace),
                                status = stringResource(if (calendarGranted) R.string.granted else R.string.optional),
                                description = stringResource(R.string.calendar_smartspace_desc),
                                c = colors,
                                actionLabel = if (calendarGranted) null else stringResource(R.string.grant),
                                onAction = { calendarPermissionLauncher.launch(Manifest.permission.READ_CALENDAR) },
                            )
                            PermissionStatusRow(
                                title = stringResource(R.string.weather_location),
                                status = stringResource(if (locationGranted) R.string.granted else R.string.optional),
                                description = stringResource(R.string.weather_location_desc),
                                c = colors,
                                actionLabel = if (locationGranted) null else stringResource(R.string.grant),
                                onAction = { locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION) },
                            )
                            PermissionStatusRow(
                                title = stringResource(R.string.quick_actions),
                                status = stringResource(R.string.best_effort),
                                description = stringResource(R.string.quick_actions_desc),
                                c = colors,
                            )
                        }
                        Spacer(Modifier.height(8.dp))

                        // Hidden apps
                        Lbl(stringResource(R.string.hidden_apps), colors)
                        val hiddenInfos = remember(hiddenApps, allAppsRaw) { allAppsRaw.filter { it.key in hiddenApps } }
                        if (hiddenInfos.isEmpty()) Text(stringResource(R.string.no_hidden_apps), color = colors.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp))
                        else {
                            if (hiddenInfos.size > 1) {
                                Text(stringResource(R.string.unhide_all_count, hiddenInfos.size), color = colors.accent, fontSize = 12.sp, fontWeight = FontWeight.Medium,
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
                                    Icon(Icons.Default.Visibility, stringResource(R.string.unhide), tint = colors.accent, modifier = Modifier.size(18.dp))
                                }; Spacer(Modifier.height(6.dp))
                            }
                            Text(stringResource(R.string.tap_to_unhide), color = colors.textSecondary, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp))
                        }

                        Lbl(stringResource(R.string.about), colors)
                        Text(stringResource(R.string.about_version, app.lawnchairlite.BuildConfig.VERSION_NAME), color = colors.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(stringResource(R.string.about_body), color = colors.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp, bottom = 32.dp))
                    }
                }
                }

                // No results
                if (searching && !showTheme && !showIcons && !showGrid && !showDrawer && !showDock && !showGestures && !showFeatures && !showAdvanced) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), Alignment.Center) {
                        Text(stringResource(R.string.no_settings_match, settingsSearch), color = colors.textSecondary, fontSize = 13.sp)
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
    val sectionDescription = stringResource(R.string.section_content_description, title)
    val state = stringResource(if (expanded) R.string.expanded else R.string.collapsed)
    val iconDescription = stringResource(if (expanded) R.string.collapse else R.string.expand)
    Row(
        Modifier.fillMaxWidth()
            .padding(top = 16.dp, bottom = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(c.card)
            .border(0.5.dp, if (expanded) c.accent.copy(alpha = 0.3f) else c.border, RoundedCornerShape(12.dp))
            .semantics { contentDescription = sectionDescription; role = Role.Button; stateDescription = state }
            .clickable(role = Role.Button) { onToggle() }
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
            contentDescription = iconDescription,
            tint = if (expanded) c.accent else c.textSecondary,
            modifier = Modifier.size(20.dp),
        )
    }
}

// ── Icon Pack Picker ─────────────────────────────────────────────────

@Composable
private fun DrawerGroupsSection(groups: List<DrawerGroup>, apps: List<AppInfo>, c: LauncherColors, vm: LauncherViewModel) {
    var newGroupName by remember { mutableStateOf("") }
    var expandedGroupId by remember { mutableStateOf<String?>(null) }
    val groupIds = groups.map { it.id }
    LaunchedEffect(groupIds) {
        if (expandedGroupId != null && expandedGroupId !in groupIds) expandedGroupId = groups.firstOrNull()?.id
    }

    Lbl(stringResource(R.string.drawer_groups), c)
    Text(stringResource(R.string.drawer_groups_desc), color = c.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextField(
            value = newGroupName,
            onValueChange = { newGroupName = it.take(40) },
            modifier = Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(10.dp)),
            placeholder = { Text(stringResource(R.string.new_drawer_group_name), color = c.textSecondary, fontSize = 13.sp) },
            colors = TextFieldDefaults.colors(
                focusedTextColor = c.text,
                unfocusedTextColor = c.text,
                cursorColor = c.accent,
                focusedContainerColor = c.card,
                unfocusedContainerColor = c.card,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
        )
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.create_group), color = if (newGroupName.isBlank()) c.textSecondary else c.accent, fontSize = 12.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.clip(RoundedCornerShape(8.dp))
                .background(if (newGroupName.isBlank()) c.card else c.accent.copy(alpha = 0.12f))
                .clickable {
                    vm.addDrawerGroup(newGroupName)
                    newGroupName = ""
                }
                .padding(horizontal = 12.dp, vertical = 7.dp))
    }
    Spacer(Modifier.height(8.dp))

    groups.forEach { group ->
        var renameText by remember(group.id, group.name) { mutableStateOf(group.name) }
        var prefixText by remember(group.id) { mutableStateOf("") }
        val expanded = expandedGroupId == group.id
        val assignedApps = apps.count { it.key in group.appKeys }
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.card)
                .border(0.5.dp, c.border, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(group.name, color = if (group.enabled) c.accent else c.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.drawer_group_assignment_count, assignedApps, group.packagePrefixes.size), color = c.textSecondary, fontSize = 11.sp)
                }
                Switch(
                    checked = group.enabled,
                    onCheckedChange = { vm.setDrawerGroupEnabled(group.id, it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = c.accent,
                        uncheckedThumbColor = c.textSecondary,
                        uncheckedTrackColor = c.surface,
                        uncheckedBorderColor = c.border,
                    ),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(if (expanded) R.string.close else R.string.edit), color = c.accent, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(c.accent.copy(alpha = 0.1f))
                        .clickable { expandedGroupId = if (expanded) null else group.id }.padding(horizontal = 10.dp, vertical = 5.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.remove), color = c.error, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(c.error.copy(alpha = 0.1f))
                        .clickable { vm.removeDrawerGroup(group.id) }.padding(horizontal = 10.dp, vertical = 5.dp))
            }

            AnimatedVisibility(expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column(Modifier.padding(top = 10.dp)) {
                    TextField(
                        value = renameText,
                        onValueChange = { renameText = it.take(40) },
                        modifier = Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(10.dp)),
                        placeholder = { Text(stringResource(R.string.drawer_group_name), color = c.textSecondary, fontSize = 13.sp) },
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = c.text,
                            unfocusedTextColor = c.text,
                            cursorColor = c.accent,
                            focusedContainerColor = c.surface,
                            unfocusedContainerColor = c.surface,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                    )
                    if (renameText != group.name) {
                        Text(stringResource(R.string.save), color = c.accent, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(top = 6.dp).clip(RoundedCornerShape(8.dp)).background(c.accent.copy(alpha = 0.12f))
                                .clickable { vm.renameDrawerGroup(group.id, renameText) }.padding(horizontal = 12.dp, vertical = 7.dp))
                    }

                    Text(stringResource(R.string.package_prefixes), color = c.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.padding(top = 14.dp, bottom = 8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextField(
                            value = prefixText,
                            onValueChange = { prefixText = it.take(120) },
                            modifier = Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(10.dp)),
                            placeholder = { Text(stringResource(R.string.package_prefix_hint), color = c.textSecondary, fontSize = 13.sp) },
                            colors = TextFieldDefaults.colors(
                                focusedTextColor = c.text,
                                unfocusedTextColor = c.text,
                                cursorColor = c.accent,
                                focusedContainerColor = c.surface,
                                unfocusedContainerColor = c.surface,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.add_prefix), color = if (prefixText.isBlank()) c.textSecondary else c.accent, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (prefixText.isBlank()) c.surface else c.accent.copy(alpha = 0.12f))
                                .clickable {
                                    vm.addDrawerGroupPrefix(group.id, prefixText)
                                    prefixText = ""
                                }
                                .padding(horizontal = 12.dp, vertical = 7.dp))
                    }
                    if (group.packagePrefixes.isNotEmpty()) {
                        Row(Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            group.packagePrefixes.forEach { prefix ->
                                Text(prefix, color = c.accent, fontSize = 11.sp, fontWeight = FontWeight.Medium,
                                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(c.accent.copy(alpha = 0.1f))
                                        .clickable { vm.removeDrawerGroupPrefix(group.id, prefix) }.padding(horizontal = 10.dp, vertical = 5.dp))
                            }
                        }
                    }

                    Text(stringResource(R.string.group_apps), color = c.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.padding(top = 14.dp, bottom = 8.dp))
                    Column(Modifier.fillMaxWidth().heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
                        apps.sortedBy { it.label.lowercase() }.forEach { app ->
                            val assigned = app.key in group.appKeys
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        if (assigned) vm.removeAppFromDrawerGroup(group.id, app.key)
                                        else vm.addAppToDrawerGroup(group.id, app.key)
                                    }
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (app.icon != null) {
                                    Image(rememberDrawablePainter(app.icon), null, Modifier.size(24.dp).clip(RoundedCornerShape(6.dp)))
                                    Spacer(Modifier.width(8.dp))
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(app.label, color = c.text, fontSize = 12.sp, maxLines = 1)
                                    Text(app.packageName, color = c.textSecondary, fontSize = 10.sp, maxLines = 1)
                                }
                                Text(stringResource(if (assigned) R.string.remove_from_group else R.string.add_to_group), color = if (assigned) c.error else c.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun CategoryRulesSection(rules: List<AppCategoryRule>, c: LauncherColors, vm: LauncherViewModel) {
    var type by remember { mutableStateOf(CategoryRuleType.APP_NAME_REGEX) }
    var pattern by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(DrawerCategory.TOOLS) }
    val categories = DrawerCategory.entries.filter { it != DrawerCategory.ALL }
    val placeholder = when (type) {
        CategoryRuleType.APP_NAME_REGEX -> "mail|docs|work"
        CategoryRuleType.PACKAGE_PREFIX -> "com.google.android"
        CategoryRuleType.INSTALL_SOURCE -> "com.android.vending"
    }

    Lbl(stringResource(R.string.category_rules), c)
    if (rules.isEmpty()) {
        Text(stringResource(R.string.category_rules_desc), color = c.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
    } else {
        rules.forEachIndexed { index, rule ->
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.card)
                    .border(0.5.dp, c.border, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(rule.category.localizedLabel(), color = c.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.category_rule_pattern, rule.type.localizedLabel(), rule.pattern), color = c.textSecondary, fontSize = 11.sp, maxLines = 1)
                    }
                    Switch(
                        checked = rule.enabled,
                        onCheckedChange = { vm.setCategoryRuleEnabled(index, it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = c.accent,
                            uncheckedThumbColor = c.textSecondary,
                            uncheckedTrackColor = c.surface,
                            uncheckedBorderColor = c.border,
                        ),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.remove), color = c.error, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(c.error.copy(alpha = 0.1f))
                            .clickable { vm.removeCategoryRule(index) }.padding(horizontal = 10.dp, vertical = 5.dp))
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }

    Chips(CategoryRuleType.entries.map { it to it.localizedLabel() }, type, c) { type = it }
    Spacer(Modifier.height(8.dp))
    TextField(
        value = pattern,
        onValueChange = { pattern = it.take(120) },
        modifier = Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(10.dp)),
        placeholder = { Text(placeholder, color = c.textSecondary, fontSize = 13.sp) },
        colors = TextFieldDefaults.colors(
            focusedTextColor = c.text,
            unfocusedTextColor = c.text,
            cursorColor = c.accent,
            focusedContainerColor = c.card,
            unfocusedContainerColor = c.card,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
    )
    Spacer(Modifier.height(8.dp))
    Chips(categories.map { it to it.localizedLabel() }, category, c) { category = it }
    Spacer(Modifier.height(8.dp))
    Text(stringResource(R.string.add_rule), color = if (pattern.isBlank()) c.textSecondary else c.accent, fontSize = 12.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.clip(RoundedCornerShape(8.dp))
            .background(if (pattern.isBlank()) c.card else c.accent.copy(alpha = 0.12f))
            .clickable {
                vm.addCategoryRule(type, pattern, category)
                pattern = ""
            }.padding(horizontal = 12.dp, vertical = 7.dp))
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun IconPackSection(
    settings: LauncherSettings, packs: List<IconPackInfo>, loading: Boolean,
    c: LauncherColors, vm: LauncherViewModel,
) {
    var expanded by remember { mutableStateOf(false) }
    val activeChain = sanitizeIconPackChain(settings.iconPacks.ifEmpty { listOf(settings.iconPack) })
    val primaryPack = activeChain.firstOrNull().orEmpty()
    val primaryLabel = packs.find { it.packageName == primaryPack }?.label ?: primaryPack.substringAfterLast(".")
    val activeLabel = when {
        primaryPack.isBlank() -> stringResource(R.string.system_default)
        activeChain.size == 1 -> primaryLabel
        else -> "$primaryLabel - ${stringResource(R.string.icon_pack_mixer_count, activeChain.size)}"
    }

    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.card)
            .border(0.5.dp, if (activeChain.isNotEmpty()) c.accent.copy(alpha = 0.4f) else c.border, RoundedCornerShape(12.dp))
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
            Text(stringResource(R.string.active_pack), color = c.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(activeLabel, color = if (activeChain.isNotEmpty()) c.accent else c.textSecondary, fontSize = 12.sp)
        }
        if (activeChain.isNotEmpty()) {
            Text(stringResource(R.string.reset), color = c.error, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(c.error.copy(alpha = 0.1f))
                    .clickable { vm.clearIconPack(); expanded = false }.padding(horizontal = 10.dp, vertical = 4.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(stringResource(if (expanded) R.string.close else R.string.browse), color = c.accent, fontSize = 12.sp, fontWeight = FontWeight.Medium,
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
                    Text(stringResource(R.string.no_icon_packs_found), color = c.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.install_icon_packs_from_play_store), color = c.textSecondary, fontSize = 11.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.refresh), color = c.accent, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(c.accent.copy(alpha = 0.12f))
                            .clickable { vm.refreshIconPacks() }.padding(horizontal = 14.dp, vertical = 6.dp))
                }
            } else {
                packs.forEach { pack ->
                    val isPrimary = pack.packageName == primaryPack
                    val inMixer = pack.packageName in activeChain
                    // Preview icons (loaded async to avoid blocking compose thread)
                    var previewIcons by remember { mutableStateOf<List<android.graphics.drawable.Drawable?>>(emptyList()) }
                    LaunchedEffect(pack.packageName) { vm.getIconPackPreviewAsync(pack.packageName) { previewIcons = it } }
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .background(if (inMixer) c.accent.copy(alpha = 0.1f) else c.card)
                            .border(0.5.dp, if (inMixer) c.accent.copy(alpha = 0.4f) else c.border, RoundedCornerShape(10.dp))
                            .clickable {
                                vm.setIconPackChain(listOf(pack.packageName) + activeChain.filterNot { it == pack.packageName })
                            }
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
                            Text(pack.label, color = if (inMixer) c.accent else c.text, fontSize = 13.sp, fontWeight = if (isPrimary) FontWeight.Bold else FontWeight.Medium)
                            Text(pack.packageName, color = c.textSecondary, fontSize = 10.sp, maxLines = 1)
                            if (inMixer) {
                                Text(
                                    stringResource(if (isPrimary) R.string.primary_pack else R.string.fallback_pack),
                                    color = c.textSecondary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
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
                        Column(horizontalAlignment = Alignment.End) {
                            if (isPrimary) {
                                Box(Modifier.size(8.dp).clip(CircleShape).background(c.accent))
                            } else if (inMixer) {
                                Text(stringResource(R.string.make_primary), color = c.accent, fontSize = 11.sp, fontWeight = FontWeight.Medium,
                                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(c.accent.copy(alpha = 0.12f))
                                        .clickable {
                                            vm.setIconPackChain(listOf(pack.packageName) + activeChain.filterNot { it == pack.packageName })
                                        }.padding(horizontal = 8.dp, vertical = 4.dp))
                            } else {
                                val canAdd = activeChain.size < 6
                                Text(stringResource(R.string.add), color = if (canAdd) c.accent else c.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium,
                                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (canAdd) c.accent.copy(alpha = 0.12f) else c.border.copy(alpha = 0.18f))
                                        .then(if (canAdd) Modifier.clickable { vm.setIconPackChain(activeChain + pack.packageName) } else Modifier)
                                        .padding(horizontal = 8.dp, vertical = 4.dp))
                            }
                            if (inMixer) {
                                Spacer(Modifier.height(6.dp))
                                Text(stringResource(R.string.remove), color = c.error, fontSize = 11.sp, fontWeight = FontWeight.Medium,
                                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(c.error.copy(alpha = 0.1f))
                                        .clickable { vm.setIconPackChain(activeChain.filterNot { it == pack.packageName }) }
                                        .padding(horizontal = 8.dp, vertical = 4.dp))
                            }
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
    val selectedState = stringResource(R.string.selected)
    val notSelectedState = stringResource(R.string.not_selected)
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        opts.forEach { (v, l) -> val s = v == sel
            Box(Modifier.clip(RoundedCornerShape(10.dp)).background(if (s) c.accent.copy(alpha = 0.15f) else c.card)
                .border(0.5.dp, if (s) c.accent.copy(alpha = 0.4f) else c.border, RoundedCornerShape(10.dp))
                .semantics { contentDescription = l; role = Role.RadioButton; selected = s; stateDescription = if (s) selectedState else notSelectedState }
                .clickable(role = Role.RadioButton) { onSel(v) }.padding(horizontal = 16.dp, vertical = 8.dp))
            { Text(l, color = if (s) c.accent else c.textSecondary, fontSize = 13.sp, fontWeight = if (s) FontWeight.Bold else FontWeight.Normal) }
        }
    }
}

@Composable private fun Tog(label: String, value: Boolean, c: LauncherColors, onChange: (Boolean) -> Unit) {
    val state = stringResource(if (value) R.string.on else R.string.off)
    Row(Modifier.fillMaxWidth().semantics { contentDescription = label; role = Role.Switch; stateDescription = state }.clickable(role = Role.Switch) { onChange(!value) }.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Switch(checked = value, onCheckedChange = { onChange(it) }, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = c.accent, uncheckedThumbColor = c.textSecondary, uncheckedTrackColor = c.card, uncheckedBorderColor = c.border))
    }
    HorizontalDivider(color = c.border.copy(alpha = 0.3f), thickness = 0.5.dp)
}

@Composable private fun ActionBtn(label: String, sub: String, c: LauncherColors, onClick: () -> Unit) {
    val description = stringResource(R.string.button_with_subtitle_content_description, label, sub)
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.card).border(0.5.dp, c.border, RoundedCornerShape(12.dp)).semantics { contentDescription = description; role = Role.Button }.clickable(role = Role.Button) { onClick() }.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.Medium); Spacer(Modifier.weight(1f)); Text(sub, color = c.accent, fontSize = 12.sp)
    }
}

@Composable private fun PermissionStatusRow(
    title: String,
    status: String,
    description: String,
    c: LauncherColors,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = c.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(8.dp))
                Text(status, color = c.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Text(description, color = c.textSecondary, fontSize = 11.sp, lineHeight = 15.sp)
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.width(10.dp))
            Text(
                actionLabel,
                color = c.accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(8.dp))
                    .background(c.accent.copy(alpha = 0.12f))
                    .clickable { onAction() }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
    HorizontalDivider(color = c.border.copy(alpha = 0.22f), thickness = 0.5.dp)
}

@Composable private fun RestorePreviewDialog(
    preview: BackupImportPreview,
    colors: LauncherColors,
    onDismiss: () -> Unit,
    onConfirm: (() -> Unit)?,
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.clip(RoundedCornerShape(20.dp)).background(colors.surface)
                .border(0.5.dp, colors.border, RoundedCornerShape(20.dp))
                .padding(24.dp),
        ) {
            Text(stringResource(R.string.review_restore), color = colors.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.restore_schema_version, preview.schemaVersion, preview.appVersion?.let { stringResource(R.string.version_suffix, it) } ?: ""),
                color = colors.textSecondary,
                fontSize = 12.sp,
            )
            if (preview.migrationSource != null) {
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.migration_source, preview.migrationSource), color = colors.textSecondary, fontSize = 12.sp, lineHeight = 16.sp)
            }
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.sections), color = colors.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Text(preview.sectionSummary, color = colors.text, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 4.dp))
            if (preview.privateSections.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.private_data_list, preview.privateSections.joinToString(", ")), color = colors.textSecondary, fontSize = 12.sp, lineHeight = 16.sp)
            }
            if (preview.omittedPrivateSections.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.omitted_private_sections_list, preview.omittedPrivateSections.joinToString(", ")), color = colors.textSecondary, fontSize = 12.sp, lineHeight = 16.sp)
            }
            if (preview.migrationUnsupported.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.unsupported_migration_items, preview.migrationUnsupported.take(5).joinToString(", ")), color = colors.textSecondary, fontSize = 12.sp, lineHeight = 16.sp)
            }
            if (preview.unknownFields.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.unknown_fields_ignored, preview.unknownFields.take(5).joinToString(", ")), color = colors.textSecondary, fontSize = 12.sp, lineHeight = 16.sp)
            }
            if (preview.skippedFields.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.invalid_values_skipped, preview.skippedFields.take(5).joinToString(", ")), color = colors.textSecondary, fontSize = 12.sp, lineHeight = 16.sp)
            }
            val message = preview.error ?: preview.warning
            if (message != null) {
                Spacer(Modifier.height(10.dp))
                Text(message, color = if (preview.error != null) colors.error else colors.accent, fontSize = 12.sp, lineHeight = 16.sp)
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = colors.textSecondary) }
                if (onConfirm != null) {
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = onConfirm,
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                        shape = RoundedCornerShape(12.dp),
                    ) { Text(stringResource(R.string.restore), color = Color.White) }
                }
            }
        }
    }
}

@Composable private fun CustomGestureRecorderDialog(
    currentPattern: String,
    colors: LauncherColors,
    onSave: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    var points by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var pattern by remember(currentPattern) { mutableStateOf(sanitizeCustomGesturePattern(currentPattern)) }
    val ready = pattern.isNotBlank()

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.widthIn(min = 280.dp, max = 340.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(colors.surface)
                .border(0.5.dp, colors.border, RoundedCornerShape(20.dp))
                .padding(18.dp),
        ) {
            Text(stringResource(R.string.draw_gesture_title), color = colors.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.draw_gesture_hint), color = colors.textSecondary, fontSize = 12.sp, lineHeight = 16.sp)
            Spacer(Modifier.height(14.dp))
            Box(
                Modifier.fillMaxWidth().height(220.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.card)
                    .border(0.5.dp, colors.border, RoundedCornerShape(16.dp)),
            ) {
                Canvas(
                    Modifier.fillMaxSize().pointerInput(Unit) {
                        awaitEachGesture {
                            val captured = mutableListOf<Offset>()
                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            captured += down.position
                            points = captured.toList()
                            do {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull() ?: continue
                                if (change.pressed) {
                                    captured += change.position
                                    points = captured.toList()
                                    change.consume()
                                }
                            } while (event.changes.any { it.pressed })
                            pattern = encodeCustomGesture(captured.map { CustomGesturePoint(it.x, it.y) })
                        }
                    },
                ) {
                    points.zipWithNext().forEach { (start, end) ->
                        drawLine(
                            color = colors.accent,
                            start = start,
                            end = end,
                            strokeWidth = 8f,
                            cap = StrokeCap.Round,
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(if (ready) R.string.gesture_recording_ready else R.string.gesture_too_short),
                color = if (ready) colors.accent else colors.textSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            )
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (currentPattern.isNotBlank()) {
                    TextButton(onClick = onClear) { Text(stringResource(R.string.clear), color = colors.error) }
                    Spacer(Modifier.width(8.dp))
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = colors.textSecondary) }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { onSave(pattern) },
                    enabled = ready,
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                    shape = RoundedCornerShape(12.dp),
                ) { Text(stringResource(R.string.save_gesture), color = Color.White) }
            }
        }
    }
}

@Composable private fun DiagnosticReportRow(
    report: DiagnosticReportSummary,
    colors: LauncherColors,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.card)
            .border(0.5.dp, colors.border, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(report.title, color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Text(stringResource(R.string.bytes_count, report.bytes), color = colors.textSecondary, fontSize = 11.sp)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallDiagnosticAction(stringResource(R.string.copy), colors, onCopy)
            SmallDiagnosticAction(stringResource(R.string.share), colors, onShare)
            SmallDiagnosticAction(stringResource(R.string.delete), colors, onDelete, destructive = true)
        }
    }
}

@Composable private fun SmallDiagnosticAction(label: String, colors: LauncherColors, onClick: () -> Unit, destructive: Boolean = false) {
    val tint = if (destructive) colors.error else colors.accent
    Text(
        label,
        color = tint,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(8.dp))
            .background(tint.copy(alpha = 0.12f))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

@Composable private fun AssistantReplacementPicker(currentAppKey: String, apps: List<AppInfo>, c: LauncherColors, vm: LauncherViewModel) {
    var expanded by remember { mutableStateOf(false) }
    val sortedApps = remember(apps) { apps.sortedBy { it.label.lowercase() } }
    val currentApp = currentAppKey.takeIf { it.isNotBlank() }?.let { vm.resolveApp(it) }
    val currentLabel = currentApp?.label ?: stringResource(R.string.system_assistant)

    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(c.card)
                .border(0.5.dp, c.border, RoundedCornerShape(10.dp))
                .clickable { expanded = !expanded }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.assistant_replacement), color = c.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            currentApp?.icon?.let { icon ->
                Image(rememberDrawablePainter(icon), null, Modifier.size(18.dp).clip(RoundedCornerShape(4.dp)))
                Spacer(Modifier.width(6.dp))
            }
            Text(currentLabel, color = c.accent, fontSize = 13.sp)
        }
        Text(
            stringResource(R.string.assistant_replacement_desc),
            color = c.textSecondary,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
        )
        AnimatedVisibility(expanded) {
            Column(Modifier.padding(start = 16.dp, top = 4.dp).heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (currentAppKey.isBlank()) c.accent.copy(alpha = 0.1f) else Color.Transparent)
                        .clickable { vm.setAssistantApp(""); expanded = false }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.system_assistant),
                        color = if (currentAppKey.isBlank()) c.accent else c.text,
                        fontSize = 12.sp,
                        fontWeight = if (currentAppKey.isBlank()) FontWeight.Bold else FontWeight.Normal,
                    )
                }
                sortedApps.forEach { app ->
                    val selected = app.key == currentAppKey
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) c.accent.copy(alpha = 0.1f) else Color.Transparent)
                            .clickable { vm.setAssistantApp(app.key); expanded = false }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (app.icon != null) {
                            Image(rememberDrawablePainter(app.icon), null, Modifier.size(24.dp).clip(RoundedCornerShape(6.dp)))
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            app.label,
                            color = if (selected) c.accent else c.text,
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable private fun GesturePicker(label: String, current: GestureAction, c: LauncherColors, vm: LauncherViewModel? = null, gestureSource: String = "", onChange: (GestureAction) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var showAppPicker by remember { mutableStateOf(false) }
    val currentAppKey = if (vm != null && gestureSource.isNotBlank()) vm.getGestureApp(gestureSource) else ""
    val currentAppLabel = if (current == GestureAction.LAUNCH_APP && currentAppKey.isNotBlank()) {
        vm?.resolveApp(currentAppKey)?.label ?: stringResource(R.string.app_fallback)
    } else current.localizedLabel()

    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(c.card).border(0.5.dp, c.border, RoundedCornerShape(10.dp)).clickable { expanded = !expanded }.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.Medium); Spacer(Modifier.weight(1f))
            if (current == GestureAction.LAUNCH_APP && currentAppKey.isNotBlank()) {
                val gestureAppIcon = vm?.resolveApp(currentAppKey)?.icon
                if (gestureAppIcon != null) { Image(rememberDrawablePainter(gestureAppIcon), null, Modifier.size(18.dp).clip(RoundedCornerShape(4.dp))); Spacer(Modifier.width(6.dp)) }
            }
            Text(if (current == GestureAction.LAUNCH_APP) stringResource(R.string.launch_app_format, currentAppLabel) else current.localizedLabel(), color = c.accent, fontSize = 13.sp)
        }
        AnimatedVisibility(expanded) {
            Column(Modifier.padding(start = 8.dp, top = 4.dp)) {
                GestureAction.entries.forEach { action -> val sel = action == current
                    Text(action.localizedLabel(), color = if (sel) c.accent else c.text, fontSize = 13.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
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
                Text(stringResource(R.string.select_app), color = c.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
                allAppsForPicker.forEach { pickApp ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                            .clickable { vm.setGestureApp(gestureSource, pickApp.key); showAppPicker = false; expanded = false }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (pickApp.icon != null) {
                            Image(rememberDrawablePainter(pickApp.icon), null, Modifier.size(24.dp).clip(RoundedCornerShape(6.dp)))
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(pickApp.label, color = c.text, fontSize = 12.sp, maxLines = 1)
                    }
                }
            }
        }
    }
}
