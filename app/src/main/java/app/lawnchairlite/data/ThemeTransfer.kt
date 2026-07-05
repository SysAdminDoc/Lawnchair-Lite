package app.lawnchairlite.data

data class ThemeSnapshot(
    val themeMode: ThemeMode = ThemeMode.MIDNIGHT,
    val dynamicColor: Boolean = false,
    val accentOverride: String = "",
    val iconPack: String = "",
    val iconPacks: List<String> = emptyList(),
    val themedIcons: Boolean = false,
    val iconShape: IconShape = IconShape.NONE,
    val iconShadow: Boolean = false,
    val grayscaleIcons: Boolean = false,
)

object ThemeTransfer {
    const val TYPE = "lawnchair-lite-theme"
    const val SCHEMA = 1

    fun export(snapshot: ThemeSnapshot, appVersion: String): String {
        val fields = listOf(
            jsonPair("type", TYPE),
            jsonPair("schema", SCHEMA),
            jsonPair("version", appVersion),
            jsonPair("exported_at", System.currentTimeMillis()),
            jsonPair("theme", snapshot.themeMode.name),
            jsonPair("dynamic_color", snapshot.dynamicColor),
            jsonPair("accent_override", snapshot.accentOverride),
            jsonPair("icon_pack", snapshot.iconPack),
            jsonPair("icon_packs", serializeIconPackChain(snapshot.iconPacks.ifEmpty { listOf(snapshot.iconPack) })),
            jsonPair("themed_icons", snapshot.themedIcons),
            jsonPair("icon_shape", snapshot.iconShape.name),
            jsonPair("icon_shadow", snapshot.iconShadow),
            jsonPair("grayscale_icons", snapshot.grayscaleIcons),
        )
        return fields.joinToString(prefix = "{\n  ", separator = ",\n  ", postfix = "\n}")
    }

    fun parse(json: String): ThemeSnapshot? = runCatching {
        val fields = parseFlatJson(json)
        if (fields["type"] != TYPE) return@runCatching null
        if ((fields["schema"]?.toIntOrNull() ?: SCHEMA) > SCHEMA) return@runCatching null
        ThemeSnapshot(
            themeMode = fields.optEnum("theme", ThemeMode.MIDNIGHT),
            dynamicColor = fields["dynamic_color"] == "true",
            accentOverride = fields["accent_override"].orEmpty().take(32),
            iconPack = fields["icon_pack"].orEmpty().take(240),
            iconPacks = fields["icon_packs"]?.let { parseIconPackChain(it) }?.takeIf { it.isNotEmpty() }
                ?: parseIconPackChain(fields["icon_pack"].orEmpty()),
            themedIcons = fields["themed_icons"] == "true",
            iconShape = fields.optEnum("icon_shape", IconShape.NONE),
            iconShadow = fields["icon_shadow"] == "true",
            grayscaleIcons = fields["grayscale_icons"] == "true",
        )
    }.getOrNull()

    private inline fun <reified T : Enum<T>> Map<String, String>.optEnum(key: String, default: T): T {
        val raw = this[key].orEmpty()
        return runCatching { enumValueOf<T>(raw) }.getOrDefault(default)
    }

    private fun jsonPair(key: String, value: String): String = "${jsonString(key)}: ${jsonString(value)}"
    private fun jsonPair(key: String, value: Boolean): String = "${jsonString(key)}: $value"
    private fun jsonPair(key: String, value: Int): String = "${jsonString(key)}: $value"
    private fun jsonPair(key: String, value: Long): String = "${jsonString(key)}: $value"

    private fun jsonString(value: String): String = buildString {
        append('"')
        value.forEach { char ->
            when (char) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (char.code < 0x20) append("\\u%04x".format(char.code)) else append(char)
            }
        }
        append('"')
    }

    private fun parseFlatJson(json: String): Map<String, String> =
        jsonFieldRegex.findAll(json).associate { match ->
            val key = unescapeJsonString(match.groupValues[1])
            val rawValue = match.groupValues[2]
            val stringValue = match.groupValues[3]
            key to if (rawValue.startsWith("\"")) unescapeJsonString(stringValue) else rawValue
        }

    private val jsonFieldRegex = Regex("\"((?:\\\\.|[^\"\\\\])*)\"\\s*:\\s*(\"((?:\\\\.|[^\"\\\\])*)\"|true|false|-?\\d+)")

    private fun unescapeJsonString(value: String): String = buildString {
        var index = 0
        while (index < value.length) {
            val char = value[index++]
            if (char != '\\' || index >= value.length) {
                append(char)
                continue
            }
            when (val escaped = value[index++]) {
                '"' -> append('"')
                '\\' -> append('\\')
                '/' -> append('/')
                'b' -> append('\b')
                'f' -> append('\u000C')
                'n' -> append('\n')
                'r' -> append('\r')
                't' -> append('\t')
                'u' -> {
                    val end = (index + 4).coerceAtMost(value.length)
                    val code = value.substring(index, end).toIntOrNull(16)
                    if (code != null && end - index == 4) {
                        append(code.toChar())
                        index = end
                    }
                }
                else -> append(escaped)
            }
        }
    }
}
