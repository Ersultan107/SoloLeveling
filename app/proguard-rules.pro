# Solo Leveling ProGuard Rules

# Keep data models (Room entities)
-keep class com.sololeveling.app.data.model.** { *; }

# Keep Room Database
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *

# Keep Retrofit/OkHttp
-keepattributes Signature
-keepattributes *Annotation*
-keep class retrofit2.** { *; }
-keep class okhttp3.** { *; }
-keep class com.squareup.okhttp3.** { *; }

# Keep Gson models
-keep class com.google.gson.** { *; }
-keepclassmembers class ** {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Keep WorkManager workers
-keep class com.sololeveling.app.worker.** { *; }

# Keep Services and Receivers
-keep class com.sololeveling.app.service.** { *; }

# Keep ViewBinding
-keep class com.sololeveling.app.databinding.** { *; }

# MPAndroidChart
-keep class com.github.mikephil.charting.** { *; }

# Lottie
-keep class com.airbnb.lottie.** { *; }

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Remove logging in release
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
}
