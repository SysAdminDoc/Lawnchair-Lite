package app.lawnchairlite.data

import org.junit.Assert.assertEquals
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
}
