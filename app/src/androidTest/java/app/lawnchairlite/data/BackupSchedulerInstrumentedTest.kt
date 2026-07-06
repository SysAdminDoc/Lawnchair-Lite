package app.lawnchairlite.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class BackupSchedulerInstrumentedTest {

    @Test
    fun runBackupNowWritesJsonBackupAndRecordsPath() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val result = BackupScheduler.runBackupNow(context)

        assertTrue(result.error, result.success)
        val backupFile = File(result.path)
        assertTrue(backupFile.exists())
        assertEquals("json", backupFile.extension)

        val json = JSONObject(backupFile.readText())
        assertEquals(1, json.getInt("schema"))
        assertTrue(json.has("included_sections"))

        val state = LauncherPrefs(context).backupScheduleState.first()
        assertEquals(backupFile.absolutePath, state.lastPath)
        assertTrue(state.lastSuccessAt > 0L)
    }
}
