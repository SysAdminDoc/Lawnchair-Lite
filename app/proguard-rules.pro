# Lawnchair Lite - ProGuard Rules

# R8 full mode: keep reflection/metadata attributes for retained model and manifest classes.
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*

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
