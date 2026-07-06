# Add project specific ProGuard rules here.
# Minification is disabled for the release build for now (see build.gradle.kts),
# so these rules only take effect once you enable R8/ProGuard.

# --- Chaquopy / Python: keep classes called from Python via reflection ---
-keep class com.chaquo.python.** { *; }
-keep class com.adnanearrassen.ytarchiver.python.** { *; }

# --- kotlinx.serialization ---
-keepclassmembers class ** {
    *** Companion;
}
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}
