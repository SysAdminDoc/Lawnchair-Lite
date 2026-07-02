package app.lawnchairlite.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsStoreTest {

    @Test
    fun reportNamesCannotEscapeDiagnosticsDirectory() {
        assertTrue(DiagnosticsStore.isSafeReportName("crash-123.txt"))
        assertFalse(DiagnosticsStore.isSafeReportName("../crash-123.txt"))
        assertFalse(DiagnosticsStore.isSafeReportName("nested\\crash-123.txt"))
        assertFalse(DiagnosticsStore.isSafeReportName("crash-123.json"))
    }
}
