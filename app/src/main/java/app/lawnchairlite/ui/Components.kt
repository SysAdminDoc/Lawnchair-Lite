package app.lawnchairlite.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
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
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.BatteryManager
import android.view.View
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.viewinterop.AndroidView

/** Lawnchair Lite v2.12.0 - UI Components */

private val GrayscaleColorFilter = androidx.compose.ui.graphics.ColorFilter.colorMatrix(
    androidx.compose.ui.graphics.ColorMatrix(
        android.graphics.ColorMatrix().apply { setSaturation(0f) }.array
    )
)

private val HexagonShape = GenericShape { size, _ ->
    val w = size.width; val h = size.height
    moveTo(w * 0.5f, 0f)
    lineTo(w, h * 0.25f)
    lineTo(w, h * 0.75f)
    lineTo(w * 0.5f, h)
    lineTo(0f, h * 0.75f)
    lineTo(0f, h * 0.25f)
    close()
}

private val DiamondShape = GenericShape { size, _ ->
    val w = size.width; val h = size.height
    moveTo(w * 0.5f, h * 0.05f)
    lineTo(w * 0.95f, h * 0.5f)
    lineTo(w * 0.5f, h * 0.95f)
    lineTo(w * 0.05f, h * 0.5f)
    close()
}

fun iconClip(shape: IconShape): androidx.compose.ui.graphics.Shape = when (shape) {
    IconShape.CIRCLE -> CircleShape
    IconShape.SQUIRCLE -> RoundedCornerShape(22)
    IconShape.SQUARE -> RoundedCornerShape(14)
    IconShape.TEARDROP -> RoundedCornerShape(topStartPercent = 50, topEndPercent = 50, bottomStartPercent = 50, bottomEndPercent = 14)
    IconShape.HEXAGON -> HexagonShape
    IconShape.DIAMOND -> DiamondShape
}

@Composable
fun AppIconContent(app: AppInfo, shape: IconShape, iconSizeDp: Dp = 50.dp, modifier: Modifier = Modifier, showLabel: Boolean = true, dimmed: Boolean = false, customLabel: String? = null, badgeCount: Int = 0, badgeDotOnly: Boolean = false, iconShadow: Boolean = false, labelSizeSp: Int = 11, grayscale: Boolean = false, labelWeight: FontWeight = FontWeight.Normal) {
    val c = LocalLauncherColors.current
    Column(modifier.graphicsLayer(alpha = if (dimmed) 0.25f else 1f), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(iconSizeDp).then(if (iconShadow) Modifier.shadow(6.dp, iconClip(shape)) else Modifier)) {
            Box(Modifier.fillMaxSize().clip(iconClip(shape)).background(c.card), Alignment.Center) {
                if (app.icon != null) Image(rememberDrawablePainter(app.icon), app.label, Modifier.fillMaxSize().padding((iconSizeDp.value * 0.1f).dp),
                    colorFilter = if (grayscale) GrayscaleColorFilter else null)
            }
            if (badgeCount > 0) {
                if (badgeDotOnly) {
                    Box(Modifier.align(Alignment.TopEnd).offset(x = 2.dp, y = (-1).dp).size(10.dp).clip(CircleShape).background(c.accent))
                } else {
                    val badgeText = if (badgeCount > 99) "99+" else badgeCount.toString()
                    Box(
                        Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-2).dp)
                            .defaultMinSize(minWidth = 16.dp, minHeight = 16.dp)
                            .clip(RoundedCornerShape(8.dp)).background(c.accent)
                            .padding(horizontal = 4.dp),
                        Alignment.Center,
                    ) { Text(badgeText, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
                }
            }
        }
        if (showLabel) { Spacer(Modifier.height(3.dp)); Text(customLabel ?: app.label, color = c.text, fontSize = labelSizeSp.sp, fontWeight = labelWeight, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 68.dp)) }
    }
}

@Composable
fun TappableAppIcon(app: AppInfo, shape: IconShape, iconSizeDp: Dp = 50.dp, modifier: Modifier = Modifier, showLabel: Boolean = true, customLabel: String? = null, badgeCount: Int = 0, badgeDotOnly: Boolean = false, iconShadow: Boolean = false, grayscale: Boolean = false, labelSizeSp: Int = 11, labelWeight: FontWeight = FontWeight.Normal, onClick: () -> Unit = {}, onLongClick: () -> Unit = {}) {
    val c = LocalLauncherColors.current; var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, spring(dampingRatio = 0.6f, stiffness = 500f), label = "s")
    Column(modifier.graphicsLayer(scaleX = scale, scaleY = scale).pointerInput(app.key) { detectTapGestures(onPress = { pressed = true; tryAwaitRelease(); pressed = false }, onTap = { onClick() }, onLongPress = { onLongClick() }) }, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(iconSizeDp).then(if (iconShadow) Modifier.shadow(6.dp, iconClip(shape)) else Modifier)) {
            Box(Modifier.fillMaxSize().clip(iconClip(shape)).background(c.card), Alignment.Center) {
                if (app.icon != null) Image(rememberDrawablePainter(app.icon), app.label, Modifier.fillMaxSize().padding((iconSizeDp.value * 0.1f).dp),
                    colorFilter = if (grayscale) GrayscaleColorFilter else null)
            }
            if (badgeCount > 0) {
                if (badgeDotOnly) {
                    Box(Modifier.align(Alignment.TopEnd).offset(x = 2.dp, y = (-1).dp).size(10.dp).clip(CircleShape).background(c.accent))
                } else {
                    val badgeText = if (badgeCount > 99) "99+" else badgeCount.toString()
                    Box(
                        Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-2).dp)
                            .defaultMinSize(minWidth = 16.dp, minHeight = 16.dp)
                            .clip(RoundedCornerShape(8.dp)).background(c.accent)
                            .padding(horizontal = 4.dp),
                        Alignment.Center,
                    ) { Text(badgeText, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
                }
            }
        }
        if (showLabel) { Spacer(Modifier.height(3.dp)); Text(customLabel ?: app.label, color = c.text, fontSize = labelSizeSp.sp, fontWeight = labelWeight, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 68.dp)) }
    }
}

@Composable
fun FolderIconContent(folder: GridCell.Folder, shape: IconShape, resolveApp: (String) -> AppInfo?, iconSizeDp: Dp = 50.dp, modifier: Modifier = Modifier, showLabel: Boolean = true, dimmed: Boolean = false, badgeCount: Int = 0) {
    val c = LocalLauncherColors.current
    Column(modifier.graphicsLayer(alpha = if (dimmed) 0.25f else 1f), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(iconSizeDp)) {
            Box(Modifier.fillMaxSize().clip(iconClip(shape)).background(c.card).border(0.5.dp, c.accent.copy(alpha = 0.3f), iconClip(shape)), Alignment.Center) {
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
            if (badgeCount > 0) {
                val badgeText = if (badgeCount > 99) "99+" else badgeCount.toString()
                Box(
                    Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-2).dp)
                        .defaultMinSize(minWidth = 16.dp, minHeight = 16.dp)
                        .clip(RoundedCornerShape(8.dp)).background(c.accent)
                        .padding(horizontal = 4.dp),
                    Alignment.Center,
                ) { Text(badgeText, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
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
            is GridCell.Widget -> Icon(Icons.Default.Widgets, null, tint = c.accent, modifier = Modifier.size(26.dp))
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
fun PageLineIndicator(pageCount: Int, currentPage: Int, modifier: Modifier = Modifier) {
    if (pageCount <= 1) return; val c = LocalLauncherColors.current
    val fraction by animateFloatAsState(if (pageCount > 1) currentPage.toFloat() / (pageCount - 1) else 0f, label = "plf")
    Box(modifier.fillMaxWidth().padding(horizontal = 60.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(c.textSecondary.copy(alpha = 0.12f))) {
        Box(Modifier.fillMaxWidth(1f / pageCount.coerceAtLeast(1)).fillMaxHeight()
            .graphicsLayer { translationX = fraction * size.width * (pageCount - 1).coerceAtLeast(1) / pageCount.coerceAtLeast(1) }
            .clip(RoundedCornerShape(2.dp)).background(c.accent))
    }
}

@Composable
fun AtAGlanceClock(modifier: Modifier = Modifier, clockStyle: app.lawnchairlite.data.ClockStyle = app.lawnchairlite.data.ClockStyle.LARGE, onDateClick: () -> Unit = {}, onTimeClick: () -> Unit = {}, onCycleStyle: () -> Unit = {}) {
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

    val timeStr = if (is24h) {
        val h = String.format("%02d", cal.get(Calendar.HOUR_OF_DAY)); val m = String.format("%02d", cal.get(Calendar.MINUTE)); "$h:$m"
    } else {
        val hour = cal.get(Calendar.HOUR).let { if (it == 0) 12 else it }; val min = String.format("%02d", cal.get(Calendar.MINUTE)); "$hour:$min"
    }
    val ampm = if (!is24h) (if (cal.get(Calendar.AM_PM) == Calendar.AM) "AM" else "PM") else null

    // Double-tap detection with pending-action pattern:
    // Single tap deferred 360ms so a second tap can cancel it and cycle style instead.
    // This avoids accidentally opening the clock app on every double-tap.
    val scope = rememberCoroutineScope()
    var pendingTapJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var lastClockTap by remember { mutableLongStateOf(0L) }
    val clockTapHandler: () -> Unit = {
        val now = System.currentTimeMillis()
        if (now - lastClockTap < 350L) {
            pendingTapJob?.cancel()
            pendingTapJob = null
            onCycleStyle()
            lastClockTap = 0L
        } else {
            lastClockTap = now
            pendingTapJob?.cancel()
            pendingTapJob = scope.launch {
                kotlinx.coroutines.delay(360L)
                onTimeClick()
            }
        }
    }

    when (clockStyle) {
        app.lawnchairlite.data.ClockStyle.LARGE -> Column(modifier.padding(horizontal = 24.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onDateClick() }) {
                Text(dateStr, color = c.text.copy(alpha = 0.7f), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                if (batteryPct in 0..100) {
                    val batteryLow = batteryPct <= 15
                    Text("  |  ", color = c.textSecondary.copy(alpha = 0.4f), fontSize = 12.sp)
                    Icon(if (batteryLow) Icons.Default.BatteryAlert else Icons.Default.BatteryFull,
                        null, tint = if (batteryLow) Color(0xFFEF5350) else c.accent.copy(alpha = 0.7f),
                        modifier = Modifier.size(14.dp))
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
            Box(Modifier.clickable { clockTapHandler() }) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(timeStr, color = c.text, fontSize = 52.sp, fontWeight = FontWeight.Thin, lineHeight = 52.sp)
                    if (ampm != null) { Spacer(Modifier.width(6.dp)); Text(ampm, color = c.accent, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 9.dp)) }
                }
            }
        }
        app.lawnchairlite.data.ClockStyle.COMPACT -> Row(
            modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp).clickable { clockTapHandler() },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(dateStr, color = c.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.clickable { onDateClick() })
            Text("  ·  ", color = c.textSecondary.copy(alpha = 0.4f), fontSize = 13.sp)
            Text(timeStr, color = c.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            if (ampm != null) { Spacer(Modifier.width(3.dp)); Text(ampm, color = c.accent, fontSize = 10.sp, fontWeight = FontWeight.Medium) }
            if (batteryPct in 0..100) {
                Text("  ·  ", color = c.textSecondary.copy(alpha = 0.4f), fontSize = 13.sp)
                Text("$batteryPct%", color = c.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }
        app.lawnchairlite.data.ClockStyle.MINIMAL -> Box(
            modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp).clickable { clockTapHandler() },
            Alignment.CenterStart,
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(timeStr, color = c.text, fontSize = 64.sp, fontWeight = FontWeight.ExtraLight, lineHeight = 64.sp)
                if (ampm != null) { Spacer(Modifier.width(6.dp)); Text(ampm, color = c.accent, fontSize = 18.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 12.dp)) }
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
    val keyboardController = LocalSoftwareKeyboardController.current
    TextField(value = query, onValueChange = onQueryChange, modifier = modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(25.dp)).background(c.searchBg),
        placeholder = { Text("Search apps\u2026", color = c.textSecondary, fontSize = 14.sp) },
        leadingIcon = { Icon(Icons.Default.Search, null, tint = c.textSecondary, modifier = Modifier.size(18.dp)) },
        trailingIcon = if (query.isNotBlank()) {{ IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Close, "Clear", tint = c.textSecondary, modifier = Modifier.size(16.dp)) } }} else null,
        colors = TextFieldDefaults.colors(focusedTextColor = c.text, unfocusedTextColor = c.text, cursorColor = c.accent, focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
        singleLine = true, textStyle = TextStyle(fontSize = 14.sp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }))
}

@Composable
fun FastScrollerRail(letters: List<Char>, onLetterSelected: (Char) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalLauncherColors.current
    var dragging by remember { mutableStateOf(false) }
    val bgAlpha by animateFloatAsState(if (dragging) 0.12f else 0f, label = "fsa")
    Column(
        modifier.padding(end = 2.dp, top = 8.dp, bottom = 8.dp).widthIn(min = 20.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(c.accent.copy(alpha = bgAlpha))
            .pointerInput(letters) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    dragging = true
                    val idx = (down.position.y / size.height * letters.size).toInt().coerceIn(0, letters.lastIndex)
                    onLetterSelected(letters[idx])
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.all { !it.pressed }) { dragging = false; break }
                        val pos = event.changes.first().position
                        val newIdx = (pos.y / size.height * letters.size).toInt().coerceIn(0, letters.lastIndex)
                        onLetterSelected(letters[newIdx])
                        event.changes.forEach { it.consume() }
                    }
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        letters.forEach { ch ->
            Text(ch.toString(), color = c.accent, fontSize = 9.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                modifier = Modifier.size(16.dp).wrapContentSize(Alignment.Center))
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
                    val verInfo = remember(app.packageName) { vm.getAppVersionInfo(app.packageName) }
                    val launchCount = remember(cell.appKey) { vm.getAppLaunchCount(cell.appKey) }
                    val sizeInfo = remember(app.packageName) { vm.getAppSizeInfo(app.packageName) }
                    Text("${app.packageName}${if (verInfo != null) " $verInfo" else ""}${if (sizeInfo != null) " · $sizeInfo" else ""}${if (launchCount > 0) " · $launchCount launches" else ""}", color = c.textSecondary, fontSize = 10.sp)
                }
                is GridCell.Folder -> {
                    FolderIconContent(cell, shape, { vm.resolveApp(it) }, 54.dp, showLabel = false)
                    Spacer(Modifier.height(6.dp))
                    Text(cell.name, color = c.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("${cell.appKeys.size} apps", color = c.textSecondary, fontSize = 10.sp)
                }
                is GridCell.Widget -> {
                    Icon(Icons.Default.Widgets, null, tint = c.accent, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(6.dp))
                    Text("Widget", color = c.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
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
fun FolderOverlay(folder: GridCell.Folder, shape: IconShape, iconSizeDp: Dp, resolveApp: (String) -> AppInfo?, customLabels: Map<String, String>, folderColumns: Int = 4, onAppClick: (AppInfo) -> Unit, onRemoveApp: (String) -> Unit, onReorder: (List<String>) -> Unit, onRename: () -> Unit, onDismiss: () -> Unit) {
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
                        orderedKeys.chunked(folderColumns).forEachIndexed { rowIdx, row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                row.forEachIndexed { colIdx, key ->
                                    val flatIdx = rowIdx * folderColumns + colIdx
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
                                repeat(folderColumns - row.size) { Spacer(Modifier.width(66.dp)) }
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
fun DrawerContextMenu(app: AppInfo, shape: IconShape, vm: LauncherViewModel, shortcuts: List<AppShortcut>, onShortcutClick: (AppShortcut) -> Unit, onPinHome: () -> Unit, onPinDock: () -> Unit, onHide: () -> Unit, onAppInfo: () -> Unit, onUninstall: () -> Unit, onDismiss: () -> Unit) {
    val c = LocalLauncherColors.current
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).pointerInput(Unit) { detectTapGestures { onDismiss() } }, Alignment.Center) {
        Column(Modifier.widthIn(min = 240.dp, max = 280.dp).clip(RoundedCornerShape(20.dp)).background(c.surface).border(0.5.dp, c.border, RoundedCornerShape(20.dp)).pointerInput(Unit) { detectTapGestures { } }.padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(8.dp))
            Box(Modifier.size(54.dp).clip(iconClip(shape)).background(c.card), Alignment.Center) { if (app.icon != null) Image(rememberDrawablePainter(app.icon), null, Modifier.fillMaxSize().padding(5.dp)) }
            val verInfo = remember(app.packageName) { vm.getAppVersionInfo(app.packageName) }
            val launchCount = remember(app.key) { vm.getAppLaunchCount(app.key) }
            val sizeInfo = remember(app.packageName) { vm.getAppSizeInfo(app.packageName) }
            Spacer(Modifier.height(6.dp)); Text(app.label, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text("${app.packageName}${if (verInfo != null) " $verInfo" else ""}${if (sizeInfo != null) " · $sizeInfo" else ""}${if (launchCount > 0) " · $launchCount launches" else ""}", color = c.textSecondary, fontSize = 10.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 16.dp))
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

// ── Widget Picker ─────────────────────────────────────────────────────

@Composable
fun WidgetPickerDialog(
    widgets: List<AppWidgetProviderInfo>,
    onSelect: (AppWidgetProviderInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = LocalLauncherColors.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val pm = context.packageManager
    var search by remember { mutableStateOf("") }
    val filtered = remember(widgets, search) {
        if (search.isBlank()) widgets
        else widgets.filter { it.loadLabel(pm).toString().contains(search, ignoreCase = true) }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 500.dp)
                .clip(RoundedCornerShape(20.dp)).background(c.surface)
                .border(0.5.dp, c.border, RoundedCornerShape(20.dp)).padding(16.dp)
        ) {
            Text("Add Widget", color = c.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
            TextField(
                value = search, onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(12.dp)),
                placeholder = { Text("Search widgets...", color = c.textSecondary, fontSize = 13.sp) },
                colors = TextFieldDefaults.colors(focusedTextColor = c.text, unfocusedTextColor = c.text, cursorColor = c.accent, focusedContainerColor = c.card, unfocusedContainerColor = c.card, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                singleLine = true, textStyle = TextStyle(fontSize = 13.sp),
            )
            Spacer(Modifier.height(8.dp))
            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), Alignment.Center) {
                    Text("No widgets found", color = c.textSecondary, fontSize = 13.sp)
                }
            } else {
                LazyColumn(Modifier.weight(1f)) {
                    items(filtered) { info ->
                        val label = info.loadLabel(pm).toString()
                        val appLabel = try { pm.getApplicationLabel(pm.getApplicationInfo(info.provider.packageName, 0)).toString() } catch (_: Exception) { info.provider.packageName }
                        val minW = info.minWidth; val minH = info.minHeight
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                .clickable { onSelect(info) }
                                .padding(horizontal = 10.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val icon = try { info.loadIcon(context, context.resources.displayMetrics.densityDpi) } catch (_: Exception) { null }
                            if (icon != null) {
                                Image(rememberDrawablePainter(icon), label, Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)))
                                Spacer(Modifier.width(10.dp))
                            }
                            Column(Modifier.weight(1f)) {
                                Text(label, color = c.text, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(appLabel, color = c.textSecondary, fontSize = 10.sp, maxLines = 1)
                                Text("${minW}x${minH}dp", color = c.textSecondary.copy(alpha = 0.6f), fontSize = 9.sp)
                            }
                        }
                        Divider(color = c.border.copy(alpha = 0.2f), thickness = 0.5.dp)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Cancel", color = c.textSecondary) }
        }
    }
}

// ── Widget Host View (Compose wrapper for AppWidgetHostView) ──────────

@Composable
fun WidgetHostViewComposable(
    hostView: AppWidgetHostView?,
    modifier: Modifier = Modifier,
) {
    if (hostView == null) return
    AndroidView(
        factory = { hostView },
        modifier = modifier,
        update = { /* host view manages its own updates */ },
        onRelease = { try { (it.parent as? android.view.ViewGroup)?.removeView(it) } catch (_: Exception) {} },
    )
}

// ── Calculator Result ─────────────────────────────────────────────────

@Composable
fun CalculatorResultRow(result: String, modifier: Modifier = Modifier) {
    val c = LocalLauncherColors.current
    val context = androidx.compose.ui.platform.LocalContext.current
    Row(
        modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp)).background(c.accent.copy(alpha = 0.1f))
            .border(0.5.dp, c.accent.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
            .clickable {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                cm?.setPrimaryClip(android.content.ClipData.newPlainText("result", result))
                android.widget.Toast.makeText(context, "Copied: $result", android.widget.Toast.LENGTH_SHORT).show()
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("=", color = c.accent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(12.dp))
        Text(result, color = c.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.weight(1f))
        Icon(Icons.Default.ContentCopy, "Copy", tint = c.textSecondary.copy(alpha = 0.4f), modifier = Modifier.size(16.dp))
    }
}

// ── Suggestion Row ────────────────────────────────────────────────────

@Composable
fun SuggestionRow(
    apps: List<AppInfo>, shape: IconShape, iconSizeDp: Dp,
    iconShadow: Boolean = false, grayscale: Boolean = false,
    labelWeight: FontWeight = FontWeight.Normal,
    onAppClick: (AppInfo) -> Unit, modifier: Modifier = Modifier,
) {
    if (apps.isEmpty()) return
    val c = LocalLauncherColors.current
    Column(modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
        Text("SUGGESTED", color = c.accent.copy(alpha = 0.5f), fontSize = 10.sp,
            fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 6.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            apps.forEach { app ->
                TappableAppIcon(app, shape, iconSizeDp - 4.dp, showLabel = true,
                    iconShadow = iconShadow, grayscale = grayscale, labelWeight = labelWeight,
                    onClick = { onAppClick(app) },
                    modifier = Modifier.width(62.dp))
            }
        }
    }
}

// ── Search History Chips ──────────────────────────────────────────────

@Composable
fun SearchHistoryChips(
    history: List<String>, onTap: (String) -> Unit,
    onRemove: (String) -> Unit, onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (history.isEmpty()) return
    val c = LocalLauncherColors.current
    Column(modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("RECENT SEARCHES", color = c.accent.copy(alpha = 0.5f), fontSize = 10.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.weight(1f))
            Text("Clear", color = c.textSecondary, fontSize = 11.sp,
                modifier = Modifier.clip(RoundedCornerShape(8.dp))
                    .clickable { onClearAll() }
                    .padding(horizontal = 8.dp, vertical = 4.dp))
        }
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            history.forEach { term ->
                Row(
                    Modifier.clip(RoundedCornerShape(16.dp))
                        .background(c.card)
                        .border(0.5.dp, c.border, RoundedCornerShape(16.dp))
                        .clickable { onTap(term) }
                        .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(term, color = c.text, fontSize = 12.sp, maxLines = 1)
                    Spacer(Modifier.width(2.dp))
                    Icon(Icons.Default.Close, "Remove", tint = c.textSecondary.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp).clip(CircleShape)
                            .clickable { onRemove(term) }.padding(2.dp))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

// ── Contact Search Result ─────────────────────────────────────────────

@Composable
fun ContactResultRow(
    name: String, number: String?, lookupUri: String?,
    onTap: () -> Unit, onCall: (() -> Unit)?,
) {
    val c = LocalLauncherColors.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .clickable { onTap() }.padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(c.accent.copy(alpha = 0.15f)), Alignment.Center) {
            Icon(Icons.Default.Person, null, tint = c.accent, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(name, color = c.text, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (number != null) Text(number, color = c.textSecondary, fontSize = 11.sp, maxLines = 1)
        }
        if (onCall != null) {
            IconButton(onClick = { onCall() }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Phone, "Call", tint = c.accent, modifier = Modifier.size(16.dp))
            }
        }
    }
}
