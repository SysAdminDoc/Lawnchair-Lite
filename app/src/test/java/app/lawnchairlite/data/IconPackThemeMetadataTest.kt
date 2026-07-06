package app.lawnchairlite.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IconPackThemeMetadataTest {
    @Test
    fun parsesAccentAndWallpaperSuggestions() {
        val metadata = IconPackThemeMetadataParser.parseXml(
            "com.example.icons",
            """
            <lawnchair-theme accent="#4ade80">
                <wallpaper label="Forest Glass" uri="https://example.com/wallpapers/forest.jpg" />
                <wallpaper name="Local" uri="android.resource://com.example.icons/drawable/wallpaper_local" />
            </lawnchair-theme>
            """.trimIndent(),
        )

        assertEquals("#4ADE80", metadata?.accentColor)
        assertEquals(2, metadata?.wallpaperSuggestions?.size)
        assertEquals("Forest Glass", metadata?.wallpaperSuggestions?.first()?.label)
    }

    @Test
    fun parsesNestedAccentTag() {
        val metadata = IconPackThemeMetadataParser.parseXml(
            "com.example.icons",
            """
            <theme>
                <accent color="#ff5722" />
            </theme>
            """.trimIndent(),
        )

        assertEquals("#FF5722", metadata?.accentColor)
    }

    @Test
    fun rejectsEmptyOrUnsafeMetadata() {
        assertNull(IconPackThemeMetadataParser.parseXml("com.example.icons", """<theme accent="purple" />"""))
        assertNull(IconPackThemeMetadataParser.parseXml("com.example.icons", """<theme><wallpaper uri="wallpaper.jpg" /></theme>"""))
    }
}
