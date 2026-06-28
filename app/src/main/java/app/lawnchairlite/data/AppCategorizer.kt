package app.lawnchairlite.data

/**
 * Lawnchair Lite - App Categorizer
 *
 * Smart auto-categorization based on package name heuristics.
 * No Play Store API needed - works offline.
 */
object AppCategorizer {

    private val GAMES = setOf(
        "game", "games", "play", "puzzle", "arcade", "racing", "rpg", "casino",
        "chess", "sudoku", "solitaire", "minecraft", "roblox", "fortnite",
        "pubg", "cod", "clash", "candy", "angry", "temple", "subway",
    )
    private val SOCIAL = setOf(
        "whatsapp", "telegram", "messenger", "instagram", "facebook", "twitter",
        "tiktok", "snapchat", "discord", "signal", "viber", "wechat", "line",
        "reddit", "tumblr", "pinterest", "linkedin", "dating", "tinder",
        "threads", "mastodon", "bluesky", "contacts", "dialer", "messaging",
        "mms", "sms", "chat",
    )
    private val MEDIA = setOf(
        "music", "spotify", "youtube", "netflix", "video", "player", "camera",
        "photos", "gallery", "podcast", "radio", "audio", "media", "tv",
        "hulu", "disney", "prime", "hbo", "twitch", "soundcloud", "shazam",
        "plex", "vlc", "recorder", "editor",
    )
    private val TOOLS = setOf(
        "settings", "calculator", "clock", "calendar", "weather", "compass",
        "flashlight", "files", "filemanager", "explorer", "browser", "chrome",
        "firefox", "opera", "brave", "edge", "safari", "maps", "translate",
        "keyboard", "launcher", "cleaner", "battery", "vpn", "wifi",
        "bluetooth", "nfc", "qr", "scanner", "measure", "notes", "memo",
    )
    private val WORK = setOf(
        "mail", "gmail", "outlook", "office", "docs", "sheets", "slides",
        "drive", "dropbox", "onedrive", "slack", "teams", "zoom", "meet",
        "notion", "trello", "asana", "jira", "confluence", "evernote",
        "keep", "todo", "task", "pdf", "scan", "print", "remote",
    )

    /**
     * Split package name and label into tokens for word-boundary matching.
     * "com.google.android.youtube" -> ["com", "google", "android", "youtube"]
     * "My Game App" -> ["my", "game", "app"]
     */
    private fun tokenize(text: String): Set<String> =
        text.lowercase().split('.', ' ', '_', '-').filter { it.isNotBlank() }.toSet()

    fun categorize(app: AppInfo, rules: List<AppCategoryRule> = emptyList()): DrawerCategory {
        rules.firstOrNull { it.matches(app) }?.let { return it.category }
        val tokens = tokenize(app.packageName) + tokenize(app.label)

        return when {
            GAMES.any { it in tokens } -> DrawerCategory.GAMES
            SOCIAL.any { it in tokens } -> DrawerCategory.SOCIAL
            MEDIA.any { it in tokens } -> DrawerCategory.MEDIA
            WORK.any { it in tokens } -> DrawerCategory.WORK
            TOOLS.any { it in tokens } -> DrawerCategory.TOOLS
            else -> DrawerCategory.OTHER
        }
    }

    fun categorizeAll(apps: List<AppInfo>, rules: List<AppCategoryRule> = emptyList()): Map<DrawerCategory, List<AppInfo>> {
        val result = mutableMapOf<DrawerCategory, MutableList<AppInfo>>()
        DrawerCategory.entries.forEach { result[it] = mutableListOf() }
        apps.forEach { app ->
            val cat = categorize(app, rules)
            result[cat]!!.add(app)
            result[DrawerCategory.ALL]!!.add(app)
        }
        return result
    }

    fun AppCategoryRule.matches(app: AppInfo): Boolean {
        if (!enabled || category == DrawerCategory.ALL || pattern.isBlank()) return false
        return when (type) {
            CategoryRuleType.APP_NAME_REGEX -> runCatching {
                Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(app.label)
            }.getOrDefault(false)
            CategoryRuleType.PACKAGE_PREFIX -> app.packageName.startsWith(pattern.trim(), ignoreCase = true)
            CategoryRuleType.INSTALL_SOURCE -> app.installSource.startsWith(pattern.trim(), ignoreCase = true)
        }
    }
}
