# ScamShield ProGuard Rules

# Keep TFLite classes
-keep class org.tensorflow.lite.** { *; }
-keep class org.tensorflow.lite.support.** { *; }
-dontwarn org.tensorflow.**

# Keep telecom classes
-keep class android.telecom.** { *; }

# Keep our app classes
-keep class com.scamshield.** { *; }

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# Android Compose
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**
