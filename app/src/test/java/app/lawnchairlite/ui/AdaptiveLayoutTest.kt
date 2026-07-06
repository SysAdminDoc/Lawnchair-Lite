package app.lawnchairlite.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveLayoutTest {
    @Test
    fun phoneKeepsFullScreenDrawer() {
        val layout = resolveAdaptiveLauncherLayout(
            screenWidthDp = 411,
            screenHeightDp = 891,
            requestedDrawerColumns = 0,
            homeColumns = 4,
        )

        assertFalse(layout.largeScreen)
        assertEquals(0, layout.drawerPaneWidthDp)
        assertEquals(4, layout.drawerColumns)
        assertEquals(0, layout.homeEndPaddingDp)
    }

    @Test
    fun tabletUsesRightSideDrawerPane() {
        val layout = resolveAdaptiveLauncherLayout(
            screenWidthDp = 1200,
            screenHeightDp = 800,
            requestedDrawerColumns = 0,
            homeColumns = 5,
        )

        assertTrue(layout.largeScreen)
        assertEquals(456, layout.drawerPaneWidthDp)
        assertEquals(4, layout.drawerColumns)
        assertEquals(472, layout.homeEndPaddingDp)
    }

    @Test
    fun requestedTabletDrawerColumnsAreBounded() {
        val layout = resolveAdaptiveLauncherLayout(
            screenWidthDp = 900,
            screenHeightDp = 720,
            requestedDrawerColumns = 2,
            homeColumns = 5,
        )

        assertTrue(layout.largeScreen)
        assertEquals(3, layout.drawerColumns)
    }
}
