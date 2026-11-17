# Fieldfolio release R8 rules.
# Used with the default Android optimized rules in app/build.gradle.kts.
# Keep release shrinking and obfuscation enabled; preserve only runtime contracts.

# ---------------------------------------------------------------------------
# Kotlin signatures, generic types and runtime annotations used by serializers
# and Retrofit. R8/AGP and libraries also contribute their own consumer rules.
# ---------------------------------------------------------------------------
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod,Exceptions

# Preserve existing Kotlin metadata/coroutine safeguards for compatibility.
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# ---------------------------------------------------------------------------
# Moshi (KSP generated adapters and reflection)
# ---------------------------------------------------------------------------
# Moshi looks up the generated FooJsonAdapter constructor by reflection.
# Retain the model and adapter names; retaining adapter members also covers
# generated adapters that are reached indirectly from nested DTOs.
-keep @com.squareup.moshi.JsonClass class * { *; }

-if @com.squareup.moshi.JsonClass class *
-keep class <1>JsonAdapter { *; }

# Moshi's EnumJsonAdapter looks up enum constants via Class.getField(name).
# Its runtime lookup requires fields such as DETECTION and FOLDER to retain
# their original names. Kotlin @JsonClass(generateAdapter = true) on an outer
# DTO does NOT protect the unannotated enum types inside it.
# Preserve all *Fieldfolio* enums (including nested enums) as one policy,
# instead of chasing each startup NoSuchFieldException separately.
# This intentionally does NOT keep all app classes or disable R8 globally.
-keep enum com.karandaev.fieldfolio.** { *; }

# For custom Moshi adapters supplied via @ToJson / @FromJson.
-keepclassmembers class * {
    @com.squareup.moshi.ToJson <methods>;
    @com.squareup.moshi.FromJson <methods>;
}
-keep @com.squareup.moshi.JsonQualifier @interface *

# Annotation processors produce consumer R8 rules, but this prevents
# spurious missing-annotation warnings from optional JSR-305 dependencies.
-dontwarn javax.annotation.**

# ---------------------------------------------------------------------------
# Gson (some Retrofit endpoints use converter-gson)
# ---------------------------------------------------------------------------
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ---------------------------------------------------------------------------
# Retrofit: service interfaces are implemented with dynamic proxies.
# ---------------------------------------------------------------------------
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keepclasseswithmembers,includedescriptorclasses class * {
    @retrofit2.http.* <methods>;
}
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# ---------------------------------------------------------------------------
# Room. Keep the existing conservative policy for the current Room database.
# Its generated implementations are used indirectly.
# ---------------------------------------------------------------------------
-keep class androidx.room.** { *; }
-keep @androidx.room.Entity class * { *; }
-keep class * extends androidx.room.RoomDatabase { <init>(...); }
-keepclassmembers class ** {
    @androidx.room.* <methods>;
}
-keepclassmembers class * {
    @androidx.room.ColumnInfo <fields>;
    @androidx.room.PrimaryKey <fields>;
    @androidx.room.Embedded <fields>;
    @androidx.room.Relation <fields>;
}
-keep class **_Impl { *; }

# ---------------------------------------------------------------------------
# osmdroid: runtime reflection is used inside the map library.
# ---------------------------------------------------------------------------
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**
-dontwarn org.apache.http.**
-dontwarn org.slf4j.**

# ---------------------------------------------------------------------------
# Optional/platform classes referenced by libraries in some configurations.
# Keep these suppressions from the previous release rules for compatibility;
# actual required classes must still be supplied by Gradle dependencies.
# ---------------------------------------------------------------------------
-dontwarn androidx.appcompat.**
-dontwarn com.google.android.material.**
-dontwarn androidx.compose.**
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**
-dontwarn com.google.android.play.core.**
-dontwarn androidx.room.paging.**
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.codehaus.mojo.animal_sniffer.*
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn sun.misc.**
-dontwarn kotlinx.coroutines.**
-dontwarn coil.**
-dontwarn org.junit.**
-dontwarn org.hamcrest.**

# ---------------------------------------------------------------------------
# No legacy com.karandaev.roadCrackDetector rules: package renamed to
# com.karandaev.fieldfolio; obsolete rules matched no app classes.
# ---------------------------------------------------------------------------
