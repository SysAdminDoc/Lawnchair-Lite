package app.lawnchairlite.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import app.lawnchairlite.data.ThemeMode

/** Lawnchair Lite v0.9.0 - Theme Engine */
data class LauncherColors(
    val background: Color, val backgroundSecondary: Color, val surface: Color, val surfaceHover: Color,
    val card: Color, val accent: Color, val accentGlow: Color, val text: Color, val textSecondary: Color,
    val border: Color, val dock: Color, val searchBg: Color, val widgetBg: Color,
)

val LocalLauncherColors = staticCompositionLocalOf { MidnightColors }

val MidnightColors = LauncherColors(Color(0xFF0A0A0F),Color(0xFF111118),Color(0xEB12121C),Color(0xF21C1C2A),Color(0xE016162A),Color(0xFF7C6AFF),Color(0x407C6AFF),Color(0xFFE8E6F0),Color(0xFF8A87A0),Color(0x1F7C6AFF),Color(0xF00E0E16),Color(0xB31E1E30),Color(0xBF161626))
val GlassColors = LauncherColors(Color(0xFF1A1A2E),Color(0xFF16213E),Color(0x0FFFFFFF),Color(0x1AFFFFFF),Color(0x0AFFFFFF),Color(0xFF00D4FF),Color(0x3300D4FF),Color(0xFFF0F4FF),Color(0xFF8899BB),Color(0x14FFFFFF),Color(0x0DFFFFFF),Color(0x12FFFFFF),Color(0x0DFFFFFF))
val OledColors = LauncherColors(Color(0xFF000000),Color(0xFF000000),Color(0xFA0C0C0C),Color(0xFA161616),Color(0xF2080808),Color(0xFF00E676),Color(0x3300E676),Color(0xFFE0E0E0),Color(0xFF666666),Color(0x0FFFFFFF),Color(0xFA060606),Color(0xE6121212),Color(0xE60A0A0A))
val MochaColors = LauncherColors(Color(0xFF1E1E2E),Color(0xFF181825),Color(0xF21E1E2E),Color(0xF2313244),Color(0xE6181825),Color(0xFFCBA6F7),Color(0x33CBA6F7),Color(0xFFCDD6F4),Color(0xFF6C7086),Color(0x1ACBA6F7),Color(0xF511111B),Color(0x99313244),Color(0xCC1E1E2E))
val AuroraColors = LauncherColors(Color(0xFF0A0E1A),Color(0xFF0D1B2A),Color(0xE60F192D),Color(0xEB14233C),Color(0xD90C1426),Color(0xFF64FFDA),Color(0x2664FFDA),Color(0xFFE0F7FA),Color(0xFF5E8A9A),Color(0x1A64FFDA),Color(0xF2080E1C),Color(0x99142337),Color(0xB30F192D))

fun themeColors(mode: ThemeMode): LauncherColors = when (mode) { ThemeMode.MIDNIGHT -> MidnightColors; ThemeMode.GLASS -> GlassColors; ThemeMode.OLED -> OledColors; ThemeMode.MOCHA -> MochaColors; ThemeMode.AURORA -> AuroraColors }

@Composable
fun LauncherTheme(themeMode: ThemeMode = ThemeMode.GLASS, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLauncherColors provides themeColors(themeMode)) { content() }
}
