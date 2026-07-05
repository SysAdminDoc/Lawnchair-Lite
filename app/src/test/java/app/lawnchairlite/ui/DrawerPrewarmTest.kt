package app.lawnchairlite.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class DrawerPrewarmTest {

    @Test
    fun targetSkipsFirstViewportWithoutHeaders() {
        val labels = (1..30).map { "App $it" }

        assertEquals(16, drawerPrewarmTargetIndex(labels, columns = 4, showSectionHeaders = false, showRecentRow = false))
    }

    @Test
    fun targetAccountsForSectionHeaders() {
        val labels = listOf(
            "Alpha",
            "Archive",
            "Browser",
            "Camera",
            "Clock",
            "Docs",
            "Drive",
            "Email",
        )

        assertEquals(12, drawerPrewarmTargetIndex(labels, columns = 2, showSectionHeaders = true, showRecentRow = false))
    }

    @Test
    fun targetIncludesRecentRowOffset() {
        val labels = (1..30).map { "App $it" }

        assertEquals(17, drawerPrewarmTargetIndex(labels, columns = 4, showSectionHeaders = false, showRecentRow = true))
    }

    @Test
    fun shortListsDoNotPrewarm() {
        assertEquals(0, drawerPrewarmTargetIndex(emptyList(), columns = 4, showSectionHeaders = false, showRecentRow = false))
        assertEquals(0, drawerPrewarmTargetIndex(listOf("Only"), columns = 4, showSectionHeaders = false, showRecentRow = false))
    }
}
