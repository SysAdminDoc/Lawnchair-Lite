package app.lawnchairlite.data

data class BackupExportOptions(
    val includeSearchHistory: Boolean = false,
    val includeAppUsage: Boolean = false,
    val includeHiddenApps: Boolean = false,
    val includeAppearance: Boolean = true,
    val includeLayout: Boolean = true,
    val includeDrawerSearch: Boolean = true,
    val includeGestures: Boolean = true,
    val includeFeatureSettings: Boolean = true,
    val includeCustomLabels: Boolean = true,
) {
    fun includedLauncherSections(): List<String> = buildList {
        if (includeAppearance) add(SECTION_APPEARANCE)
        if (includeLayout) add(SECTION_LAYOUT_WIDGETS)
        if (includeDrawerSearch) add(SECTION_DRAWER_SEARCH)
        if (includeGestures) add(SECTION_GESTURES)
        if (includeFeatureSettings) add(SECTION_FEATURE_SETTINGS)
        if (includeCustomLabels) add(SECTION_CUSTOM_LABELS)
    }

    fun omittedLauncherSections(): List<String> =
        ALL_LAUNCHER_SECTIONS - includedLauncherSections().toSet()

    fun includedPrivateSections(): List<String> = buildList {
        if (includeSearchHistory) add(SECTION_SEARCH_HISTORY)
        if (includeAppUsage) add(SECTION_USAGE)
        if (includeHiddenApps) add(SECTION_HIDDEN_APPS)
    }

    fun omittedPrivateSections(): List<String> =
        ALL_PRIVATE_SECTIONS - includedPrivateSections().toSet()

    fun hasAnySection(): Boolean =
        includedLauncherSections().isNotEmpty() || includedPrivateSections().isNotEmpty()

    companion object {
        const val SECTION_APPEARANCE = "appearance"
        const val SECTION_LAYOUT_WIDGETS = "layout_widgets"
        const val SECTION_DRAWER_SEARCH = "drawer_search"
        const val SECTION_GESTURES = "gestures"
        const val SECTION_FEATURE_SETTINGS = "feature_settings"
        const val SECTION_CUSTOM_LABELS = "custom_labels"
        const val SECTION_SEARCH_HISTORY = "search_history"
        const val SECTION_USAGE = "usage_and_recents"
        const val SECTION_HIDDEN_APPS = "hidden_apps"

        val ALL_LAUNCHER_SECTIONS = listOf(
            SECTION_APPEARANCE,
            SECTION_LAYOUT_WIDGETS,
            SECTION_DRAWER_SEARCH,
            SECTION_GESTURES,
            SECTION_FEATURE_SETTINGS,
            SECTION_CUSTOM_LABELS,
        )

        val ALL_PRIVATE_SECTIONS = listOf(
            SECTION_SEARCH_HISTORY,
            SECTION_USAGE,
            SECTION_HIDDEN_APPS,
        )
    }
}
