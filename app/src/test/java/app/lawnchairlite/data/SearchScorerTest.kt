package app.lawnchairlite.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchScorerTest {
    @Test
    fun preservesCurrentRankingBuckets() {
        assertEquals(100, SearchScorer.score("Calculator", "com.android.calculator2", "calculator"))
        assertEquals(90, SearchScorer.score("Calculator", "com.android.calculator2", "calc"))
        assertEquals(80, SearchScorer.score("Google Calendar", "com.google.android.calendar", "cal"))
        assertEquals(70, SearchScorer.score("Google Calendar", "com.google.android.calendar", "lend"))
        assertEquals(60, SearchScorer.score("Clock", "com.android.deskclock", "desk"))
        assertEquals(50, SearchScorer.score("Camera", "com.android.camera", "cmr"))
        assertEquals(0, SearchScorer.score("Camera", "com.android.camera", "mail"))
    }
}
