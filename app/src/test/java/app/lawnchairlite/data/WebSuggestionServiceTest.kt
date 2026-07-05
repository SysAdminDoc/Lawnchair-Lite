package app.lawnchairlite.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WebSuggestionServiceTest {
    @Test
    fun parsesOsJsonSuggestionArraysWithDedupeAndExactQueryFiltering() {
        val parsed = SearchSuggestionParser.parseSuggestionArray(
            """["calendar",["calendar","calendar 2026"," calendar 2026 ","calendar google","calendar app"]]""",
            "calendar",
            limit = 3,
        )

        assertEquals(listOf("calendar 2026", "calendar google", "calendar app"), parsed)
    }

    @Test
    fun returnsEmptyListForMalformedSuggestionPayloads() {
        assertEquals(emptyList<String>(), SearchSuggestionParser.parseSuggestionArray("{}", "calendar"))
        assertEquals(emptyList<String>(), SearchSuggestionParser.parseSuggestionArray("""["calendar"]""", "calendar"))
    }

    @Test
    fun declaresSuggestionEndpointsOnlyForSupportedEngines() {
        assertEquals(
            "https://suggestqueries.google.com/complete/search?client=firefox&q=%s",
            SearchEngine.GOOGLE.suggestionUrlTemplate,
        )
        assertNull(SearchEngine.STARTPAGE.suggestionUrlTemplate)
    }
}
