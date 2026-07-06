package app.lawnchairlite.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupScheduleStateTest {

    @Test
    fun defaultScheduleIsDisabled() {
        val state = BackupScheduleState()

        assertFalse(state.enabled)
        assertEquals(0L, state.lastSuccessAt)
        assertEquals("", state.lastPath)
    }

    @Test
    fun enabledScheduleKeepsLastBackupDetails() {
        val state = BackupScheduleState(
            enabled = true,
            lastSuccessAt = 123L,
            lastPath = "/storage/emulated/0/Android/data/app.lawnchairlite/files/backups/backup.json",
        )

        assertTrue(state.enabled)
        assertEquals(123L, state.lastSuccessAt)
        assertEquals(
            "/storage/emulated/0/Android/data/app.lawnchairlite/files/backups/backup.json",
            state.lastPath,
        )
    }
}
