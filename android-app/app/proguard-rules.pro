# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Keep Moonshine classes
-keep class ai.moonshine.** { *; }

# Keep RunAnywhere classes
-keep class ai.runanywhere.** { *; }

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

