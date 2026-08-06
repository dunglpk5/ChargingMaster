# ===== Giữ model dùng cho Gson (parse bằng reflection) =====
-keepattributes Signature, *Annotation*, InnerClasses, EnclosingMethod
-keep class com.dung.chargmagagement.model.remote.** { *; }
-keep class com.dung.chargmagagement.model.entity.** { *; }

# ===== Retrofit / OkHttp =====
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-keep,allowobfuscation interface retrofit2.Call
-keepclasseswithmembers class * { @retrofit2.http.* <methods>; }

# ===== Glide =====
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule { <init>(...); }

# ===== Room =====
-keep class * extends androidx.room.RoomDatabase { <init>(); }

# Bỏ log ở bản release
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}
