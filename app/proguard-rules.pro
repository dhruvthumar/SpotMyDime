-keep class com.spotmydime.data.** { *; }
-keep class com.spotmydime.ai.** { *; }
-keep class com.spotmydime.util.** { *; }
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
