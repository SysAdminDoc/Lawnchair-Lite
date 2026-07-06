package app.lawnchairlite.data

import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource
import org.w3c.dom.Element

data class IconPackWallpaperSuggestion(
    val label: String = "",
    val uri: String = "",
)

data class IconPackThemeMetadata(
    val packageName: String,
    val accentColor: String = "",
    val wallpaperSuggestions: List<IconPackWallpaperSuggestion> = emptyList(),
) {
    val hasAccent: Boolean get() = accentColor.isNotBlank()
    val hasWallpaperSuggestions: Boolean get() = wallpaperSuggestions.isNotEmpty()
    val isEmpty: Boolean get() = !hasAccent && !hasWallpaperSuggestions
}

object IconPackThemeMetadataParser {
    private val hexColorRegex = Regex("^#(?:[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$")
    private val schemeRegex = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://")

    fun parseXml(packageName: String, xml: String): IconPackThemeMetadata? {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isIgnoringComments = true
            isCoalescing = true
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        }
        val doc = factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
        var accentColor = ""
        val wallpapers = linkedMapOf<String, IconPackWallpaperSuggestion>()
        val nodes = doc.getElementsByTagName("*")

        for (index in 0 until nodes.length) {
            val element = nodes.item(index) as? Element ?: continue
            when (element.tagName) {
                "lawnchair-theme", "lawnchair_theme", "theme" -> {
                    accentColor = firstValidAccent(element, "accent", "accent_color", "accentColor") ?: accentColor
                }
                "accent" -> {
                    accentColor = firstValidAccent(element, "color", "value", "accent") ?: accentColor
                }
                "wallpaper" -> {
                    val uri = sanitizeUri(element.attr("uri").ifBlank { element.attr("url").ifBlank { element.attr("href") } })
                    if (uri.isNotBlank()) {
                        wallpapers[uri] = IconPackWallpaperSuggestion(
                            label = sanitizeLabel(element.attr("label").ifBlank { element.attr("name") }),
                            uri = uri,
                        )
                    }
                }
            }
        }

        return buildMetadata(packageName, accentColor, wallpapers.values)
    }

    fun parse(packageName: String, parser: XmlPullParser): IconPackThemeMetadata? {
        var accentColor = ""
        val wallpapers = linkedMapOf<String, IconPackWallpaperSuggestion>()
        var eventType = parser.eventType

        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "lawnchair-theme", "lawnchair_theme", "theme" -> {
                        accentColor = firstValidAccent(parser, "accent", "accent_color", "accentColor") ?: accentColor
                    }
                    "accent" -> {
                        accentColor = firstValidAccent(parser, "color", "value", "accent") ?: accentColor
                    }
                    "wallpaper" -> {
                        val uri = sanitizeUri(
                            parser.getAttributeValue(null, "uri")
                                ?: parser.getAttributeValue(null, "url")
                                ?: parser.getAttributeValue(null, "href"),
                        )
                        if (uri.isNotBlank()) {
                            wallpapers[uri] = IconPackWallpaperSuggestion(
                                label = sanitizeLabel(parser.getAttributeValue(null, "label") ?: parser.getAttributeValue(null, "name")),
                                uri = uri,
                            )
                        }
                    }
                }
            }
            eventType = parser.next()
        }

        return buildMetadata(packageName, accentColor, wallpapers.values)
    }

    private fun firstValidAccent(parser: XmlPullParser, vararg names: String): String? {
        return names.firstNotNullOfOrNull { name -> sanitizeAccent(parser.getAttributeValue(null, name)).takeIf { it.isNotBlank() } }
    }

    private fun firstValidAccent(element: Element, vararg names: String): String? {
        return names.firstNotNullOfOrNull { name -> sanitizeAccent(element.attr(name)).takeIf { it.isNotBlank() } }
    }

    private fun Element.attr(name: String): String = if (hasAttribute(name)) getAttribute(name) else ""

    private fun buildMetadata(
        packageName: String,
        accentColor: String,
        wallpapers: Collection<IconPackWallpaperSuggestion>,
    ): IconPackThemeMetadata? {
        return IconPackThemeMetadata(
            packageName = packageName.take(240),
            accentColor = accentColor,
            wallpaperSuggestions = wallpapers.take(8),
        ).takeUnless { it.isEmpty }
    }

    private fun sanitizeAccent(raw: String?): String {
        val value = raw.orEmpty().trim().take(9)
        return if (hexColorRegex.matches(value)) value.uppercase(Locale.ROOT) else ""
    }

    private fun sanitizeUri(raw: String?): String {
        val value = raw.orEmpty().trim().take(512)
        if (value.any { it.code < 0x20 || it.isWhitespace() }) return ""
        return if (schemeRegex.containsMatchIn(value)) value else ""
    }

    private fun sanitizeLabel(raw: String?): String =
        raw.orEmpty().trim().replace(Regex("\\s+"), " ").take(64)
}
