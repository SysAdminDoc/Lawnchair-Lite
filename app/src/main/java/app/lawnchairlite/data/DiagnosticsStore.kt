package app.lawnchairlite.data

import android.content.Context
import android.os.Build
import app.lawnchairlite.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DiagnosticReportSummary(
    val fileName: String,
    val title: String,
    val timestamp: Long,
    val bytes: Long,
)

object DiagnosticsStore {
    private const val MAX_REPORTS = 5
    private const val DIR_NAME = "diagnostics"
    private val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    fun saveCrashReport(context: Context, report: String): File? = runCatching {
        val dir = diagnosticsDir(context)
        val file = File(dir, "crash-${System.currentTimeMillis()}.txt")
        file.writeText(report)
        prune(dir)
        file
    }.getOrNull()

    fun listReports(context: Context): List<DiagnosticReportSummary> = runCatching {
        diagnosticsDir(context).listFiles { file -> file.isFile && file.name.endsWith(".txt") }
            .orEmpty()
            .sortedByDescending { it.lastModified() }
            .map { file ->
                DiagnosticReportSummary(
                    fileName = file.name,
                    title = "Crash ${formatter.format(Date(file.lastModified()))}",
                    timestamp = file.lastModified(),
                    bytes = file.length(),
                )
            }
    }.getOrDefault(emptyList())

    fun readReport(context: Context, fileName: String): String? = runCatching {
        reportFile(context, fileName)?.readText()
    }.getOrNull()

    fun deleteReport(context: Context, fileName: String): Boolean = runCatching {
        reportFile(context, fileName)?.delete() == true
    }.getOrDefault(false)

    fun isSafeReportName(fileName: String): Boolean =
        fileName.endsWith(".txt") && '/' !in fileName && '\\' !in fileName

    fun buildSupportBundle(context: Context): String {
        val reports = listReports(context)
        return buildString {
            appendLine("=== Lawnchair Lite Diagnostics ===")
            appendLine("Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Build: ${if (BuildConfig.DEBUG) "DEBUG" else "RELEASE"}")
            appendLine("Package: ${context.packageName}")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("Fingerprint: ${Build.FINGERPRINT}")
            appendLine("Time: ${System.currentTimeMillis()}")
            appendLine("Crash reports: ${reports.size}")
            reports.forEach { report ->
                appendLine()
                appendLine("--- ${report.title} (${report.bytes} bytes) ---")
                appendLine(readReport(context, report.fileName).orEmpty())
            }
        }
    }

    private fun diagnosticsDir(context: Context): File =
        File(context.filesDir, DIR_NAME).apply { mkdirs() }

    private fun reportFile(context: Context, fileName: String): File? {
        if (!isSafeReportName(fileName)) return null
        val file = File(diagnosticsDir(context), fileName)
        return file.takeIf { it.isFile }
    }

    private fun prune(dir: File) {
        dir.listFiles { file -> file.isFile && file.name.endsWith(".txt") }
            .orEmpty()
            .sortedByDescending { it.lastModified() }
            .drop(MAX_REPORTS)
            .forEach { it.delete() }
    }
}
