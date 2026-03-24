# Lawnchair Lite v2.15.4 - ProGuard Rules

# Keep DataStore (uses reflection for preferences)
-keep class androidx.datastore.** { *; }

# Keep app data classes (serialized to/from DataStore strings)
-keep class app.lawnchairlite.data.** { *; }

# Keep NotificationListener (referenced by manifest string)
-keep class app.lawnchairlite.NotificationListener { *; }

# Keep CrashCopyReceiver (referenced by manifest)
-keep class app.lawnchairlite.CrashCopyReceiver { *; }

# Keep AdminReceiver (referenced by manifest)
-keep class app.lawnchairlite.AdminReceiver { *; }
