package app.lawnchairlite.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import app.lawnchairlite.LauncherViewModel
import app.lawnchairlite.data.AppInfo
import app.lawnchairlite.data.AppShortcut
import app.lawnchairlite.data.DragSource
import app.lawnchairlite.data.GridCell
import app.lawnchairlite.data.IconShape
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import java.text.SimpleDateFormat
import java.util.*
import android.app.AlarmManager
import android.content.Context
import android.os.BatteryManager

/** Lawnchair Lite v2.4.0 - UI Components */

fun iconClip(shape: IconShape): androidx.compose.ui.graphics.Shape = when (shape) {
    IconShape.CIRCLE -> CircleShape
    IconShape.SQUIRCLE -> RoundedCornerShape(22)
    IconShape.SQUARE -> RoundedCornerShape(14)
    IconShape.TEARDROP -> RoundedCornerShape(topStartPercent = 50, topEndPercent = 50, bottomStartPercent = 50, bottomEndPercent = 14)
}

@Composable
fun AppIconContent(app: AppInfo, shape: IconShape, iconSizeDp: Dp = 50.dp, modifier: Modifier = Modifier, showLabel: Boolean = true, dimmed: Boolean = false, customLabel: String? = null, badgeCount: Int = 0, badgeDotOnly: Boolean = false) {
    val c = LocalLauncherColors.current
    Column(modifier.graphicsLayer(alpha = if (dimmed) 0.25f else 1f), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(iconSizeDp)) {
            Box(Modifier.fillMaxSize().clip(iconClip(shape)).background(c.card), Alignment.Center) {
                if (app.icon != null) Image(rememberDrawablePainter(app.icon), app.label, Modifier.fillMaxSize().padding((iconSizeDp.value * 0.1f).dp))
            }
            if (badgeCount > 0) {
                if (badgeDotOnly) {
                    Box(Modifier.align(Alignment.TopEnd).offset(x = 2.dp, y = (-1).dp).size(10.dp).clip(CircleShape).background(Color(0xFFEF5350)))
                } else {
                    val badgeText = if (badgeCount > 99) "99+" else badgeCount.toString()
                    Box(
                        Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-2).dp)
                            .defaultMinSize(minWidth = 16.dp, minHeight = 16.dp)
                            .clip(RoundedCornerShape(8.dp)).background(Color(0xFFEF5350))
                            .padding(horizontal = 4.dp),
                        Alignment.Center,
                    ) { Text(badgeText, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
                }
            }
        }
        if (showLabel) { Spacer(Modifier.height(3.dp)); Text(customLabel ?: app.label, color = c.text, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 68.dp)) }
    }
}

@Composable
fun TappableAppIcon(app: AppInfo, shape: IconShape, iconSizeDp: Dp = 50.dp, modifier: Modifier = Modifier, showLabel: Boolean = true, customLabel: String? = null, badgeCount: Int = 0, badgeDotOnly: Boolean = false, onClick: () -> Unit = {}, onLongClick: () -> Unit = {}) {
    val c = LocalLauncherColors.current; var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, spring(dampingRatio = 0.6f, stiffness = 500f), label = "s")
    Column(modifier.graphicsLayer(scaleX = scale, scaleY = scale).pointerInput(app.key) { detectTapGestures(onPress = { pressed = true; tryAwaitRelease(); pressed = false }, onTap = { onClick() }, onLongPress = { onLongClick() }) }, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(iconSizeDp)) {
            Box(Modifier.fillMaxSize().clip(iconClip(shape)).background(c.card), Alignment.Center) { if (app.icon != null) Image(rememberDrawablePainter(app.icon), app.label, Modifier.fillMaxSize().padding((iconSizeDp.value * 0.1f).dp)) }
            if (badgeCount > 0) {
                if (badgeDotOnly) {
                    Box(Modifier.align(Alignment.TopEnd).offset(x = 2.dp, y = (-1).dp).size(10.dp).clip(CircleShape).background(Color(0xFFEF5350)))
                } else {
                    val badgeText = if (badgeCount > 99) "99+" else badgeCount.toString()
                    Box(
                        Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-2).dp)
                            .defaultMinSize(minWidth = 16.dp, minHeight = 16.dp)
                            .clip(RoundedCornerShape(8.dp)).background(Color(0xFFEF5350))
                            .padding(horizontal = 4.dp),
                        Alignment.Center,
                    ) { Text(badgeText, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
                }
            }
        }
        if (showLabel) { Spacer(Modifier.height(3.dp)); Text(customLabel ?: app.label, color = c.text, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 68.dp)) }
    }
}

@Composable
fun FolderIconContent(folder: GridCell.Folder, shape: IconShape, resolveApp: (String) -> AppInfo?, iconSizeDp: Dp = 50.dp, modifier: Modifier = Modifier, showLabel: Boolean = true, dimmed: Boolean = false) {
    val c = LocalLauncherColors.current
    Column(modifier.graphicsLayer(alpha = if (dimmed) 0.25f else 1f), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(iconSizeDp).clip(iconClip(shape)).background(c.card).border(0.5.dp, c.accent.copy(alpha = 0.3f), iconClip(shape)), Alignment.Center) {
            val p = folder.appKeys.take(4).mapNotNull { resolveApp(it) }
            if (p.isEmpty()) Icon(Icons.Default.Folder, null, tint = c.accent, modifier = Modifier.size(26.dp))
            else Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                p.chunked(2).take(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        row.forEach { a -> if (a.icon != null) Image(rememberDrawablePainter(a.icon), null, Modifier.size(17.dp).clip(RoundedCornerShape(4.dp))) else Box(Modifier.size(17.dp).clip(RoundedCornerShape(4.dp)).background(c.border)) }
                    }
                }
            }
        }
        if (showLabel) { Spacer(Modifier.height(3.dp)); Text(folder.name, color = c.accent, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 68.dp)) }
    }
}

@Composable
fun DragGhost(cell: GridCell?, app: AppInfo?, shape: IconShape, offset: Offset, resolveApp: (String) -> AppInfo?, sizeDp: Dp = 50.dp) {
    if (cell == null) return; val c = LocalLauncherColors.current; val d = LocalDensity.current; val half = sizeDp / 2
    Box(Modifier.offset(x = with(d) { offset.x.toDp() - half }, y = with(d) { offset.y.toDp() - half }).size(sizeDp + 6.dp).shadow(12.dp, iconClip(shape)).clip(iconClip(shape)).background(c.card).graphicsLayer(alpha = 0.95f, scaleX = 1.2f, scaleY = 1.2f), Alignment.Center) {
        when (cell) {
            is GridCell.App -> if (app?.icon != null) Image(rememberDrawablePainter(app.icon), null, Modifier.fillMaxSize().padding(5.dp))
            is GridCell.Folder -> {
                val p = cell.appKeys.take(4).mapNotNull { resolveApp(it) }
                if (p.isEmpty()) Icon(Icons.Default.Folder, null, tint = c.accent, modifier = Modifier.size(26.dp))
                else Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    p.chunked(2).take(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) { row.forEach { a -> if (a.icon != null) Image(rememberDrawablePainter(a.icon), null, Modifier.size(17.dp).clip(RoundedCornerShape(4.dp))) else Box(Modifier.size(17.dp).clip(RoundedCornerShape(4.dp)).background(c.border)) } }
                    }
                }
            }
        }
    }
}

@Composable
fun RemoveZone(hovering: Boolean, modifier: Modifier = Modifier) {
    val c = LocalLauncherColors.current; val bg by animateColorAsState(if (hovering) Color(0xFFEF5350) else c.surface.copy(alpha = 0.85f), label = "rz")
    val scale by animateFloatAsState(if (hovering) 1.08f else 1f, spring(stiffness = 300f), label = "rzs")
    Box(modifier.fillMaxWidth(0.5f).height(52.dp).graphicsLayer(scaleX = scale, scaleY = scale).background(bg, RoundedCornerShape(bottomStart = 14.dp)), Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Close, null, tint = if (hovering) Color.White else c.textSecondary, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(5.dp)); Text("Remove", color = if (hovering) Color.White else c.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium) }
    }
}

@Composable
fun UninstallZone(hovering: Boolean, isSystemApp: Boolean, modifier: Modifier = Modifier) {
    val c = LocalLauncherColors.current; val bg by animateColorAsState(when { isSystemApp -> c.surface.copy(alpha = 0.5f); hovering -> Color(0xFFD32F2F); else -> c.surface.copy(alpha = 0.85f) }, label = "uz")
    val scale by animateFloatAsState(if (hovering && !isSystemApp) 1.08f else 1f, spring(stiffness = 300f), label = "uzs")
    val t = when { isSystemApp -> c.textSecondary.copy(alpha = 0.4f); hovering -> Color.White; else -> c.textSecondary }
    Box(modifier.fillMaxWidth(1f).height(52.dp).graphicsLayer(scaleX = scale, scaleY = scale).background(bg, RoundedCornerShape(bottomEnd = 14.dp)), Alignment.Center) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Delete, null, tint = t, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(5.dp)); Text(if (isSystemApp) "System App" else "Uninstall", color = t, fontSize = 12.sp, fontWeight = FontWeight.Medium) } }
}

@Composable
fun PageDots(pageCount: Int, currentPage: Int, modifier: Modifier = Modifier) {
    if (pageCount <= 1) return; val c = LocalLauncherColors.current
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        repeat(pageCount) { i ->
            val a = i == currentPage; val s by animateFloatAsState(if (a) 8f else 5f, label = "ds$i"); val al by animateFloatAsState(if (a) 1f else 0.4f, label = "da$i")
            Box(Modifier.padding(horizontal = 3.dp).size(s.dp).clip(CircleShape).graphicsLayer(alpha = al).background(if (a) c.accent else c.textSecondary))
        }
    }
}

@Composable
fun AtAGlanceClock(modifier: Modifier = Modifier, onDateClick: () -> Unit = {}, onTimeClick: () -> Unit = {}) {
    val c = LocalLauncherColors.current
    val context = androidx.compose.ui.platform.LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val is24h = android.text.format.DateFormat.is24HourFormat(context)
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); kotlinx.coroutines.delay(1000L) } }
    val cal = remember(now) { Calendar.getInstance().apply { timeInMillis = now } }
    val dateFmt = remember { SimpleDateFormat("EEE, MMM d", Locale.getDefault()) }
    val dateStr = remember(now / 60000) { dateFmt.format(Date(now)) }

    // Battery level
    val batteryPct = remember(now / 30000) {
        try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        } catch (_: Exception) { -1 }
    }

    // Next alarm
    val nextAlarmStr = remember(now / 60000) {
        try {
            val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            val info = am?.nextAlarmClock
            if (info != null) {
                val alarmCal = Calendar.getInstance().apply { timeInMillis = info.triggerTime }
                val fmt = if (android.text.format.DateFormat.is24HourFormat(context))
                    SimpleDateFormat("EEE HH:mm", Locale.getDefault())
                else SimpleDateFormat("EEE h:mm a", Locale.getDefault())
                fmt.format(alarmCal.time)
            } else null
        } catch (_: Exception) { null }
    }

    Column(modifier.padding(horizontal = 24.dp, vertical = 12.dp)) {
        // Date + info row (tap date -> calendar)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onDateClick() }) {
            Text(dateStr, color = c.text.copy(alpha = 0.7f), fontSize = 14.sp, fontWeight = FontWeight.Medium)
            if (batteryPct in 0..100) {
                Text("  |  ", color = c.textSecondary.copy(alpha = 0.4f), fontSize = 12.sp)
                Icon(Icons.Default.BatteryFull, null, tint = if (batteryPct <= 15) Color(0xFFEF5350) else c.accent.copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
                Text("$batteryPct%", color = c.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }
        if (nextAlarmStr != null) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 1.dp)) {
                Icon(Icons.Default.Alarm, null, tint = c.accent.copy(alpha = 0.5f), modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(4.dp))
                Text(nextAlarmStr, color = c.textSecondary.copy(alpha = 0.7f), fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(2.dp))
        // Tap time -> clock app
        Box(Modifier.clickable { onTimeClick() }) {
            if (is24h) {
                val h = String.format("%02d", cal.get(Calendar.HOUR_OF_DAY)); val m = String.format("%02d", cal.get(Calendar.MINUTE))
                Text("$h:$m", color = c.text, fontSize = 52.sp, fontWeight = FontWeight.Thin, lineHeight = 52.sp)
            } else {
                val hour = cal.get(Calendar.HOUR).let { if (it == 0) 12 else it }; val min = String.format("%02d", cal.get(Calendar.MINUTE)); val ampm = if (cal.get(Calendar.AM_PM) == Calendar.AM) "AM" else "PM"
                Row(verticalAlignment = Alignment.Bottom) { Text("$hour:$min", color = c.text, fontSize = 52.sp, fontWeight = FontWeight.Thin, lineHeight = 52.sp); Spacer(Modifier.width(6.dp)); Text(ampm, color = c.accent, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 9.dp)) }
            }
        }
    }
}

@Composable
fun SearchPill(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalLauncherColors.current
    Row(modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(22.dp)).background(c.searchBg).border(0.5.dp, c.border, RoundedCornerShape(22.dp)).clickable { onClick() }.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(22.dp).clip(CircleShape).background(c.accent.copy(alpha = 0.15f)), Alignment.Center) { Text("G", color = c.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.width(10.dp)); Text("Search\u2026", color = c.textSecondary, fontSize = 14.sp); Spacer(Modifier.weight(1f)); Icon(Icons.Default.Search, null, tint = c.textSecondary, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun DrawerSearch(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalLauncherColors.current
    TextField(value = query, onValueChange = onQueryChange, modifier = modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(25.dp)).background(c.searchBg),
        placeholder = { Text("Search apps\u2026", color = c.textSecondary, fontSize = 14.sp) },
        leadingIcon = { Icon(Icons.Default.Search, null, tint = c.textSecondary, modifier = Modifier.size(18.dp)) },
        colors = TextFieldDefaults.colors(focusedTextColor = c.text, unfocusedTextColor = c.text, cursorColor = c.accent, focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
        singleLine = true, textStyle = TextStyle(fontSize = 14.sp))
}

@Composable
fun FastScrollerRail(letters: List<Char>, onLetterSelected: (Char) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalLauncherColors.current
    Column(modifier.padding(end = 2.dp, top = 8.dp, bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceEvenly) {
        letters.forEach { ch ->
            Text(ch.toString(), color = c.accent, fontSize = 9.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                modifier = Modifier.size(16.dp).clip(CircleShape).clickable { onLetterSelected(ch) }.wrapContentSize(Alignment.Center))
        }
    }
}

// ── Home/Dock Context Menu ───────────────────────────────────────────

@Composable
fun HomeContextMenu(
    menuState: LauncherViewModel.HomeMenuState,
    shape: IconShape,
    vm: LauncherViewModel,
    shortcuts: List<AppShortcut>,
    onDismiss: () -> Unit,
) {
    val c = LocalLauncherColors.current
    val cell = menuState.cell
    val app = menuState.appInfo
    val isFolder = cell is GridCell.Folder
    val sourceLabel = if (menuState.source == DragSource.DOCK) "Dock" else "Home"

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).pointerInput(Unit) { detectTapGestures { onDismiss() } }, Alignment.Center) {
        Column(
            Modifier.widthIn(min = 240.dp, max = 280.dp).clip(RoundedCornerShape(20.dp))
                .background(c.surface).border(0.5.dp, c.border, RoundedCornerShape(20.dp))
                .pointerInput(Unit) { detectTapGestures { } }.padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(8.dp))

            when (cell) {
                is GridCell.App -> if (app != null) {
                    Box(Modifier.size(54.dp).clip(iconClip(shape)).background(c.card), Alignment.Center) {
                        if (app.icon != null) Image(rememberDrawablePainter(app.icon), null, Modifier.fillMaxSize().padding(5.dp))
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(vm.customLabels.collectAsState().value[cell.appKey] ?: app.label, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(app.packageName, color = c.textSecondary, fontSize = 10.sp)
                }
                is GridCell.Folder -> {
                    FolderIconContent(cell, shape, { vm.resolveApp(it) }, 54.dp, showLabel = false)
                    Spacer(Modifier.height(6.dp))
                    Text(cell.name, color = c.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("${cell.appKeys.size} apps", color = c.textSecondary, fontSize = 10.sp)
                }
            }

            // App Shortcuts
            if (shortcuts.isNotEmpty() && cell is GridCell.App) {
                Spacer(Modifier.height(8.dp))
                Divider(color = c.border.copy(alpha = 0.3f), thickness = 0.5.dp)
                shortcuts.forEach { shortcut ->
                    ShortcutItem(shortcut, c) { vm.launchShortcut(shortcut) }
                }
            }

            Spacer(Modifier.height(if (shortcuts.isEmpty()) 12.dp else 4.dp))
            Divider(color = c.border.copy(alpha = 0.3f), thickness = 0.5.dp)

            if (cell is GridCell.App && app != null) {
                CtxItem("Rename", c) { vm.startLabelEdit(cell.appKey) }
                CtxItem("Rearrange Icons", c) { vm.enterEditMode() }
                Divider(color = c.border.copy(alpha = 0.3f), thickness = 0.5.dp)
                CtxItem("App Info", c) { vm.appInfo(app); onDismiss() }
                CtxItem("Remove from $sourceLabel", c) { vm.removeFromGrid(menuState.source, menuState.index) }
                if (!app.isSystemApp) {
                    Divider(color = c.border.copy(alpha = 0.3f), thickness = 0.5.dp)
                    CtxItem("Uninstall", c, isRed = true) { vm.uninstall(app); vm.removeFromGrid(menuState.source, menuState.index) }
                }
            } else if (isFolder) {
                val folder = cell as GridCell.Folder
                CtxItem("Open Folder", c) { vm.openFolderView(folder, menuState.source, menuState.index); onDismiss() }
                CtxItem("Rename Folder", c) { vm.startFolderRename(menuState.source, menuState.index, folder.name); onDismiss() }
                CtxItem("Rearrange Icons", c) { vm.enterEditMode() }
                Divider(color = c.border.copy(alpha = 0.3f), thickness = 0.5.dp)
                CtxItem("Remove from $sourceLabel", c, isRed = true) { vm.removeFromGrid(menuState.source, menuState.index) }
            }
        }
    }
}

// ── Folder Overlay (improved - selective X, better UX) ───────────────

@Composable
fun FolderOverlay(folder: GridCell.Folder, shape: IconShape, iconSizeDp: Dp, resolveApp: (String) -> AppInfo?, customLabels: Map<String, String>, onAppClick: (AppInfo) -> Unit, onRemoveApp: (String) -> Unit, onReorder: (List<String>) -> Unit, onRename: () -> Unit, onDismiss: () -> Unit) {
    val c = LocalLauncherColors.current
    val orderedKeys = remember(folder.appKeys) { mutableStateListOf(*folder.appKeys.toTypedArray()) }
    var editMode by remember { mutableStateOf(false) }
    var dragIdx by remember { mutableIntStateOf(-1) }
    var selectedForRemoval by remember { mutableStateOf<String?>(null) }
    val slotRootBounds = remember { mutableStateMapOf<Int, Rect>() }
    var containerRootOffset by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(orderedKeys.size) { slotRootBounds.keys.filter { it >= orderedKeys.size }.forEach { slotRootBounds.remove(it) } }

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)).pointerInput(Unit) { detectTapGestures { onDismiss() } }, Alignment.Center) {
        Column(Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(20.dp)).background(c.surface).border(0.5.dp, c.accent.copy(alpha = 0.2f), RoundedCornerShape(20.dp)).pointerInput(Unit) { detectTapGestures { } }.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Text(folder.name, color = c.accent, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onRename() }.padding(4.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (editMode) "Done" else "Edit", color = if (editMode) Color(0xFF66BB6A) else c.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (editMode) Color(0xFF66BB6A).copy(alpha = 0.12f) else c.card)
                        .border(0.5.dp, if (editMode) Color(0xFF66BB6A).copy(alpha = 0.3f) else c.border, RoundedCornerShape(8.dp))
                        .clickable { editMode = !editMode; dragIdx = -1; selectedForRemoval = null }.padding(horizontal = 10.dp, vertical = 4.dp))
            }
            Spacer(Modifier.height(14.dp))
            if (orderedKeys.isEmpty()) Text("Empty", color = c.textSecondary, fontSize = 13.sp)
            else {
                Box(
                    Modifier.fillMaxWidth()
                        .onGloballyPositioned { containerRootOffset = it.boundsInRoot().topLeft }
                        .then(if (editMode) Modifier.pointerInput(editMode) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { localPos ->
                                    val rootPos = localPos + containerRootOffset
                                    for ((i, b) in slotRootBounds) { if (b.contains(rootPos)) { dragIdx = i; selectedForRemoval = null; break } }
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    if (dragIdx < 0) return@detectDragGesturesAfterLongPress
                                    val rootPos = change.position + containerRootOffset
                                    for ((i, b) in slotRootBounds) {
                                        if (i != dragIdx && i < orderedKeys.size && b.contains(rootPos)) {
                                            val moving = orderedKeys.removeAt(dragIdx)
                                            orderedKeys.add(i, moving)
                                            dragIdx = i; break
                                        }
                                    }
                                },
                                onDragEnd = { if (dragIdx >= 0) onReorder(orderedKeys.toList()); dragIdx = -1 },
                                onDragCancel = { dragIdx = -1 },
                            )
                        } else Modifier)
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        orderedKeys.chunked(4).forEachIndexed { rowIdx, row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                row.forEachIndexed { colIdx, key ->
                                    val flatIdx = rowIdx * 4 + colIdx
                                    val app = resolveApp(key)
                                    val isBeingDragged = dragIdx == flatIdx
                                    val isSelectedForRemoval = selectedForRemoval == key
                                    Box(
                                        Modifier.width(66.dp)
                                            .onGloballyPositioned { slotRootBounds[flatIdx] = it.boundsInRoot() }
                                            .graphicsLayer(
                                                alpha = if (isBeingDragged) 0.4f else 1f,
                                                scaleX = if (isBeingDragged) 1.15f else 1f,
                                                scaleY = if (isBeingDragged) 1.15f else 1f,
                                            ),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (app != null) {
                                            if (editMode) {
                                                // In edit mode: tap toggles X on that specific icon only
                                                Box(Modifier.pointerInput(key) {
                                                    detectTapGestures(onTap = {
                                                        selectedForRemoval = if (selectedForRemoval == key) null else key
                                                    })
                                                }) {
                                                    AppIconContent(app, shape, iconSizeDp, showLabel = true, customLabel = customLabels[key])
                                                }
                                                // Only show X on the tapped icon
                                                if (isSelectedForRemoval) {
                                                    Box(
                                                        Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-4).dp).size(20.dp)
                                                            .shadow(4.dp, CircleShape).clip(CircleShape).background(Color(0xFFEF5350))
                                                            .clickable { onRemoveApp(key); selectedForRemoval = null },
                                                        Alignment.Center,
                                                    ) { Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(12.dp)) }
                                                }
                                            } else {
                                                TappableAppIcon(app, shape, iconSizeDp, showLabel = true, customLabel = customLabels[key], onClick = { onAppClick(app) }, onLongClick = { editMode = true })
                                            }
                                        }
                                    }
                                }
                                repeat(4 - row.size) { Spacer(Modifier.width(66.dp)) }
                            }; Spacer(Modifier.height(10.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

// ── Drawer Context Menu ──────────────────────────────────────────────

@Composable
fun DrawerContextMenu(app: AppInfo, shape: IconShape, shortcuts: List<AppShortcut>, onShortcutClick: (AppShortcut) -> Unit, onPinHome: () -> Unit, onPinDock: () -> Unit, onHide: () -> Unit, onAppInfo: () -> Unit, onUninstall: () -> Unit, onDismiss: () -> Unit) {
    val c = LocalLauncherColors.current
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).pointerInput(Unit) { detectTapGestures { onDismiss() } }, Alignment.Center) {
        Column(Modifier.widthIn(min = 240.dp, max = 280.dp).clip(RoundedCornerShape(20.dp)).background(c.surface).border(0.5.dp, c.border, RoundedCornerShape(20.dp)).pointerInput(Unit) { detectTapGestures { } }.padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(8.dp))
            Box(Modifier.size(54.dp).clip(iconClip(shape)).background(c.card), Alignment.Center) { if (app.icon != null) Image(rememberDrawablePainter(app.icon), null, Modifier.fillMaxSize().padding(5.dp)) }
            Spacer(Modifier.height(6.dp)); Text(app.label, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold); Text(app.packageName, color = c.textSecondary, fontSize = 10.sp)
            if (shortcuts.isNotEmpty()) {
                Spacer(Modifier.height(8.dp)); Divider(color = c.border.copy(alpha = 0.3f), thickness = 0.5.dp)
                shortcuts.forEach { shortcut -> ShortcutItem(shortcut, c) { onShortcutClick(shortcut) } }
            }
            Spacer(Modifier.height(if (shortcuts.isEmpty()) 12.dp else 4.dp)); Divider(color = c.border.copy(alpha = 0.3f), thickness = 0.5.dp)
            CtxItem("Add to Home Screen", c, onClick = onPinHome); CtxItem("Add to Dock", c, onClick = onPinDock)
            CtxItem("Hide from Drawer", c, onClick = onHide); CtxItem("App Info", c, onClick = onAppInfo)
            if (!app.isSystemApp) { Divider(color = c.border.copy(alpha = 0.3f), thickness = 0.5.dp); CtxItem("Uninstall", c, isRed = true, onClick = onUninstall) }
        }
    }
}

@Composable private fun CtxItem(label: String, c: LauncherColors, isRed: Boolean = false, onClick: () -> Unit) {
    Text(label, color = if (isRed) Color(0xFFEF5350) else c.text, fontSize = 14.sp, fontWeight = if (isRed) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 24.dp, vertical = 13.dp))
}

@Composable private fun ShortcutItem(shortcut: AppShortcut, c: LauncherColors, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (shortcut.icon != null) {
            Image(rememberDrawablePainter(shortcut.icon), null, Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)))
            Spacer(Modifier.width(10.dp))
        } else {
            Icon(Icons.Default.ArrowForward, null, tint = c.accent, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(shortcut.shortLabel.toString(), color = c.text, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun RenameDialog(currentName: String, title: String = "Rename Folder", onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    val c = LocalLauncherColors.current; var name by remember { mutableStateOf(currentName) }
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.clip(RoundedCornerShape(20.dp)).background(c.surface).border(0.5.dp, c.border, RoundedCornerShape(20.dp)).padding(24.dp)) {
            Text(title, color = c.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold); Spacer(Modifier.height(14.dp))
            TextField(value = name, onValueChange = { name = it }, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)), placeholder = { Text("Name", color = c.textSecondary) },
                colors = TextFieldDefaults.colors(focusedTextColor = c.text, unfocusedTextColor = c.text, cursorColor = c.accent, focusedContainerColor = c.card, unfocusedContainerColor = c.card, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent), singleLine = true, textStyle = TextStyle(fontSize = 15.sp))
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Cancel", color = c.textSecondary) }; Spacer(Modifier.width(8.dp))
                Button(onClick = { onConfirm(name.trim()) }, colors = ButtonDefaults.buttonColors(containerColor = c.accent), shape = RoundedCornerShape(12.dp)) { Text("Save", color = Color.White) }
            }
        }
    }
}
