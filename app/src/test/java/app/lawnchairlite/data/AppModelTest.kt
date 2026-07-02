package app.lawnchairlite.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppModelTest {

    @Test
    fun personalProfileKeyMatchesExistingFormat() {
        val app = AppInfo(
            label = "Mail",
            packageName = "com.example.mail",
            activityName = "com.example.mail.Main",
            icon = null,
        )

        assertEquals("com.example.mail/com.example.mail.Main", app.key)
    }

    @Test
    fun workProfileKeyIncludesProfileSerial() {
        val app = AppInfo(
            label = "Mail",
            packageName = "com.example.mail",
            activityName = "com.example.mail.Main",
            icon = null,
            isWorkProfile = true,
            profileSerial = 12L,
        )

        assertEquals("com.example.mail/com.example.mail.Main@12", app.key)
    }

    @Test
    fun folderWithoutCoverUsesLegacySerialization() {
        val folder = GridCell.Folder(
            name = "Work",
            appKeys = listOf("com.example.mail/com.example.mail.Main", "com.example.docs/com.example.docs.Main"),
        )

        val serialized = folder.serialize()

        assertEquals("F:Work:com.example.mail/com.example.mail.Main,com.example.docs/com.example.docs.Main", serialized)
        assertEquals(folder, deserializeCell(serialized))
    }

    @Test
    fun folderCoverSerializationRoundTrips() {
        val folder = GridCell.Folder(
            name = "Work",
            appKeys = listOf("com.example.mail/com.example.mail.Main", "com.example.docs/com.example.docs.Main"),
            coverEmoji = "⭐",
            coverAppKey = "",
        )

        val serialized = folder.serialize()

        assertEquals(folder, deserializeCell(serialized))
    }

    @Test
    fun drawerGroupMatchesManualAppsAndPackagePrefixes() {
        val mail = AppInfo("Mail", "com.example.mail", "com.example.mail.Main", null)
        val browser = AppInfo("Browser", "org.mozilla.firefox", "org.mozilla.firefox.App", null)
        val notes = AppInfo("Notes", "net.example.notes", "net.example.notes.Main", null)
        val group = DrawerGroup(
            id = "work",
            name = "Work",
            appKeys = setOf(mail.key),
            packagePrefixes = listOf("org.mozilla"),
        )

        assertTrue(group.matches(mail))
        assertTrue(group.matches(browser))
        assertFalse(group.matches(notes))
        assertFalse(group.copy(enabled = false).matches(mail))
    }

    @Test
    fun drawerGroupSanitizerBoundsImportedData() {
        val groups = sanitizeDrawerGroups(
            listOf(
                DrawerGroup(
                    id = "",
                    name = "  Work   Apps  ",
                    appKeys = setOf("bad-key", "com.example.mail/com.example.mail.Main"),
                    packagePrefixes = listOf(" COM.Example ", "com.example"),
                ),
            ),
        )

        assertEquals(1, groups.size)
        assertEquals("work_apps", groups.single().id)
        assertEquals("Work Apps", groups.single().name)
        assertEquals(setOf("com.example.mail/com.example.mail.Main"), groups.single().appKeys)
        assertEquals(listOf("com.example"), groups.single().packagePrefixes)
    }

}
