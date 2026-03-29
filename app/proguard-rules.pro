# ============================================================================
# TradeScreener AI — ProGuard / R8 rules for production release
# ============================================================================

# ---------- Preserve line numbers for Crashlytics stack traces ----------
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ---------- Retrofit ----------
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault

-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*

# Keep Retrofit interfaces
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# ---------- OkHttp ----------
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ---------- Gson ----------
# Keep all model / data classes used for JSON serialization
-keep class com.tradescreenerai.app.data.model.** { *; }
-keep class com.tradescreenerai.app.data.remote.api.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
# Gson TypeToken needs Signature preserved (done above)
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# ---------- Kotlin Coroutines ----------
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# ---------- Jetpack Compose ----------
# R8 full mode strips some Compose metadata; keep stability annotations
-keep class androidx.compose.runtime.** { *; }
-dontwarn androidx.compose.**

# ---------- AndroidX DataStore ----------
-keep class androidx.datastore.** { *; }

# ---------- Firebase Crashlytics ----------
-keep public class * extends java.lang.Exception
-keep class com.google.firebase.crashlytics.** { *; }

# ---------- Coil ----------
-dontwarn coil3.**

# ---------- WorkManager ----------
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

