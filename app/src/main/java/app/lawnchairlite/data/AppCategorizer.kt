package app.lawnchairlite.data

/**
 * Lawnchair Lite v2.12.0 - App Categorizer
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

    fun categorize(app: AppInfo): DrawerCategory {
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

    fun categorizeAll(apps: List<AppInfo>): Map<DrawerCategory, List<AppInfo>> {
        val result = mutableMapOf<DrawerCategory, MutableList<AppInfo>>()
        DrawerCategory.entries.forEach { result[it] = mutableListOf() }
        apps.forEach { app ->
            val cat = categorize(app)
            result[cat]!!.add(app)
            result[DrawerCategory.ALL]!!.add(app)
        }
        return result
    }
}
