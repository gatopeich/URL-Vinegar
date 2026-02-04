# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Keep data classes used for JSON/SharedPreferences
-keepclassmembers class com.gatopeich.urlvinegar.data.** { *; }

# Keep application name
-keep class com.gatopeich.urlvinegar.** { *; }
