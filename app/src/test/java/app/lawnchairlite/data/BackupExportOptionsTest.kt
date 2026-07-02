package app.lawnchairlite.data

import org.junit.Assert.assertEquals
import org.junit.Test

class BackupExportOptionsTest {

    @Test
    fun defaultsOmitPrivateSections() {
        val options = BackupExportOptions()

        assertEquals(emptyList<String>(), options.includedPrivateSections())
        assertEquals(
            listOf("search_history", "usage_and_recents", "hidden_apps"),
            options.omittedPrivateSections(),
        )
    }

    @Test
    fun selectedPrivateSectionsAreReported() {
        val options = BackupExportOptions(
            includeSearchHistory = true,
            includeAppUsage = true,
            includeHiddenApps = false,
        )

        assertEquals(
            listOf("search_history", "usage_and_recents"),
            options.includedPrivateSections(),
        )
        assertEquals(listOf("hidden_apps"), options.omittedPrivateSections())
    }
}
