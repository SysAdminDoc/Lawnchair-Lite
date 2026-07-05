package app.lawnchairlite.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ThemeTransferTest {

    @Test
    fun themeSnapshotRoundTripsThroughJson() {
        val snapshot = ThemeSnapshot(
            themeMode = ThemeMode.AURORA,
            dynamicColor = true,
            accentOverride = "#80CBC4",
            iconPack = "com.example.icons",
            themedIcons = true,
            iconShape = IconShape.HEXAGON,
            iconShadow = true,
            grayscaleIcons = true,
        )

        val parsed = ThemeTransfer.parse(ThemeTransfer.export(snapshot, appVersion = "2.27.0"))

        assertEquals(snapshot, parsed)
    }

    @Test
    fun nonThemePayloadIsRejected() {
        assertNull(ThemeTransfer.parse("""{"schema":1,"theme":"MIDNIGHT"}"""))
    }

    @Test
    fun futureSchemaIsRejected() {
        assertNull(ThemeTransfer.parse("""{"type":"lawnchair-lite-theme","schema":99,"theme":"MIDNIGHT"}"""))
    }
}
