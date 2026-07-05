package app.lawnchairlite.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppGestureShortcutsTest {

    @Test
    fun shortcutPartsFromKeyParsesStableShortcutKeys() {
        val key = shortcutKey("com.example.mail", "compose")

        assertEquals("com.example.mail" to "compose", shortcutPartsFromKey(key))
        assertNull(shortcutPartsFromKey("com.example.mail/compose"))
        assertNull(shortcutPartsFromKey("shortcut:missing-separator"))
    }

    @Test
    fun sanitizeAppGestureShortcutsKeepsOnlyAppToShortcutBindings() {
        val cleaned = sanitizeAppGestureShortcuts(
            mapOf(
                "com.example.mail/.Main" to shortcutKey("com.example.mail", "compose"),
                "shortcut:com.example.mail/compose" to shortcutKey("com.example.mail", "reply"),
                "bad-app-key" to shortcutKey("com.example.mail", "archive"),
                "com.example.docs/.Main" to "com.example.docs/create",
            ),
        )

        assertEquals(
            mapOf("com.example.mail/.Main" to "shortcut:com.example.mail/compose"),
            cleaned,
        )
    }
}
