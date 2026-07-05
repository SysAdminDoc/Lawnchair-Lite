package app.lawnchairlite.data

import org.junit.Assert.assertEquals
import org.junit.Test

class BackupExportOptionsTest {

    @Test
    fun defaultsOmitPrivateSections() {
        val options = BackupExportOptions()

        assertEquals(
            listOf("appearance", "layout_widgets", "drawer_search", "gestures", "feature_settings", "custom_labels"),
            options.includedLauncherSections(),
        )
        assertEquals(emptyList<String>(), options.omittedLauncherSections())
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

    @Test
    fun selectedLauncherSectionsAreReported() {
        val options = BackupExportOptions(
            includeAppearance = false,
            includeLayout = false,
            includeDrawerSearch = false,
            includeGestures = true,
            includeFeatureSettings = false,
            includeCustomLabels = false,
        )

        assertEquals(listOf("gestures"), options.includedLauncherSections())
        assertEquals(
            listOf("appearance", "layout_widgets", "drawer_search", "feature_settings", "custom_labels"),
            options.omittedLauncherSections(),
        )
        assertEquals(true, options.hasAnySection())
    }

    @Test
    fun emptySelectionIsReported() {
        val options = BackupExportOptions(
            includeAppearance = false,
            includeLayout = false,
            includeDrawerSearch = false,
            includeGestures = false,
            includeFeatureSettings = false,
            includeCustomLabels = false,
        )

        assertEquals(emptyList<String>(), options.includedLauncherSections())
        assertEquals(false, options.hasAnySection())
    }
}
