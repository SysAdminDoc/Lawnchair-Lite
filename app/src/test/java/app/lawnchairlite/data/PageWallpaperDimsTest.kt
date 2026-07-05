package app.lawnchairlite.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PageWallpaperDimsTest {

    @Test
    fun serializePageWallpaperDimsClampsAndSortsValues() {
        val encoded = serializePageWallpaperDims(
            mapOf(
                2 to 90,
                0 to -5,
                1 to 35,
                99 to 50,
            ),
        )

        assertEquals("0=0|1=35|2=80", encoded)
        assertEquals(mapOf(0 to 0, 1 to 35, 2 to 80), parsePageWallpaperDims(encoded))
        assertFalse(encoded.contains("99"))
    }

    @Test
    fun parsePageWallpaperDimsIgnoresInvalidPages() {
        val parsed = parsePageWallpaperDims("0=12|bad=42|3=81|50=20")

        assertEquals(mapOf(0 to 12, 3 to 80), parsed)
    }
}
