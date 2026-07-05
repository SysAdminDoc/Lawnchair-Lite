package app.lawnchairlite.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

private const val WEB_SUGGESTION_MIN_QUERY_LENGTH = 2
private const val WEB_SUGGESTION_LIMIT = 6
private const val WEB_SUGGESTION_CACHE_MAX = 64
private const val WEB_SUGGESTION_CACHE_TTL_MS = 10 * 60 * 1000L
private const val WEB_SUGGESTION_TIMEOUT_MS = 1400

class WebSuggestionService {
    private data class CacheEntry(
        val suggestions: List<String>,
        val fetchedAtMillis: Long,
    )

    private val cache = object : LinkedHashMap<String, CacheEntry>(WEB_SUGGESTION_CACHE_MAX, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CacheEntry>?): Boolean =
            size > WEB_SUGGESTION_CACHE_MAX
    }

    suspend fun suggestions(
        engine: SearchEngine,
        query: String,
        nowMillis: Long = System.currentTimeMillis(),
    ): List<String> {
        val normalized = SearchSuggestionParser.normalizeQuery(query)
        val template = engine.suggestionUrlTemplate
        if (normalized.length < WEB_SUGGESTION_MIN_QUERY_LENGTH || template == null) return emptyList()

        val cacheKey = "${engine.name}:${normalized.lowercase(Locale.US)}"
        synchronized(cache) {
            cache[cacheKey]?.takeIf { nowMillis - it.fetchedAtMillis < WEB_SUGGESTION_CACHE_TTL_MS }?.let {
                return it.suggestions
            }
        }

        val fetched = fetchSuggestions(template, normalized)
        synchronized(cache) {
            cache[cacheKey] = CacheEntry(fetched, nowMillis)
        }
        return fetched
    }

    private suspend fun fetchSuggestions(template: String, query: String): List<String> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(template.replace("%s", encoded)).openConnection() as HttpURLConnection).apply {
                connectTimeout = WEB_SUGGESTION_TIMEOUT_MS
                readTimeout = WEB_SUGGESTION_TIMEOUT_MS
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "LawnchairLite/2.27")
            }
            if (connection.responseCode !in 200..299) return@withContext emptyList()
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            SearchSuggestionParser.parseSuggestionArray(body, query, WEB_SUGGESTION_LIMIT)
        } catch (e: Exception) {
            Log.w(TAG, "Web suggestion fetch failed", e)
            emptyList()
        } finally {
            connection?.disconnect()
        }
    }

    private companion object {
        private const val TAG = "WebSuggestionService"
    }
}

object SearchSuggestionParser {
    fun normalizeQuery(query: String): String =
        query.trim().replace(Regex("\\s+"), " ")

    fun parseSuggestionArray(
        body: String,
        query: String,
        limit: Int = 6,
    ): List<String> {
        val normalizedQuery = normalizeQuery(query)
        val suggestions = parseJsonStrings(secondTopLevelArray(body) ?: return emptyList())
        val deduped = linkedSetOf<String>()
        for (rawSuggestion in suggestions) {
            val suggestion = normalizeQuery(rawSuggestion)
            if (suggestion.isBlank() || suggestion.equals(normalizedQuery, ignoreCase = true)) continue
            deduped += suggestion
            if (deduped.size >= limit) break
        }
        return deduped.toList()
    }

    private fun secondTopLevelArray(body: String): String? {
        var depth = 0
        var nestedArrayIndex = 0
        var targetStart = -1
        var inString = false
        var escaped = false

        body.forEachIndexed { index, char ->
            if (inString) {
                when {
                    escaped -> escaped = false
                    char == '\\' -> escaped = true
                    char == '"' -> inString = false
                }
                return@forEachIndexed
            }

            when (char) {
                '"' -> inString = true
                '[' -> {
                    if (depth == 1) {
                        nestedArrayIndex++
                        if (nestedArrayIndex == 1) targetStart = index
                    }
                    depth++
                }
                ']' -> {
                    depth--
                    if (targetStart >= 0 && depth == 1) {
                        return body.substring(targetStart, index + 1)
                    }
                }
            }
        }
        return null
    }

    private fun parseJsonStrings(arrayBody: String): List<String> {
        val strings = mutableListOf<String>()
        var index = 0
        while (index < arrayBody.length) {
            if (arrayBody[index] != '"') {
                index++
                continue
            }
            val parsed = readJsonString(arrayBody, index + 1) ?: return strings
            strings += parsed.first
            index = parsed.second + 1
        }
        return strings
    }

    private fun readJsonString(text: String, startIndex: Int): Pair<String, Int>? {
        val out = StringBuilder()
        var index = startIndex
        while (index < text.length) {
            when (val char = text[index]) {
                '"' -> return out.toString() to index
                '\\' -> {
                    index++
                    if (index >= text.length) return null
                    when (val escaped = text[index]) {
                        '"', '\\', '/' -> out.append(escaped)
                        'b' -> out.append('\b')
                        'f' -> out.append('\u000C')
                        'n' -> out.append('\n')
                        'r' -> out.append('\r')
                        't' -> out.append('\t')
                        'u' -> {
                            if (index + 4 >= text.length) return null
                            val hex = text.substring(index + 1, index + 5)
                            val codePoint = hex.toIntOrNull(16) ?: return null
                            out.append(codePoint.toChar())
                            index += 4
                        }
                        else -> out.append(escaped)
                    }
                }
                else -> out.append(char)
            }
            index++
        }
        return null
    }
}
