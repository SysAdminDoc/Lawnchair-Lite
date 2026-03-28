package app.lawnchairlite.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import app.lawnchairlite.data.ThemeMode

/** Lawnchair Lite - Theme Engine */
data class LauncherColors(
    val background: Color,
    val backgroundSecondary: Color,
    val surface: Color,
    val surfaceHover: Color,
    val card: Color,
    val accent: Color,
    val accentGlow: Color,
    val text: Color,
    val textSecondary: Color,
    val border: Color,
    val dock: Color,
    val searchBg: Color,
    val widgetBg: Color,
    val error: Color,
)

val LocalLauncherColors = staticCompositionLocalOf { MidnightColors }

val MidnightColors = LauncherColors(
    background = Color(0xFF0A0A0F),
    backgroundSecondary = Color(0xFF111118),
    surface = Color(0xEB12121C),
    surfaceHover = Color(0xF21C1C2A),
    card = Color(0xE016162A),
    accent = Color(0xFF7C6AFF),
    accentGlow = Color(0x407C6AFF),
    text = Color(0xFFE8E6F0),
    textSecondary = Color(0xFF8A87A0),
    border = Color(0x1F7C6AFF),
    dock = Color(0xF00E0E16),
    searchBg = Color(0xB31E1E30),
    widgetBg = Color(0xBF161626),
    error = Color(0xFFEF5350),
)

val GlassColors = LauncherColors(
    background = Color(0xFF1A1A2E),
    backgroundSecondary = Color(0xFF16213E),
    surface = Color(0x0FFFFFFF),
    surfaceHover = Color(0x1AFFFFFF),
    card = Color(0x0AFFFFFF),
    accent = Color(0xFF00D4FF),
    accentGlow = Color(0x3300D4FF),
    text = Color(0xFFF0F4FF),
    textSecondary = Color(0xFF8899BB),
    border = Color(0x14FFFFFF),
    dock = Color(0x0DFFFFFF),
    searchBg = Color(0x12FFFFFF),
    widgetBg = Color(0x0DFFFFFF),
    error = Color(0xFFEF5350),
)

val OledColors = LauncherColors(
    background = Color(0xFF000000),
    backgroundSecondary = Color(0xFF000000),
    surface = Color(0xFA0C0C0C),
    surfaceHover = Color(0xFA161616),
    card = Color(0xF2080808),
    accent = Color(0xFF00E676),
    accentGlow = Color(0x3300E676),
    text = Color(0xFFE0E0E0),
    textSecondary = Color(0xFF666666),
    border = Color(0x0FFFFFFF),
    dock = Color(0xFA060606),
    searchBg = Color(0xE6121212),
    widgetBg = Color(0xE60A0A0A),
    error = Color(0xFFEF5350),
)

val MochaColors = LauncherColors(
    background = Color(0xFF1E1E2E),
    backgroundSecondary = Color(0xFF181825),
    surface = Color(0xF21E1E2E),
    surfaceHover = Color(0xF2313244),
    card = Color(0xE6181825),
    accent = Color(0xFFCBA6F7),
    accentGlow = Color(0x33CBA6F7),
    text = Color(0xFFCDD6F4),
    textSecondary = Color(0xFF6C7086),
    border = Color(0x1ACBA6F7),
    dock = Color(0xF511111B),
    searchBg = Color(0x99313244),
    widgetBg = Color(0xCC1E1E2E),
    error = Color(0xFFF38BA8),
)

val AuroraColors = LauncherColors(
    background = Color(0xFF0A0E1A),
    backgroundSecondary = Color(0xFF0D1B2A),
    surface = Color(0xE60F192D),
    surfaceHover = Color(0xEB14233C),
    card = Color(0xD90C1426),
    accent = Color(0xFF64FFDA),
    accentGlow = Color(0x2664FFDA),
    text = Color(0xFFE0F7FA),
    textSecondary = Color(0xFF5E8A9A),
    border = Color(0x1A64FFDA),
    dock = Color(0xF2080E1C),
    searchBg = Color(0x99142337),
    widgetBg = Color(0xB30F192D),
    error = Color(0xFFFF8A80),
)

val NeonColors = LauncherColors(
    background = Color(0xFF0D0D0D),
    backgroundSecondary = Color(0xFF121212),
    surface = Color(0xEB141414),
    surfaceHover = Color(0xF21C1C1C),
    card = Color(0xE0111111),
    accent = Color(0xFFFF0080),
    accentGlow = Color(0x40FF0080),
    text = Color(0xFFFFFFFF),
    textSecondary = Color(0xFF999999),
    border = Color(0x1FFF0080),
    dock = Color(0xF00A0A0A),
    searchBg = Color(0xB3181818),
    widgetBg = Color(0xBF121212),
    error = Color(0xFFFF6E40),
)

fun themeColors(mode: ThemeMode): LauncherColors = when (mode) {
    ThemeMode.MIDNIGHT -> MidnightColors
    ThemeMode.GLASS -> GlassColors
    ThemeMode.OLED -> OledColors
    ThemeMode.MOCHA -> MochaColors
    ThemeMode.AURORA -> AuroraColors
    ThemeMode.NEON -> NeonColors
}

fun themeColorsWithAccent(mode: ThemeMode, accentOverride: String): LauncherColors {
    val base = themeColors(mode)
    if (accentOverride.isBlank()) return base
    val accent = try { Color(android.graphics.Color.parseColor(accentOverride)) } catch (_: Exception) { return base }
    val glow = accent.copy(alpha = 0.25f)
    val border = accent.copy(alpha = 0.12f)
    return base.copy(accent = accent, accentGlow = glow, border = border)
}

@Composable
fun LauncherTheme(themeMode: ThemeMode = ThemeMode.GLASS, accentOverride: String = "", content: @Composable () -> Unit) {
    val colors = remember(themeMode, accentOverride) { themeColorsWithAccent(themeMode, accentOverride) }
    CompositionLocalProvider(LocalLauncherColors provides colors) { content() }
}
