package app.lawnchairlite.data

import java.text.Normalizer
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

object SearchScorer {
    private const val ALIAS_SCORE = 85
    private const val SEMANTIC_SCORE = 82
    private const val SEMANTIC_PACKAGE_SCORE = 62
    private const val INITIALS_SCORE = 75
    private const val ALIAS_CONTAINS_SCORE = 68
    private const val FUZZY_SCORE = 55

    private val tokenRegex = Regex("[a-z0-9]+")
    private val combiningMarksRegex = Regex("\\p{Mn}+")
    private val whitespaceRegex = Regex("\\s+")

    private val aliasGroups = listOf(
        setOf("sms", "messages", "messaging", "text", "texts", "text messages"),
        setOf("phone", "dialer", "call", "calls", "telephone"),
        setOf("browser", "web", "internet", "chrome", "firefox", "edge", "brave"),
        setOf("calendar", "agenda", "events", "gcal"),
        setOf("clock", "alarm", "alarms", "timer", "stopwatch"),
        setOf("files", "file manager", "documents", "storage"),
        setOf("mail", "email", "gmail", "outlook"),
        setOf("maps", "map", "navigation", "gps"),
        setOf("photos", "gallery", "images", "pictures"),
        setOf("music", "audio", "songs", "player"),
        setOf("store", "market", "app store", "play store"),
        setOf("settings", "preferences", "config", "configuration"),
        setOf("calculator", "calc", "math"),
        setOf("camera", "photo", "selfie"),
    )

    private val semanticFillerTokens = setOf("app", "apps", "application", "my", "the", "open", "launch", "find")

    private val semanticGroups = listOf(
        SemanticGroup(
            queryTerms = setOf("gym", "fitness", "workout", "workouts", "lift", "lifting", "training", "trainer", "exercise"),
            appHints = setOf("strong", "stronglifts", "fit", "fitness", "workout", "gym", "training", "trainer", "exercise", "health", "strava", "garmin"),
        ),
        SemanticGroup(
            queryTerms = setOf("password", "passwords", "vault", "login", "logins", "credential", "credentials", "2fa", "totp"),
            appHints = setOf("bitwarden", "onepassword", "1password", "lastpass", "protonpass", "dashlane", "authy", "aegis", "authenticator"),
        ),
        SemanticGroup(
            queryTerms = setOf("money", "cash", "bank", "banking", "pay", "payment", "wallet", "budget"),
            appHints = setOf("cashapp", "cash", "venmo", "paypal", "wallet", "bank", "banking", "chime", "mint", "ynab"),
        ),
        SemanticGroup(
            queryTerms = setOf("food", "delivery", "deliver", "restaurant", "restaurants", "order", "takeout", "groceries"),
            appHints = setOf("doordash", "ubereats", "grubhub", "instacart", "amazon", "walmart", "restaurant", "food", "delivery"),
        ),
        SemanticGroup(
            queryTerms = setOf("vpn", "privacy", "secure", "security", "shield"),
            appHints = setOf("vpn", "protonvpn", "mullvad", "nord", "expressvpn", "hostshield", "shield", "security"),
        ),
    )

    private val aliasLookup: Map<String, Set<String>> = buildMap {
        aliasGroups.forEach { group ->
            val normalizedGroup = group.map { SearchText.from(it).text }.filter { it.isNotBlank() }.toSet()
            normalizedGroup.forEach { alias ->
                put(alias, normalizedGroup - alias)
                put(SearchText.from(alias).compact, normalizedGroup - alias)
            }
        }
    }

    fun score(label: String, packageName: String, query: String): Int {
        val q = SearchText.from(query)
        if (q.text.isBlank()) return 0
        val l = SearchText.from(label)
        val p = SearchText.from(packageName)
        val aliasScore = aliasScore(l, p, q)
        val semanticScore = semanticScore(l, p, q)
        return when {
            l.text == q.text -> 100
            l.text.startsWith(q.text) || l.compact.startsWith(q.compact) -> 90
            l.tokens.any { it.startsWith(q.compact) } -> 80
            semanticScore != null -> semanticScore
            aliasScore != null -> aliasScore
            l.initials == q.compact || (q.compact.length > 1 && l.initials.startsWith(q.compact)) -> INITIALS_SCORE
            l.text.contains(q.text) || l.compact.contains(q.compact) -> 70
            p.text.contains(q.text) || p.compact.contains(q.compact) -> 60
            fuzzyMatches(q, l) -> FUZZY_SCORE
            isSubsequence(q.compact, l.compact) -> 50
            else -> 0
        }
    }

    private fun semanticScore(label: SearchText, packageName: SearchText, query: SearchText): Int? {
        val meaningfulQueryTokens = query.tokens.filterNot { it in semanticFillerTokens }.toSet()
        if (meaningfulQueryTokens.isEmpty()) return null

        val matchedGroups = semanticGroups.filter { group -> meaningfulQueryTokens.any { it in group.queryTerms } }
        if (matchedGroups.isEmpty()) return null

        return when {
            matchedGroups.any { group -> group.matchesLabel(label) } -> SEMANTIC_SCORE
            matchedGroups.any { group -> group.matchesPackage(packageName) } -> SEMANTIC_PACKAGE_SCORE
            else -> null
        }
    }

    private fun aliasScore(label: SearchText, packageName: SearchText, query: SearchText): Int? {
        val aliases = (aliasLookup[query.text].orEmpty() + aliasLookup[query.compact].orEmpty())
            .map { SearchText.from(it) }
            .filter { it.text.isNotBlank() }
        if (aliases.isEmpty()) return null

        return when {
            aliases.any { alias -> label.text == alias.text || label.compact == alias.compact } -> ALIAS_SCORE
            aliases.any { alias -> label.text.startsWith(alias.text) || label.tokens.any { it.startsWith(alias.compact) } } -> ALIAS_SCORE
            aliases.any { alias -> label.text.contains(alias.text) || label.compact.contains(alias.compact) } -> ALIAS_CONTAINS_SCORE
            aliases.any { alias -> packageName.text.contains(alias.text) || packageName.compact.contains(alias.compact) } -> 60
            else -> null
        }
    }

    private fun fuzzyMatches(query: SearchText, label: SearchText): Boolean {
        if (query.compact.length < 4) return false

        if (query.tokens.isNotEmpty() && query.tokens.all { queryToken ->
                queryToken.length >= 3 && label.tokens.any { labelToken -> fuzzyTokenMatch(queryToken, labelToken) }
            }) {
            return true
        }

        val maxDistance = allowedEditDistance(query.compact.length, label.compact.length)
        if (maxDistance > 0 && editDistanceAtMost(query.compact, label.compact, maxDistance)) return true

        val prefixLength = min(label.compact.length, query.compact.length + maxDistance)
        val labelPrefix = label.compact.take(prefixLength)
        return labelPrefix.length >= query.compact.length - maxDistance &&
            editDistanceAtMost(query.compact, labelPrefix, maxDistance)
    }

    private fun fuzzyTokenMatch(queryToken: String, labelToken: String): Boolean {
        if (labelToken.startsWith(queryToken)) return true
        val maxDistance = allowedEditDistance(queryToken.length, labelToken.length)
        return maxDistance > 0 && editDistanceAtMost(queryToken, labelToken, maxDistance)
    }

    private fun allowedEditDistance(aLength: Int, bLength: Int): Int {
        val shorter = min(aLength, bLength)
        val longer = max(aLength, bLength)
        return when {
            shorter < 4 -> 0
            longer <= 5 -> 1
            longer <= 9 -> 2
            else -> 3
        }
    }

    private fun editDistanceAtMost(a: String, b: String, maxDistance: Int): Boolean {
        if (kotlin.math.abs(a.length - b.length) > maxDistance) return false
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)

        for (i in 1..a.length) {
            current[0] = i
            var rowMin = current[0]
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(
                    previous[j] + 1,
                    current[j - 1] + 1,
                    previous[j - 1] + cost,
                )
                rowMin = min(rowMin, current[j])
            }
            if (rowMin > maxDistance) return false
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length] <= maxDistance
    }

    private fun isSubsequence(query: String, text: String): Boolean {
        if (query.isBlank()) return false
        var queryIndex = 0
        for (ch in text) {
            if (queryIndex < query.length && ch == query[queryIndex]) queryIndex++
        }
        return queryIndex == query.length
    }

    private data class SearchText(
        val text: String,
        val tokens: List<String>,
        val compact: String,
        val initials: String,
    ) {
        companion object {
            fun from(raw: String): SearchText {
                val normalized = normalize(raw)
                val tokens = tokenRegex.findAll(normalized).map { it.value }.toList()
                val text = tokens.joinToString(" ")
                return SearchText(
                    text = text,
                    tokens = tokens,
                    compact = tokens.joinToString(""),
                    initials = tokens.mapNotNull { it.firstOrNull() }.joinToString(""),
                )
            }
        }
    }

    private data class SemanticGroup(
        val queryTerms: Set<String>,
        val appHints: Set<String>,
    ) {
        fun matchesLabel(label: SearchText): Boolean = appHints.any { hint ->
            label.tokens.any { token -> token == hint || token.startsWith(hint) } || label.compact.contains(hint)
        }

        fun matchesPackage(packageName: SearchText): Boolean = appHints.any { hint ->
            packageName.tokens.any { token -> token == hint || token.startsWith(hint) } || packageName.compact.contains(hint)
        }
    }

    private fun normalize(raw: String): String {
        val transliterated = buildString {
            raw.forEach { append(transliterate(it)) }
        }
        val decomposed = Normalizer.normalize(transliterated, Normalizer.Form.NFKD)
        return decomposed
            .replace(combiningMarksRegex, "")
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
            .replace(whitespaceRegex, " ")
    }

    private fun transliterate(ch: Char): String {
        return when (ch.lowercaseChar()) {
            'æ' -> "ae"
            'œ' -> "oe"
            'ß' -> "ss"
            'ø' -> "o"
            'ł' -> "l"
            'đ' -> "d"
            'ð' -> "d"
            'þ' -> "th"
            'ħ' -> "h"
            'ı' -> "i"
            'ŋ' -> "n"
            'а' -> "a"
            'б' -> "b"
            'в' -> "v"
            'г' -> "g"
            'д' -> "d"
            'е', 'ё' -> "e"
            'ж' -> "zh"
            'з' -> "z"
            'и', 'й' -> "i"
            'к' -> "k"
            'л' -> "l"
            'м' -> "m"
            'н' -> "n"
            'о' -> "o"
            'п' -> "p"
            'р' -> "r"
            'с' -> "s"
            'т' -> "t"
            'у' -> "u"
            'ф' -> "f"
            'х' -> "kh"
            'ц' -> "ts"
            'ч' -> "ch"
            'ш' -> "sh"
            'щ' -> "shch"
            'ы' -> "y"
            'э' -> "e"
            'ю' -> "yu"
            'я' -> "ya"
            'ь', 'ъ' -> ""
            'α' -> "a"
            'β' -> "b"
            'γ' -> "g"
            'δ' -> "d"
            'ε', 'η' -> "e"
            'ζ' -> "z"
            'θ' -> "th"
            'ι' -> "i"
            'κ' -> "k"
            'λ' -> "l"
            'μ' -> "m"
            'ν' -> "n"
            'ξ' -> "x"
            'ο', 'ω' -> "o"
            'π' -> "p"
            'ρ' -> "r"
            'σ', 'ς' -> "s"
            'τ' -> "t"
            'υ' -> "y"
            'φ' -> "f"
            'χ' -> "ch"
            'ψ' -> "ps"
            else -> ch.toString()
        }
    }
}
