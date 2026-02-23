# Lawnchair Lite v2.1.0 - ProGuard Rules

# Keep Compose
-dontwarn androidx.compose.**
-keep class androidx.compose.** { *; }

# Keep DataStore
-keep class androidx.datastore.** { *; }

# Keep Coil
-dontwarn coil.**
-keep class coil.** { *; }

# Keep app data classes
-keep class app.lawnchairlite.data.** { *; }
