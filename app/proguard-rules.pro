# ProGuard/R8 rules for StickerFerry

# ---- Kotlin serialization (domain models are persisted as JSON in Room) ----
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class com.akshaykzi.stickerferry.**$$serializer { *; }
-keepclasseswithmembers class com.akshaykzi.stickerferry.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ---- FFmpegMediaMetadataRetriever (JNI callbacks from native code) ----
-keep class wseemann.media.** { *; }

# ---- Hilt ----
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# ---- Room ----
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# ---- Ktor / Coil ----
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**
-keep class coil.** { *; }
-dontwarn coil.**
# slf4j has no Android binding; Ktor references it optionally.
-dontwarn org.slf4j.**

# ---- WhatsApp sticker provider (queried cross-app by package name) ----
-keep class com.akshaykzi.stickerferry.data.whatsapp.StickerContentProvider { *; }

# ---- General Android ----
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
