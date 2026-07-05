package app.lawnchairlite.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudBackupTargetTest {

    @Test
    fun blankUriIsNotConfigured() {
        val target = CloudBackupTarget()

        assertFalse(target.isConfigured)
    }

    @Test
    fun displayNameIsPreferredForLabel() {
        val target = CloudBackupTarget(
            uri = "content://provider/document/backup.json",
            displayName = "Lawnchair backup.json",
        )

        assertTrue(target.isConfigured)
        assertEquals("Lawnchair backup.json", target.label)
    }

    @Test
    fun uriIsFallbackLabel() {
        val target = CloudBackupTarget(uri = "content://provider/document/backup.json")

        assertEquals("content://provider/document/backup.json", target.label)
    }
}
