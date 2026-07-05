package app.lawnchairlite.data

import android.content.Context
import android.database.Cursor
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log

private const val TAG = "CustomFont"
private const val MAX_FONT_URI_LENGTH = 2048
private const val MAX_FONT_NAME_LENGTH = 80

fun sanitizeCustomFontUri(raw: String): String {
    val uri = raw.trim()
    if (uri.length !in 1..MAX_FONT_URI_LENGTH) return ""
    return if (uri.startsWith("content://") || uri.startsWith("file://")) uri else ""
}

fun sanitizeCustomFontName(raw: String, uri: String = ""): String {
    val cleaned = raw.trim().replace(Regex("\\s+"), " ").take(MAX_FONT_NAME_LENGTH)
    if (cleaned.isNotBlank()) return cleaned
    return uri.substringBefore('?')
        .substringBefore('#')
        .substringAfterLast('/')
        .trim()
        .take(MAX_FONT_NAME_LENGTH)
        .ifBlank { "Custom font" }
}

fun resolveCustomFontName(context: Context, uri: Uri): String {
    val queried = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor: Cursor ->
                if (!cursor.moveToFirst()) return@use ""
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) cursor.getString(index).orEmpty() else ""
            }
    }.getOrDefault("").orEmpty()
    return sanitizeCustomFontName(queried, uri.toString())
}

fun loadCustomAndroidTypeface(context: Context, rawUri: String): Typeface? {
    val uriString = sanitizeCustomFontUri(rawUri)
    if (uriString.isBlank()) return null
    return runCatching {
        context.contentResolver.openFileDescriptor(Uri.parse(uriString), "r")?.use { pfd ->
            Typeface.Builder(pfd.fileDescriptor).build()
        }
    }.onFailure {
        Log.w(TAG, "Failed to load custom font: $uriString", it)
    }.getOrNull()
}
