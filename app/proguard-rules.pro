# ---- General ---------------------------------------------------------------
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-dontobfuscate
-optimizations !code/simplification/arithmetic,!field/*,!class/merging/*

# ---- ONNX Runtime Mobile ---------------------------------------------------
-keep class ai.onnxruntime.** { *; }
-keep class com.microsoft.onnxruntime.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}

# ---- Manifest model classes (parsed with org.json reflection-free) ---------
-keep class com.shadow306.app.core.modeldownload.** { *; }

# ---- DataStore / Preferences -----------------------------------------------
-keep class androidx.datastore.** { *; }
-keep class androidx.datastore.preferences.** { *; }

# ---- WorkManager -----------------------------------------------------------
-keep class androidx.work.** { *; }
-keep class com.shadow306.app.core.work.** { *; }

# ---- Coil ------------------------------------------------------------------
-keep class coil3.** { *; }
-keep class coil.** { *; }

# ---- Kotlin coroutines -----------------------------------------------------
-keep class kotlinx.coroutines.** { *; }

# ---- Crash reports ---------------------------------------------------------
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ---- Remove logging in release ---------------------------------------------
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# ---- Keep BuildConfig ------------------------------------------------------
-keep class com.shadow306.app.BuildConfig { *; }

# ---- Keep Composables (for debugging, can be removed for smaller APK) ------
-keep class androidx.compose.** { *; }
