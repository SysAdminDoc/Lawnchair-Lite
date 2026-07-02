# Compose instrumentation runs in a separate APK against the minified debug app.
# Keep coroutine APIs available for the test runtime classpath.
-keep class androidx.compose.** { *; }
-keep class app.lawnchairlite.ui.** { *; }
-keep class kotlinx.coroutines.** { *; }
-keep class kotlin.coroutines.** { *; }
-keep class kotlin.time.** { *; }
