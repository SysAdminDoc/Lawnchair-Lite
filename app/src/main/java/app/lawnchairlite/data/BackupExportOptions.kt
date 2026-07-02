package app.lawnchairlite.data

data class BackupExportOptions(
    val includeSearchHistory: Boolean = false,
    val includeAppUsage: Boolean = false,
    val includeHiddenApps: Boolean = false,
) {
    fun includedPrivateSections(): List<String> = buildList {
        if (includeSearchHistory) add(SECTION_SEARCH_HISTORY)
        if (includeAppUsage) add(SECTION_USAGE)
        if (includeHiddenApps) add(SECTION_HIDDEN_APPS)
    }

    fun omittedPrivateSections(): List<String> =
        ALL_PRIVATE_SECTIONS - includedPrivateSections().toSet()

    companion object {
        const val SECTION_SEARCH_HISTORY = "search_history"
        const val SECTION_USAGE = "usage_and_recents"
        const val SECTION_HIDDEN_APPS = "hidden_apps"

        val ALL_PRIVATE_SECTIONS = listOf(
            SECTION_SEARCH_HISTORY,
            SECTION_USAGE,
            SECTION_HIDDEN_APPS,
        )
    }
}
