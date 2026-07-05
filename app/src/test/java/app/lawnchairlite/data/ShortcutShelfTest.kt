package app.lawnchairlite.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ShortcutShelfTest {

    @Test
    fun sanitizeShortcutShelfKeepsOnlyUniqueShortcutCells() {
        val compose = GridCell.Shortcut("com.example.mail", "compose", "Compose", "com.example.mail/.Main")
        val reply = GridCell.Shortcut("com.example.mail", "reply", "Reply", "com.example.mail/.Main")

        val cleaned = sanitizeShortcutShelf(
            listOf(
                GridCell.App("com.example.mail/.Main"),
                compose,
                compose.copy(label = "Compose again"),
                reply,
                GridCell.Folder("Tools", listOf("com.example.mail/.Main")),
                GridCell.Widget(42),
            ),
        )

        assertEquals(listOf(compose, reply), cleaned)
    }

    @Test
    fun sanitizeShortcutShelfCapsAtEightItems() {
        val shelf = (0 until 12).map { index ->
            GridCell.Shortcut("com.example.app$index", "shortcut$index", "Shortcut $index", "com.example.app$index/.Main")
        }

        val cleaned = sanitizeShortcutShelf(shelf)

        assertEquals(MAX_SHORTCUT_SHELF_ITEMS, cleaned.size)
        assertEquals("shortcut7", cleaned.last().shortcutId)
    }
}
