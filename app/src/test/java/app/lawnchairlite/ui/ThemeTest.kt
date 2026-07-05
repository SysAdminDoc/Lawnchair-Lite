package app.lawnchairlite.ui

import androidx.compose.ui.graphics.Color
import app.lawnchairlite.data.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeTest {

    @Test
    fun dynamicAccentAppliesWhenNoCustomAccentIsSet() {
        val colors = themeColorsWithAccent(ThemeMode.MIDNIGHT, accentOverride = "", dynamicAccent = Color(0xFF80CBC4))

        assertEquals(Color(0xFF80CBC4), colors.accent)
        assertEquals(Color(0x4080CBC4), colors.accentGlow)
        assertEquals(Color(0x1F80CBC4), colors.border)
    }

    @Test
    fun customAccentOverridesDynamicAccent() {
        val colors = themeColorsWithAccent(ThemeMode.MIDNIGHT, accentOverride = "#FF5722", dynamicAccent = Color(0xFF80CBC4))

        assertEquals(Color(0xFFFF5722), colors.accent)
        assertEquals(Color(0x40FF5722), colors.accentGlow)
        assertEquals(Color(0x1FFF5722), colors.border)
    }

    @Test
    fun invalidCustomAccentFallsBackToDynamicAccent() {
        val colors = themeColorsWithAccent(ThemeMode.MIDNIGHT, accentOverride = "bad", dynamicAccent = Color(0xFF80CBC4))

        assertEquals(Color(0xFF80CBC4), colors.accent)
    }
}
