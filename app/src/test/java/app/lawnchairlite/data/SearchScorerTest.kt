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

    @Test
    fun normalizesDiacriticsForLabelsAndQueries() {
        assertEquals(90, SearchScorer.score("Cafe Musica", "org.example.music", "café"))
        assertEquals(90, SearchScorer.score("Café Música", "org.example.music", "cafe"))
        assertEquals(90, SearchScorer.score("São Paulo Transit", "br.gov.transit", "sao"))
    }

    @Test
    fun transliteratesCommonNonAsciiNames() {
        assertEquals(90, SearchScorer.score("Æther Notes", "org.example.notes", "aether"))
        assertEquals(90, SearchScorer.score("Straße Maps", "org.example.maps", "strasse"))
        assertEquals(100, SearchScorer.score("Календарь", "org.example.calendar", "kalendar"))
    }

    @Test
    fun matchesInitialsBelowWordPrefixPriority() {
        assertEquals(75, SearchScorer.score("Google Calendar", "com.google.android.calendar", "gc"))
        assertEquals(75, SearchScorer.score("Simple Mobile Tools", "org.simple.tools", "smt"))
    }

    @Test
    fun matchesCommonLauncherAliases() {
        assertEquals(85, SearchScorer.score("Messages", "com.android.messaging", "sms"))
        assertEquals(85, SearchScorer.score("Phone", "com.android.dialer", "dialer"))
        assertEquals(85, SearchScorer.score("Calendar", "com.google.android.calendar", "agenda"))
        assertEquals(85, SearchScorer.score("Files", "com.android.documentsui", "documents"))
    }

    @Test
    fun matchesLocalSemanticIntentPhrases() {
        assertEquals(82, SearchScorer.score("Strong", "io.strongapp.strong", "my gym app"))
        assertEquals(82, SearchScorer.score("Bitwarden", "com.x8bit.bitwarden", "password vault"))
        assertEquals(82, SearchScorer.score("DoorDash", "com.doordash.driverapp", "food delivery"))
        assertEquals(0, SearchScorer.score("Calendar", "com.google.android.calendar", "my gym app"))
    }

    @Test
    fun matchesTypoHeavyQueriesBelowPackagePriority() {
        assertEquals(55, SearchScorer.score("Calculator", "com.android.calculator2", "calcualtor"))
        assertEquals(55, SearchScorer.score("Google Calendar", "com.google.android.calendar", "gogle calender"))
        assertEquals(60, SearchScorer.score("Clock", "com.android.deskclock", "desk"))
    }
}
