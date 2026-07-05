package app.lawnchairlite.data

import org.junit.Assert.assertEquals
import org.junit.Test

class CustomFontTest {

    @Test
    fun customFontUriAcceptsOnlyReadableUriSchemes() {
        assertEquals("content://downloads/document/42", sanitizeCustomFontUri(" content://downloads/document/42 "))
        assertEquals("file:///sdcard/Download/font.ttf", sanitizeCustomFontUri("file:///sdcard/Download/font.ttf"))
        assertEquals("", sanitizeCustomFontUri("https://example.com/font.ttf"))
        assertEquals("", sanitizeCustomFontUri("font.ttf"))
    }

    @Test
    fun customFontNameNormalizesWhitespaceAndFallsBackToUriLeaf() {
        assertEquals("Inter Display.ttf", sanitizeCustomFontName("  Inter   Display.ttf  "))
        assertEquals("Brand.otf", sanitizeCustomFontName("", "content://downloads/document/Brand.otf?token=1"))
        assertEquals("Custom font", sanitizeCustomFontName(""))
    }
}
