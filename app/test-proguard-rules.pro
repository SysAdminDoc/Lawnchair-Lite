-dontwarn com.google.errorprone.annotations.MustBeClosed
-keep @org.junit.runner.RunWith class * { *; }
-keep class app.lawnchairlite.** { *; }
-keepclassmembers class * {
    @org.junit.Test <methods>;
}
-keep class androidx.concurrent.futures.** { *; }
-keep class androidx.test.espresso.** { *; }
-keep class com.google.common.util.concurrent.** { *; }
